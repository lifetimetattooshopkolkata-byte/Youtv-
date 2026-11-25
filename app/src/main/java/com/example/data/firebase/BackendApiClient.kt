package com.example.data.firebase

import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BackendTranscodeResponse(
    val configured: Boolean,
    val jobId: String? = null,
    val signedUploadUrl: String? = null,
    val hlsOutputPath: String? = null,
    val message: String = ""
)

data class BackendOwnerVerifyResponse(
    val configured: Boolean,
    val isOwner: Boolean,
    val message: String = ""
)

class BackendApiClient {
    private val baseUrl: String = BuildConfig.YOUTV_BACKEND_URL.trim().trimEnd('/')

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") || baseUrl.startsWith("http://")

    private suspend fun getIdToken(): String? = try {
        Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
    } catch (_: Exception) {
        null
    }

    suspend fun verifyOwnerWithBackend(): BackendOwnerVerifyResponse = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext BackendOwnerVerifyResponse(
                configured = false,
                isOwner = false,
                message = "Cloud Run backend URL (YOUTV_BACKEND_URL) is not configured; falling back to verified /admins Firestore authorization."
            )
        }
        val token = getIdToken() ?: return@withContext BackendOwnerVerifyResponse(
            configured = true,
            isOwner = false,
            message = "Missing Firebase ID token."
        )
        return@withContext try {
            val req = Request.Builder()
                .url("$baseUrl/api/admin/verify")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = JSONObject(body)
                    BackendOwnerVerifyResponse(
                        configured = true,
                        isOwner = json.optBoolean("owner", false),
                        message = json.optString("message", "Verified")
                    )
                } else {
                    BackendOwnerVerifyResponse(
                        configured = true,
                        isOwner = false,
                        message = "Server denied admin access (${resp.code})."
                    )
                }
            }
        } catch (e: Exception) {
            BackendOwnerVerifyResponse(
                configured = true,
                isOwner = false,
                message = e.localizedMessage ?: "Failed to reach Cloud Run admin API."
            )
        }
    }

    suspend fun requestTranscodeJob(
        videoId: String,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long
    ): BackendTranscodeResponse = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext BackendTranscodeResponse(
                configured = false,
                message = "Transcoder API backend not configured. Using safe direct playback fallback."
            )
        }
        val token = getIdToken() ?: return@withContext BackendTranscodeResponse(
            configured = true,
            message = "Sign in required to request transcoding."
        )
        return@withContext try {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("fileName", fileName)
                put("mimeType", mimeType)
                put("fileSizeBytes", fileSizeBytes)
            }.toString()
            val req = Request.Builder()
                .url("$baseUrl/api/videos/transcode")
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = JSONObject(body)
                    BackendTranscodeResponse(
                        configured = json.optBoolean("transcoderConfigured", true),
                        jobId = json.optString("jobId").takeIf { it.isNotBlank() },
                        signedUploadUrl = json.optString("signedUploadUrl").takeIf { it.isNotBlank() },
                        hlsOutputPath = json.optString("hlsOutputPath").takeIf { it.isNotBlank() },
                        message = json.optString("message", "Queued for transcoding")
                    )
                } else {
                    BackendTranscodeResponse(
                        configured = false,
                        message = "Backend rejected request (${resp.code}); using safe playback fallback."
                    )
                }
            }
        } catch (e: Exception) {
            BackendTranscodeResponse(
                configured = false,
                message = e.localizedMessage ?: "Transcoder backend unreachable; using playback fallback."
            )
        }
    }
}
