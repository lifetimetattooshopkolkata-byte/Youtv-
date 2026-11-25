package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AdminRecord
import com.example.data.model.AuditLog
import com.example.data.model.Channel
import com.example.data.model.ModerationAction
import com.example.data.model.PlatformSettings
import com.example.data.model.Report
import com.example.data.model.UserProfile
import com.example.data.model.Video
import com.example.data.model.VideoProcessingJob
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
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

sealed interface OwnerAuthStatus {
    data object Checking : OwnerAuthStatus
    data class Verified(val adminRecord: AdminRecord, val backendMessage: String) : OwnerAuthStatus
    data class Denied(val reason: String, val setupRequired: Boolean) : OwnerAuthStatus
}

class AdmitRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val backendApi: BackendApiClient = BackendApiClient()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun newValidId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    suspend fun verifyOwnerAuthorization(): OwnerAuthStatus {
        val user = auth.currentUser ?: return OwnerAuthStatus.Denied("Not signed in.", setupRequired = false)
        if (!user.isEmailVerified) {
            return OwnerAuthStatus.Denied("Owner Google account email must be verified.", setupRequired = true)
        }
        return try {
            val backendCheck = backendApi.verifyOwnerWithBackend()
            if (backendCheck.configured && !backendCheck.isOwner) {
                return OwnerAuthStatus.Denied(backendCheck.message, setupRequired = true)
            }
            val adminSnap = db.collection("admins").document(user.uid).get().await()
            if (!adminSnap.exists()) {
                return OwnerAuthStatus.Denied(
                    "No verified /admins/${user.uid} owner record found. Configure OWNER_EMAIL in the backend and run the owner bootstrap script.",
                    setupRequired = true
                )
            }
            val record = adminSnap.toObject(AdminRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return OwnerAuthStatus.Denied("Invalid /admins record.", setupRequired = true)
            if (record.role != "OWNER") {
                return OwnerAuthStatus.Denied("Owner role required.", setupRequired = true)
            }
            OwnerAuthStatus.Verified(record, backendCheck.message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "admins/${user.uid}")
            OwnerAuthStatus.Denied(
                e.localizedMessage ?: "Owner verification failed.",
                setupRequired = true
            )
        }
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        emitAll(
            db.collection("users").snapshots()
                .map { snap -> snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "users")
                    throw error
                }
        )
    }

    fun observeAllChannels(): Flow<List<Channel>> = flow {
        emitAll(
            db.collection("channels").snapshots()
                .map { snap -> snap.toObjects(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "channels")
                    throw error
                }
        )
    }

    fun observeAllVideos(): Flow<List<Video>> = flow {
        emitAll(
            db.collection("videos").snapshots()
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

    fun observeAllReports(): Flow<List<Report>> = flow {
        emitAll(
            db.collection("reports").snapshots()
                .map { snap ->
                    snap.toObjects(Report::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "reports")
                    throw error
                }
        )
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = flow {
        emitAll(
            db.collection("auditLogs").snapshots()
                .map { snap ->
                    snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "auditLogs")
                    throw error
                }
        )
    }

    fun observeModerationActions(): Flow<List<ModerationAction>> = flow {
        emitAll(
            db.collection("moderationActions").snapshots()
                .map { snap ->
                    snap.toObjects(ModerationAction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.LIST, "moderationActions")
                    throw error
                }
        )
    }

    fun observeProcessingJobs(): Flow<List<VideoProcessingJob>> = flow {
        emitAll(
            db.collection("videoProcessingJobs").snapshots()
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

    fun observePlatformSettings(): Flow<PlatformSettings> = flow {
        emitAll(
            db.collection("platformSettings").document("global").snapshots()
                .map { snap ->
                    snap.toObject(PlatformSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: PlatformSettings()
                }
                .catch { error ->
                    handleFirestoreError(unwrapFirestoreThrowable(error), OperationType.GET, "platformSettings/global")
                    emit(PlatformSettings())
                }
        )
    }

    suspend fun moderateUser(
        targetUser: UserProfile,
        actionType: String, // SUSPEND_USER, RESTORE_USER, BLOCK_USER, UNBLOCK_USER, DISABLE_UPLOADS, ENABLE_UPLOADS
        reason: String
    ): Result<Unit> {
        val owner = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val cleanReason = reason.trim().take(500)
        require(cleanReason.length >= 2) { "Moderation reason is required." }
        return try {
            val batch = db.batch()
            val userRef = db.collection("users").document(targetUser.userId)
            val updates = mutableMapOf<String, Any>(
                "moderationReason" to cleanReason,
                "moderatedBy" to owner.uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            when (actionType) {
                "SUSPEND_USER" -> {
                    updates["status"] = "suspended"
                    updates["uploadDisabled"] = true
                }
                "BLOCK_USER" -> {
                    updates["status"] = "blocked"
                    updates["uploadDisabled"] = true
                }
                "RESTORE_USER", "UNBLOCK_USER" -> {
                    updates["status"] = "active"
                    updates["uploadDisabled"] = false
                }
                "DISABLE_UPLOADS" -> updates["uploadDisabled"] = true
                "ENABLE_UPLOADS" -> updates["uploadDisabled"] = false
            }
            batch.update(userRef, updates)
            appendImmutableModerationAndAudit(
                batch = batch,
                ownerUid = owner.uid,
                ownerEmail = owner.email.orEmpty(),
                actionType = actionType,
                targetType = "USER",
                targetId = targetUser.userId,
                reason = cleanReason,
                summary = "$actionType on ${targetUser.displayName}: $cleanReason",
                notifyUserId = targetUser.userId,
                notifyTitle = "Account Moderation Update",
                notifyBody = "Action: $actionType. Reason: $cleanReason"
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${targetUser.userId}")
            Result.failure(e)
        }
    }

    suspend fun safeDeleteUser(targetUser: UserProfile, reason: String): Result<Unit> {
        val owner = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val cleanReason = reason.trim().take(500)
        require(cleanReason.length >= 2) { "Deletion reason is required." }
        return try {
            val batch = db.batch()
            batch.delete(db.collection("users").document(targetUser.userId))
            appendImmutableModerationAndAudit(
                batch = batch,
                ownerUid = owner.uid,
                ownerEmail = owner.email.orEmpty(),
                actionType = "DELETE_USER",
                targetType = "USER",
                targetId = targetUser.userId,
                reason = cleanReason,
                summary = "Deleted user ${targetUser.displayName} (${targetUser.userId}): $cleanReason"
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/${targetUser.userId}")
            Result.failure(e)
        }
    }

    suspend fun moderateVideo(
        video: Video,
        actionType: String, // BLOCK_VIDEO, RESTORE_VIDEO, DELETE_VIDEO, CHANGE_VISIBILITY
        newVisibility: String? = null,
        reason: String
    ): Result<Unit> {
        val owner = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val cleanReason = reason.trim().take(500)
        require(cleanReason.length >= 2) { "Moderation reason is required." }
        return try {
            val batch = db.batch()
            val vidRef = db.collection("videos").document(video.id)
            when (actionType) {
                "BLOCK_VIDEO" -> batch.update(
                    vidRef,
                    mapOf("moderationStatus" to "BLOCKED", "updatedAt" to FieldValue.serverTimestamp())
                )
                "RESTORE_VIDEO" -> batch.update(
                    vidRef,
                    mapOf("moderationStatus" to "ACTIVE", "updatedAt" to FieldValue.serverTimestamp())
                )
                "CHANGE_VISIBILITY" -> batch.update(
                    vidRef,
                    mapOf("visibility" to (newVisibility ?: "PRIVATE"), "updatedAt" to FieldValue.serverTimestamp())
                )
                "DELETE_VIDEO" -> batch.delete(vidRef)
            }
            appendImmutableModerationAndAudit(
                batch = batch,
                ownerUid = owner.uid,
                ownerEmail = owner.email.orEmpty(),
                actionType = actionType,
                targetType = "VIDEO",
                targetId = video.id,
                reason = cleanReason,
                summary = "$actionType on '${video.title}': $cleanReason",
                notifyUserId = video.ownerId,
                notifyTitle = "Video Moderation Notice",
                notifyBody = "'${video.title}': $actionType ($cleanReason)"
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "videos/${video.id}")
            Result.failure(e)
        }
    }

    suspend fun resolveReport(
        report: Report,
        newStatus: String,
        resolutionNote: String
    ): Result<Unit> {
        val owner = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val note = resolutionNote.trim().take(500).ifBlank { "Reviewed by owner" }
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("reports").document(report.id),
                mapOf(
                    "status" to newStatus,
                    "resolutionNote" to note,
                    "resolvedBy" to owner.uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            appendImmutableModerationAndAudit(
                batch = batch,
                ownerUid = owner.uid,
                ownerEmail = owner.email.orEmpty(),
                actionType = "HANDLE_REPORT",
                targetType = "REPORT",
                targetId = report.id,
                reason = note,
                summary = "Report ${report.id} (${report.targetType}) marked $newStatus: $note"
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "reports/${report.id}")
            Result.failure(e)
        }
    }

    suspend fun savePlatformSettings(settings: PlatformSettings): Result<Unit> {
        val owner = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        return try {
            val batch = db.batch()
            val payload = mapOf(
                "registrationsEnabled" to settings.registrationsEnabled,
                "uploadsEnabled" to settings.uploadsEnabled,
                "maintenanceMode" to settings.maintenanceMode,
                "maxUploadSizeMb" to settings.maxUploadSizeMb.coerceIn(10L, 10240L),
                "dailyUploadLimit" to settings.dailyUploadLimit.coerceIn(1L, 500L),
                "allowedFormats" to settings.allowedFormats.take(10),
                "categories" to settings.categories.take(25),
                "supportedLanguages" to settings.supportedLanguages.take(10),
                "autoFlagKeywords" to settings.autoFlagKeywords.take(50),
                "notificationsEnabled" to settings.notificationsEnabled,
                "transcoderConfigured" to settings.transcoderConfigured,
                "totalStorageQuotaMb" to settings.totalStorageQuotaMb.coerceAtLeast(100L),
                "updatedBy" to owner.uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(db.collection("platformSettings").document("global"), payload)
            appendImmutableModerationAndAudit(
                batch = batch,
                ownerUid = owner.uid,
                ownerEmail = owner.email.orEmpty(),
                actionType = "UPDATE_SETTINGS",
                targetType = "SETTINGS",
                targetId = "global",
                reason = "Updated platform configuration",
                summary = "Platform settings updated (maintenance=${settings.maintenanceMode}, uploads=${settings.uploadsEnabled})"
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "platformSettings/global")
            Result.failure(e)
        }
    }

    private fun appendImmutableModerationAndAudit(
        batch: com.google.firebase.firestore.WriteBatch,
        ownerUid: String,
        ownerEmail: String,
        actionType: String,
        targetType: String,
        targetId: String,
        reason: String,
        summary: String,
        notifyUserId: String? = null,
        notifyTitle: String? = null,
        notifyBody: String? = null
    ) {
        val modId = newValidId("mod")
        batch.set(
            db.collection("moderationActions").document(modId),
            mapOf(
                "adminUid" to ownerUid,
                "adminEmail" to ownerEmail.ifBlank { "owner@youtv.local" },
                "actionType" to actionType,
                "targetType" to targetType,
                "targetId" to targetId.take(128),
                "reason" to reason.take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = newValidId("log")
        batch.set(
            db.collection("auditLogs").document(logId),
            mapOf(
                "actorUid" to ownerUid,
                "action" to actionType,
                "targetType" to targetType,
                "targetId" to targetId.take(128),
                "result" to "SUCCESS",
                "summary" to summary.take(400),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        if (!notifyUserId.isNullOrBlank() && !notifyTitle.isNullOrBlank() && !notifyBody.isNullOrBlank()) {
            val notifId = newValidId("ntf")
            batch.set(
                db.collection("notifications").document(notifId),
                mapOf(
                    "userId" to notifyUserId,
                    "title" to notifyTitle.take(120),
                    "body" to notifyBody.take(500),
                    "notificationType" to "MODERATION",
                    "readFlag" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }
}
