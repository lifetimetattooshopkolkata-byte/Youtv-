package com.example.data.distribution

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.Keep
import com.example.BuildConfig
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

@Keep
@IgnoreExtraProperties
data class AppRelease(
    val releaseId: String = "",
    val versionName: String = "1.0",
    val versionCode: Long = 1L,
    val buildType: String = "release",
    val channel: String = "production",
    val releaseNotes: String = "",
    val fileName: String = "",
    val gcsObjectPath: String? = null,
    val hostingPath: String? = null,
    val cloudBuildId: String? = null,
    val status: String = "SETUP_REQUIRED",
    val sha256: String? = null,
    val fileSizeBytes: Long = 0L,
    val downloadCount: Long = 0L,
    val triggeredBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DistributionOverview(
    val backendConfigured: Boolean = false,
    val gcsConfigured: Boolean = false,
    val firebaseHostingConfigured: Boolean = false,
    val cloudBuildConfigured: Boolean = false,
    val firebaseHostingUrl: String? = null,
    val releases: List<AppRelease> = emptyList(),
    val statusMessage: String = ""
)

data class ApkBuildTriggerResult(
    val success: Boolean,
    val setupRequired: Boolean,
    val message: String,
    val release: AppRelease? = null
)

data class DirectApkDownloadResult(
    val success: Boolean,
    val downloadUrl: String? = null,
    val source: String = "UNCONFIGURED",
    val sha256: String? = null,
    val message: String = ""
)

class DistributionManager(
    private val context: Context,
    private val baseUrl: String = BuildConfig.YOUTV_BACKEND_URL.trim().trimEnd('/')
) {
    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    val isBackendConfigured: Boolean
        get() = baseUrl.startsWith("https://") || baseUrl.startsWith("http://")

    private suspend fun getIdToken(): String? = try {
        Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
    } catch (_: Exception) {
        null
    }

    suspend fun fetchDistributionOverview(): DistributionOverview = withContext(Dispatchers.IO) {
        if (!isBackendConfigured) {
            return@withContext fetchFirestoreFallbackOverview(
                "Cloud Run backend (YOUTV_BACKEND_URL) is not configured yet. Configure Cloud Run + GCS_BUCKET or FIREBASE_HOSTING_URL to enable direct signed APK downloads."
            )
        }

        val token = getIdToken() ?: return@withContext DistributionOverview(
            backendConfigured = true,
            statusMessage = "Sign in with a verified Google account to check APK releases."
        )

        try {
            val req = Request.Builder()
                .url("$baseUrl/api/distribution/releases")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext fetchFirestoreFallbackOverview(
                        "Distribution API returned HTTP ${resp.code}; showing Firestore release catalog."
                    )
                }

                val json = JSONObject(rawBody)
                val releasesArr = json.optJSONArray("releases")
                val list = buildList {
                    if (releasesArr != null) {
                        for (i in 0 until releasesArr.length()) {
                            val item = releasesArr.optJSONObject(i) ?: continue
                            add(
                                AppRelease(
                                    releaseId = item.optString("releaseId"),
                                    versionName = item.optString("versionName", "1.0"),
                                    versionCode = item.optLong("versionCode", 1L),
                                    buildType = item.optString("buildType", "release"),
                                    channel = item.optString("channel", "production"),
                                    releaseNotes = item.optString("releaseNotes"),
                                    fileName = item.optString("fileName"),
                                    gcsObjectPath = item.optString("gcsObjectPath").takeIf { it.isNotBlank() && it != "null" },
                                    hostingPath = item.optString("hostingPath").takeIf { it.isNotBlank() && it != "null" },
                                    cloudBuildId = item.optString("cloudBuildId").takeIf { it.isNotBlank() && it != "null" },
                                    status = item.optString("status", "SETUP_REQUIRED"),
                                    sha256 = item.optString("sha256").takeIf { it.isNotBlank() && it != "null" },
                                    fileSizeBytes = item.optLong("fileSizeBytes", 0L),
                                    downloadCount = item.optLong("downloadCount", 0L),
                                    triggeredBy = item.optString("triggeredBy")
                                )
                            )
                        }
                    }
                }

                DistributionOverview(
                    backendConfigured = true,
                    gcsConfigured = json.optBoolean("gcsConfigured", false),
                    firebaseHostingConfigured = json.optBoolean("firebaseHostingConfigured", false),
                    cloudBuildConfigured = json.optBoolean("cloudBuildConfigured", false),
                    firebaseHostingUrl = json.optString("firebaseHostingUrl").takeIf { it.isNotBlank() && it != "null" },
                    releases = list,
                    statusMessage = if (list.isEmpty()) "No APK builds published yet." else "${list.size} release(s) available."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fetchFirestoreFallbackOverview(e.localizedMessage ?: "Failed to reach Distribution API.")
        }
    }

    private suspend fun fetchFirestoreFallbackOverview(message: String): DistributionOverview {
        return try {
            val snap = db.collection("appReleases").get().await()
            val releases = snap.toObjects(AppRelease::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            DistributionOverview(
                backendConfigured = isBackendConfigured,
                releases = releases,
                statusMessage = message
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "appReleases")
            DistributionOverview(
                backendConfigured = isBackendConfigured,
                statusMessage = message
            )
        }
    }

    suspend fun triggerApkBuild(
        versionName: String,
        versionCode: Long,
        buildType: String = "release",
        channel: String = "production",
        releaseNotes: String = ""
    ): ApkBuildTriggerResult = withContext(Dispatchers.IO) {
        if (!isBackendConfigured) {
            return@withContext ApkBuildTriggerResult(
                success = false,
                setupRequired = true,
                message = "Set YOUTV_BACKEND_URL (Cloud Run) and CLOUD_BUILD_TRIGGER_ID / GCS_BUCKET to trigger remote signed APK builds."
            )
        }

        val token = getIdToken() ?: return@withContext ApkBuildTriggerResult(
            success = false,
            setupRequired = false,
            message = "Missing verified owner Firebase ID token."
        )

        try {
            val payload = JSONObject().apply {
                put("versionName", versionName.trim())
                put("versionCode", versionCode)
                put("buildType", buildType)
                put("channel", channel)
                put("releaseNotes", releaseNotes.trim())
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/api/distribution/build")
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(rawBody) }.getOrNull()
                if (resp.isSuccessful) {
                    val relJson = json?.optJSONObject("release")
                    val rel = relJson?.let {
                        AppRelease(
                            releaseId = it.optString("releaseId"),
                            versionName = it.optString("versionName", versionName),
                            versionCode = it.optLong("versionCode", versionCode),
                            buildType = it.optString("buildType", buildType),
                            channel = it.optString("channel", channel),
                            fileName = it.optString("fileName"),
                            status = it.optString("status", "SETUP_REQUIRED")
                        )
                    }
                    ApkBuildTriggerResult(
                        success = true,
                        setupRequired = json?.optBoolean("setupRequired", false) ?: false,
                        message = json?.optString("message") ?: "Build triggered.",
                        release = rel
                    )
                } else {
                    ApkBuildTriggerResult(
                        success = false,
                        setupRequired = resp.code == 503,
                        message = json?.optString("error")?.ifBlank { null }
                            ?: "Build trigger failed (HTTP ${resp.code})."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApkBuildTriggerResult(
                success = false,
                setupRequired = false,
                message = e.localizedMessage ?: "Failed to contact DistributionManager API."
            )
        }
    }

    suspend fun requestDirectDownloadUrl(releaseId: String): DirectApkDownloadResult = withContext(Dispatchers.IO) {
        if (!isBackendConfigured) {
            return@withContext DirectApkDownloadResult(
                success = false,
                message = "Cloud Run Distribution API is not configured yet."
            )
        }
        val token = getIdToken() ?: return@withContext DirectApkDownloadResult(
            success = false,
            message = "Sign in required to download signed APK."
        )

        try {
            val cleanId = releaseId.trim()
            if (!Regex("^[a-zA-Z0-9_-]+$").matches(cleanId)) {
                return@withContext DirectApkDownloadResult(success = false, message = "Invalid release ID.")
            }

            val req = Request.Builder()
                .url("$baseUrl/api/distribution/releases/$cleanId/download")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val json = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull()
                val rawUrl = json?.optString("downloadUrl")?.takeIf { it.isNotBlank() && it != "null" }
                if (resp.isSuccessful && isSafeDownloadUrl(rawUrl)) {
                    DirectApkDownloadResult(
                        success = true,
                        downloadUrl = rawUrl,
                        source = json?.optString("source", "GCS_SIGNED_URL") ?: "GCS_SIGNED_URL",
                        sha256 = json?.optString("sha256")?.takeIf { it.isNotBlank() && it != "null" },
                        message = json?.optString("message") ?: "Ready to download."
                    )
                } else {
                    DirectApkDownloadResult(
                        success = false,
                        source = json?.optString("source", "UNCONFIGURED") ?: "UNCONFIGURED",
                        message = json?.optString("message")?.ifBlank { null }
                            ?: json?.optString("error")?.ifBlank { null }
                            ?: "Signed APK download unavailable (HTTP ${resp.code})."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DirectApkDownloadResult(
                success = false,
                message = e.localizedMessage ?: "Failed to resolve APK download URL."
            )
        }
    }

    fun openDownloadInBrowser(url: String): Boolean {
        if (!isSafeDownloadUrl(url)) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun isSafeDownloadUrl(rawUrl: String?): Boolean {
            if (rawUrl.isNullOrBlank()) return false
            val trimmed = rawUrl.trim()
            return trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://localhost", ignoreCase = true) ||
                trimmed.startsWith("http://10.0.2.2", ignoreCase = true)
        }

        fun formatFileSizeMb(bytes: Long): String {
            if (bytes <= 0L) return "Pending artifact"
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
