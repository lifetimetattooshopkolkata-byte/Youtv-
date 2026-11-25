package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.BlockedUser
import com.example.data.model.Channel
import com.example.data.model.Comment
import com.example.data.model.LikeReaction
import com.example.data.model.NotificationItem
import com.example.data.model.PlatformSettings
import com.example.data.model.Playlist
import com.example.data.model.Report
import com.example.data.model.SearchHistoryEntry
import com.example.data.model.ShortVideo
import com.example.data.model.Subscription
import com.example.data.model.UserProfile
import com.example.data.model.Video
import com.example.data.model.VideoProcessingJob
import com.example.data.model.WatchHistoryEntry
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class YouTvRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val backendApi: BackendApiClient = BackendApiClient()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun newValidId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    // --- USER PROFILE & ACCOUNT ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val unwrapped = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(unwrapped, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(user: FirebaseUser, preferredLanguage: String): Result<UserProfile> {
        val path = "users/${user.uid}"
        return try {
            val docRef = db.collection("users").document(user.uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val profile = existing.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(userId = user.uid, email = user.email.orEmpty())
                // Record sign-in audit entry
                appendUserAuditLog(user.uid, "SIGN_IN", "USER", user.uid, "User signed in via Google")
                Result.success(profile)
            } else {
                val cleanName = (user.displayName?.takeIf { it.isNotBlank() } ?: "YouTV Creator").take(80)
                val cleanEmail = (user.email?.takeIf { it.isNotBlank() } ?: "${user.uid}@youtv.local").take(200)
                val lang = if (preferredLanguage in listOf("en", "bn", "hi")) preferredLanguage else "en"
                val payload = buildMap<String, Any> {
                    put("userId", user.uid)
                    put("displayName", cleanName)
                    put("email", cleanEmail)
                    user.photoUrl?.toString()?.take(1000)?.let { put("photoUrl", it) }
                    put("preferredLanguage", lang)
                    put("status", "active")
                    put("uploadDisabled", false)
                    put("restrictedMode", false)
                    put("emailVerified", user.isEmailVerified)
                    put("storageUsedBytes", 0L)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
                appendUserAuditLog(user.uid, "REGISTER", "USER", user.uid, "New user profile created")
                val created = docRef.get().await().toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(userId = user.uid, displayName = cleanName, email = cleanEmail, preferredLanguage = lang)
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePreferredLanguage(languageCode: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val safeLang = if (languageCode in listOf("en", "bn", "hi")) languageCode else "en"
            db.collection("users").document(uid).update(
                mapOf(
                    "preferredLanguage" to safeLang,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRestrictedMode(enabled: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "restrictedMode" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun requestAccountDeletion(reason: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "status" to "deletion_requested",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            submitReport(
                targetType = "APPEAL",
                targetId = uid,
                targetOwnerId = uid,
                reason = "Account Deletion Request",
                details = reason.ifBlank { "User requested full account and data deletion." }
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    // --- PLATFORM SETTINGS ---

    fun observePlatformSettings(): Flow<PlatformSettings> = flow {
        val path = "platformSettings/global"
        emitAll(
            db.collection("platformSettings").document("global")
                .snapshots()
                .map { snap ->
                    snap.toObject(PlatformSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: PlatformSettings()
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreThrowable(error)
                    handleFirestoreError(unwrapped, OperationType.GET, path)
                    emit(PlatformSettings())
                }
        )
    }

    // --- CHANNELS ---

    fun observeAllChannels(): Flow<List<Channel>> = flow {
        requireUserId()
        emitAll(
            db.collection("channels")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.subscriberCount }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "channels")
                    throw error
                }
        )
    }

    fun observeMyChannels(userId: String): Flow<List<Channel>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("channels")
                .whereEqualTo("ownerId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "channels")
                    throw error
                }
        )
    }

    suspend fun createChannel(
        name: String,
        handle: String,
        description: String,
        avatarUrl: String? = null,
        coverUrl: String? = null
    ): Result<Channel> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val cleanName = name.trim().take(60)
        require(cleanName.length >= 2) { "Channel name must be at least 2 characters." }
        val normalizedHandle = handle.trim().let { if (it.startsWith("@")) it else "@$it" }.take(40)
        require(normalizedHandle.length >= 3) { "Handle must be at least 3 characters." }

        val channelId = newValidId("ch")
        val path = "channels/$channelId"
        return try {
            val payload = buildMap<String, Any> {
                put("ownerId", uid)
                put("name", cleanName)
                put("handle", normalizedHandle)
                put("description", description.trim().take(500))
                avatarUrl?.takeIf { it.isNotBlank() }?.take(1000)?.let { put("avatarUrl", it) }
                coverUrl?.takeIf { it.isNotBlank() }?.take(1000)?.let { put("coverUrl", it) }
                put("subscriberCount", 0L)
                put("videoCount", 0L)
                put("totalViews", 0L)
                put("status", "active")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("channels").document(channelId).set(payload).await()
            appendUserAuditLog(uid, "CREATE_CHANNEL", "CHANNEL", channelId, "Created channel $cleanName ($normalizedHandle)")
            Result.success(
                Channel(
                    id = channelId,
                    ownerId = uid,
                    name = cleanName,
                    handle = normalizedHandle,
                    description = description.trim().take(500),
                    avatarUrl = avatarUrl,
                    coverUrl = coverUrl
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- VIDEOS & SHORTS ---

    fun observePublicVideos(): Flow<List<Video>> = flow {
        requireUserId()
        emitAll(
            db.collection("videos")
                .whereEqualTo("visibility", "PUBLIC")
                .whereEqualTo("moderationStatus", "ACTIVE")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Video::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "videos")
                    throw error
                }
        )
    }

    fun observeMyVideos(userId: String): Flow<List<Video>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("videos")
                .whereEqualTo("ownerId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Video::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "videos")
                    throw error
                }
        )
    }

    fun observePublicShorts(): Flow<List<ShortVideo>> = flow {
        requireUserId()
        emitAll(
            db.collection("shorts")
                .whereEqualTo("visibility", "PUBLIC")
                .whereEqualTo("moderationStatus", "ACTIVE")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ShortVideo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "shorts")
                    throw error
                }
        )
    }

    fun observeMyProcessingJobs(userId: String): Flow<List<VideoProcessingJob>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("videoProcessingJobs")
                .whereEqualTo("ownerId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VideoProcessingJob::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "videoProcessingJobs")
                    throw error
                }
        )
    }

    suspend fun publishVideo(
        channel: Channel,
        title: String,
        description: String,
        playbackUrl: String,
        thumbnailUrl: String?,
        category: String,
        tags: List<String>,
        language: String,
        visibility: String,
        shortForm: Boolean,
        ageRestricted: Boolean,
        durationSec: Long,
        fileSizeBytes: Long
    ): Result<Pair<Video, BackendTranscodeResponse>> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val cleanTitle = title.trim().take(120)
        require(cleanTitle.isNotBlank()) { "Video title is required." }
        require(playbackUrl.isNotBlank()) { "A video file or stream URL is required." }
        val cleanTags = tags.map { it.trim() }.filter { it.isNotBlank() }.take(10)
        val safeLang = if (language in listOf("en", "bn", "hi")) language else "en"
        val safeVisibility = if (visibility in listOf("PUBLIC", "PRIVATE", "UNLISTED")) visibility else "PUBLIC"

        val videoId = newValidId("vid")
        val jobId = newValidId("job")
        return try {
            val transcodeResp = backendApi.requestTranscodeJob(
                videoId = videoId,
                fileName = "$videoId.mp4",
                mimeType = "video/mp4",
                fileSizeBytes = fileSizeBytes.coerceAtLeast(1024L)
            )
            val initialProcessingStatus = if (transcodeResp.configured) "QUEUED" else "FALLBACK_DIRECT"
            val hlsManifest = transcodeResp.hlsOutputPath

            val videoPayload = buildMap<String, Any> {
                put("ownerId", uid)
                put("channelId", channel.id)
                put("channelName", channel.name.take(60))
                put("channelHandle", channel.handle.take(40))
                channel.avatarUrl?.takeIf { it.isNotBlank() }?.take(1000)?.let { put("channelAvatarUrl", it) }
                put("title", cleanTitle)
                put("description", description.trim().take(2000))
                thumbnailUrl?.takeIf { it.isNotBlank() }?.take(1000)?.let { put("thumbnailUrl", it) }
                put("playbackUrl", playbackUrl.take(2000))
                hlsManifest?.takeIf { it.isNotBlank() }?.take(2000)?.let { put("hlsManifestUrl", it) }
                put("category", category.take(40))
                put("tags", cleanTags)
                put("language", safeLang)
                put("visibility", safeVisibility)
                put("processingStatus", initialProcessingStatus)
                put("moderationStatus", "ACTIVE")
                put("shortForm", shortForm)
                put("ageRestricted", ageRestricted)
                put("durationSec", durationSec.coerceAtLeast(0L))
                put("fileSizeBytes", fileSizeBytes.coerceAtLeast(0L))
                put("viewCount", 0L)
                put("likeCount", 0L)
                put("dislikeCount", 0L)
                put("commentCount", 0L)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val jobPayload = buildMap<String, Any> {
                put("videoId", videoId)
                put("ownerId", uid)
                put("inputPath", "uploads/original/$uid/$videoId/source.mp4")
                hlsManifest?.let { put("outputManifestPath", it) }
                put("status", initialProcessingStatus)
                put("progressPercent", if (transcodeResp.configured) 15L else 100L)
                put("errorMessage", if (transcodeResp.configured) "" else "Transcoder API not configured; using direct playback fallback.")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            db.collection("videos").document(videoId).set(videoPayload).await()
            db.collection("videoProcessingJobs").document(jobId).set(jobPayload).await()

            if (shortForm) {
                val shortPayload = buildMap<String, Any> {
                    put("ownerId", uid)
                    put("channelId", channel.id)
                    put("channelName", channel.name.take(60))
                    channel.avatarUrl?.takeIf { it.isNotBlank() }?.take(1000)?.let { put("channelAvatarUrl", it) }
                    put("title", cleanTitle)
                    put("videoUrl", playbackUrl.take(2000))
                    thumbnailUrl?.takeIf { it.isNotBlank() }?.take(1000)?.let { put("thumbnailUrl", it) }
                    put("language", safeLang)
                    put("visibility", safeVisibility)
                    put("moderationStatus", "ACTIVE")
                    put("durationSec", durationSec.coerceIn(1L, 180L))
                    put("viewCount", 0L)
                    put("likeCount", 0L)
                    put("commentCount", 0L)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                db.collection("shorts").document(videoId).set(shortPayload).await()
            }

            db.collection("channels").document(channel.id).update(
                mapOf(
                    "videoCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("users").document(uid).update(
                mapOf(
                    "storageUsedBytes" to FieldValue.increment(fileSizeBytes.coerceAtLeast(0L)),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            appendUserAuditLog(uid, "UPLOAD_VIDEO", "VIDEO", videoId, "Published video: $cleanTitle")

            val createdVideo = Video(
                id = videoId,
                ownerId = uid,
                channelId = channel.id,
                channelName = channel.name,
                channelHandle = channel.handle,
                channelAvatarUrl = channel.avatarUrl,
                title = cleanTitle,
                description = description.trim(),
                thumbnailUrl = thumbnailUrl,
                playbackUrl = playbackUrl,
                hlsManifestUrl = hlsManifest,
                category = category,
                tags = cleanTags,
                language = safeLang,
                visibility = safeVisibility,
                processingStatus = initialProcessingStatus,
                shortForm = shortForm,
                ageRestricted = ageRestricted,
                durationSec = durationSec,
                fileSizeBytes = fileSizeBytes
            )
            Result.success(createdVideo to transcodeResp)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "videos/$videoId")
            Result.failure(e)
        }
    }

    suspend fun updateVideoDetails(
        videoId: String,
        title: String,
        description: String,
        category: String,
        visibility: String
    ): Result<Unit> {
        val path = "videos/$videoId"
        return try {
            requireUserId()
            db.collection("videos").document(videoId).update(
                mapOf(
                    "title" to title.trim().take(120),
                    "description" to description.trim().take(2000),
                    "category" to category.take(40),
                    "visibility" to visibility,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteOwnVideo(video: Video): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        return try {
            db.collection("videos").document(video.id).delete().await()
            if (video.shortForm) {
                runCatching { db.collection("shorts").document(video.id).delete().await() }
            }
            if (video.channelId.isNotBlank()) {
                runCatching {
                    db.collection("channels").document(video.channelId).update(
                        mapOf(
                            "videoCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            appendUserAuditLog(uid, "DELETE_VIDEO", "VIDEO", video.id, "Deleted video: ${video.title}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "videos/${video.id}")
            Result.failure(e)
        }
    }

    // --- PLAYBACK TRACKING, LIKES, COMMENTS, SUBSCRIPTIONS ---

    suspend fun recordVideoViewAndHistory(video: Video): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        return try {
            db.collection("videos").document(video.id).update(
                mapOf(
                    "viewCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (video.channelId.isNotBlank()) {
                runCatching {
                    db.collection("channels").document(video.channelId).update(
                        mapOf(
                            "totalViews" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            val histId = "${uid}_${video.id}".take(128)
            val histPayload = buildMap<String, Any> {
                put("userId", uid)
                put("videoId", video.id)
                put("videoTitle", video.title.take(120))
                video.thumbnailUrl?.takeIf { it.isNotBlank() }?.take(1000)?.let { put("thumbnailUrl", it) }
                put("channelName", video.channelName.take(60))
                put("progressSec", 5L)
                put("durationSec", video.durationSec.coerceAtLeast(0L))
                put("watchedAt", FieldValue.serverTimestamp())
            }
            db.collection("watchHistory").document(histId).set(histPayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "watchHistory")
            Result.failure(e)
        }
    }

    fun observeMyReactions(userId: String): Flow<Map<String, String>> = flow {
        if (userId.isBlank()) {
            emit(emptyMap())
            return@flow
        }
        emitAll(
            db.collection("likes")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(LikeReaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .associate { it.videoId to it.reaction }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "likes")
                    throw error
                }
        )
    }

    suspend fun toggleVideoReaction(videoId: String, desiredReaction: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val likeId = "${videoId}_${uid}"
        val likeRef = db.collection("likes").document(likeId)
        val videoRef = db.collection("videos").document(videoId)
        return try {
            val snap = likeRef.get().await()
            val currentReaction = snap.getString("reaction")
            val batch = db.batch()
            when (currentReaction) {
                null -> {
                    batch.set(
                        likeRef,
                        mapOf(
                            "videoId" to videoId,
                            "userId" to uid,
                            "reaction" to desiredReaction,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    if (desiredReaction == "LIKE") {
                        batch.update(videoRef, mapOf("likeCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp()))
                    } else {
                        batch.update(videoRef, mapOf("dislikeCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp()))
                    }
                }
                desiredReaction -> {
                    batch.delete(likeRef)
                    if (desiredReaction == "LIKE") {
                        batch.update(videoRef, mapOf("likeCount" to FieldValue.increment(-1L), "updatedAt" to FieldValue.serverTimestamp()))
                    } else {
                        batch.update(videoRef, mapOf("dislikeCount" to FieldValue.increment(-1L), "updatedAt" to FieldValue.serverTimestamp()))
                    }
                }
                else -> {
                    batch.update(likeRef, mapOf("reaction" to desiredReaction, "updatedAt" to FieldValue.serverTimestamp()))
                    if (desiredReaction == "LIKE") {
                        batch.update(
                            videoRef,
                            mapOf(
                                "likeCount" to FieldValue.increment(1L),
                                "dislikeCount" to FieldValue.increment(-1L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        batch.update(
                            videoRef,
                            mapOf(
                                "likeCount" to FieldValue.increment(-1L),
                                "dislikeCount" to FieldValue.increment(1L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "likes/$likeId")
            Result.failure(e)
        }
    }

    fun observeComments(videoId: String): Flow<List<Comment>> = flow {
        requireUserId()
        emitAll(
            db.collection("comments")
                .whereEqualTo("videoId", videoId)
                .whereEqualTo("moderationStatus", "ACTIVE")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Comment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "comments")
                    throw error
                }
        )
    }

    suspend fun postComment(
        video: Video,
        text: String,
        parentCommentId: String? = null
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Sign in required"))
        val cleanText = text.trim().take(1000)
        require(cleanText.isNotBlank()) { "Comment cannot be empty." }
        val commentId = newValidId("cmt")
        return try {
            val payload = buildMap<String, Any> {
                put("videoId", video.id)
                put("authorId", user.uid)
                put("authorName", (user.displayName?.takeIf { it.isNotBlank() } ?: "YouTV Viewer").take(80))
                user.photoUrl?.toString()?.take(1000)?.let { put("authorAvatarUrl", it) }
                put("videoOwnerId", video.ownerId)
                parentCommentId?.takeIf { it.isNotBlank() }?.let { put("parentCommentId", it) }
                put("text", cleanText)
                put("moderationStatus", "ACTIVE")
                put("likeCount", 0L)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("comments").document(commentId).set(payload).await()
            db.collection("videos").document(video.id).update(
                mapOf(
                    "commentCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "comments/$commentId")
            Result.failure(e)
        }
    }

    suspend fun hideComment(commentId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("comments").document(commentId).update(
                mapOf(
                    "moderationStatus" to "HIDDEN",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "comments/$commentId")
            Result.failure(e)
        }
    }

    suspend fun deleteComment(comment: Comment): Result<Unit> {
        return try {
            requireUserId()
            db.collection("comments").document(comment.id).delete().await()
            runCatching {
                db.collection("videos").document(comment.videoId).update(
                    mapOf(
                        "commentCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "comments/${comment.id}")
            Result.failure(e)
        }
    }

    // --- SUBSCRIPTIONS ---

    fun observeMySubscriptions(userId: String): Flow<List<Subscription>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("subscriptions")
                .whereEqualTo("subscriberId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Subscription::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "subscriptions")
                    throw error
                }
        )
    }

    suspend fun toggleSubscription(channel: Channel): Result<Boolean> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val subId = "${channel.id}_${uid}"
        val subRef = db.collection("subscriptions").document(subId)
        val chRef = db.collection("channels").document(channel.id)
        return try {
            val snap = subRef.get().await()
            val batch = db.batch()
            val nowSubscribed = if (snap.exists()) {
                batch.delete(subRef)
                batch.update(chRef, mapOf("subscriberCount" to FieldValue.increment(-1L), "updatedAt" to FieldValue.serverTimestamp()))
                false
            } else {
                val payload = buildMap<String, Any> {
                    put("channelId", channel.id)
                    put("channelName", channel.name.take(60))
                    channel.avatarUrl?.takeIf { it.isNotBlank() }?.take(1000)?.let { put("channelAvatarUrl", it) }
                    put("subscriberId", uid)
                    put("channelOwnerId", channel.ownerId)
                    put("notificationsEnabled", true)
                    put("createdAt", FieldValue.serverTimestamp())
                }
                batch.set(subRef, payload)
                batch.update(chRef, mapOf("subscriberCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp()))
                true
            }
            batch.commit().await()
            Result.success(nowSubscribed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "subscriptions/$subId")
            Result.failure(e)
        }
    }

    // --- PLAYLISTS, WATCH HISTORY, SEARCH HISTORY, NOTIFICATIONS ---

    fun observeMyPlaylists(userId: String): Flow<List<Playlist>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("playlists")
                .whereEqualTo("ownerId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Playlist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "playlists")
                    throw error
                }
        )
    }

    suspend fun saveToWatchLater(videoId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val playlistId = "wl_$uid".take(128)
        val ref = db.collection("playlists").document(playlistId)
        return try {
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "videoIds" to FieldValue.arrayUnion(videoId),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "ownerId" to uid,
                        "title" to "Watch Later",
                        "description" to "Saved videos to watch later",
                        "visibility" to "PRIVATE",
                        "playlistType" to "WATCH_LATER",
                        "videoIds" to listOf(videoId),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "playlists/$playlistId")
            Result.failure(e)
        }
    }

    suspend fun createPlaylist(title: String, description: String, initialVideoId: String? = null): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val playlistId = newValidId("pl")
        return try {
            db.collection("playlists").document(playlistId).set(
                mapOf(
                    "ownerId" to uid,
                    "title" to title.trim().take(80),
                    "description" to description.trim().take(400),
                    "visibility" to "PRIVATE",
                    "playlistType" to "CUSTOM",
                    "videoIds" to listOfNotNull(initialVideoId),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "playlists/$playlistId")
            Result.failure(e)
        }
    }

    fun observeWatchHistory(userId: String): Flow<List<WatchHistoryEntry>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("watchHistory")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(WatchHistoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.watchedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "watchHistory")
                    throw error
                }
        )
    }

    fun observeSearchHistory(userId: String): Flow<List<SearchHistoryEntry>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("searchHistory")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SearchHistoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.searchedAt ?: Timestamp(0, 0) }
                        .take(12)
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "searchHistory")
                    throw error
                }
        )
    }

    suspend fun saveSearchQuery(query: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val clean = query.trim().take(120)
        if (clean.isBlank()) return Result.success(Unit)
        val entryId = newValidId("sh")
        return try {
            db.collection("searchHistory").document(entryId).set(
                mapOf(
                    "userId" to uid,
                    "query" to clean,
                    "searchedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "searchHistory/$entryId")
            Result.failure(e)
        }
    }

    suspend fun clearSearchHistory(entries: List<SearchHistoryEntry>): Result<Unit> {
        return try {
            requireUserId()
            val batch = db.batch()
            entries.forEach { batch.delete(db.collection("searchHistory").document(it.id)) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "searchHistory")
            Result.failure(e)
        }
    }

    fun observeNotifications(userId: String): Flow<List<NotificationItem>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("notifications")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "notifications")
                    throw error
                }
        )
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("notifications").document(notificationId).update("readFlag", true).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "notifications/$notificationId")
            Result.failure(e)
        }
    }

    // --- REPORTS, COPYRIGHT, APPEALS, BLOCKED USERS ---

    fun observeBlockedUsers(userId: String): Flow<List<BlockedUser>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("blockedUsers")
                .whereEqualTo("blockerId", userId)
                .snapshots()
                .map { snap -> snap.toObjects(BlockedUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "blockedUsers")
                    throw error
                }
        )
    }

    suspend fun blockCreator(targetUserId: String, targetUserName: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val blockId = "${uid}_${targetUserId}".take(128)
        return try {
            db.collection("blockedUsers").document(blockId).set(
                mapOf(
                    "blockerId" to uid,
                    "blockedUserId" to targetUserId,
                    "blockedUserName" to targetUserName.take(80),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "blockedUsers/$blockId")
            Result.failure(e)
        }
    }

    suspend fun unblockCreator(blockId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("blockedUsers").document(blockId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "blockedUsers/$blockId")
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        targetOwnerId: String?,
        reason: String,
        details: String,
        copyrightWorkUrl: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in required"))
        val reportId = newValidId("rep")
        return try {
            val payload = buildMap<String, Any> {
                put("reporterId", uid)
                put("targetType", targetType)
                put("targetId", targetId.take(128))
                targetOwnerId?.takeIf { it.isNotBlank() }?.take(128)?.let { put("targetOwnerId", it) }
                put("reason", reason.trim().take(120).ifBlank { "Policy report" })
                put("details", details.trim().take(1000))
                copyrightWorkUrl?.takeIf { it.isNotBlank() }?.take(1000)?.let { put("copyrightWorkUrl", it) }
                put("status", "OPEN")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("reports").document(reportId).set(payload).await()
            appendUserAuditLog(uid, "REPORT_SUBMITTED", targetType, targetId, "Submitted $targetType report")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reports/$reportId")
            Result.failure(e)
        }
    }

    private suspend fun appendUserAuditLog(
        actorUid: String,
        action: String,
        targetType: String,
        targetId: String,
        summary: String
    ) {
        val logId = newValidId("log")
        runCatching {
            db.collection("auditLogs").document(logId).set(
                mapOf(
                    "actorUid" to actorUid,
                    "action" to action,
                    "targetType" to targetType,
                    "targetId" to targetId.take(128),
                    "result" to "SUCCESS",
                    "summary" to summary.take(400),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }
}
