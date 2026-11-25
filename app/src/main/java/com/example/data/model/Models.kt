package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class AdminRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val role: String = "OWNER",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val preferredLanguage: String = "en",
    val status: String = "active", // active, suspended, blocked, deletion_requested
    val uploadDisabled: Boolean = false,
    val restrictedMode: Boolean = false,
    val emailVerified: Boolean = false,
    val storageUsedBytes: Long = 0L,
    val moderationReason: String? = null,
    val moderatedBy: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isRestrictedAccount: Boolean
        get() = status == "suspended" || status == "blocked"
}

@Keep
@IgnoreExtraProperties
data class Channel(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val handle: String = "",
    val description: String = "",
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val subscriberCount: Long = 0L,
    val videoCount: Long = 0L,
    val totalViews: Long = 0L,
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Video(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val channelHandle: String = "",
    val channelAvatarUrl: String? = null,
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String? = null,
    val playbackUrl: String = "",
    val hlsManifestUrl: String? = null,
    val captionsUrl: String? = null,
    val category: String = "Technology",
    val tags: List<String> = emptyList(),
    val language: String = "en",
    val visibility: String = "PUBLIC", // PUBLIC, PRIVATE, UNLISTED
    val processingStatus: String = "READY", // UPLOADING, QUEUED, TRANSCODING, READY, FAILED, FALLBACK_DIRECT
    val moderationStatus: String = "ACTIVE", // ACTIVE, BLOCKED, REMOVED
    val shortForm: Boolean = false,
    val ageRestricted: Boolean = false,
    val durationSec: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val dislikeCount: Long = 0L,
    val commentCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ShortVideo(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val channelAvatarUrl: String? = null,
    val title: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String? = null,
    val language: String = "en",
    val visibility: String = "PUBLIC",
    val moderationStatus: String = "ACTIVE",
    val durationSec: Long = 30L,
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Comment(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val videoOwnerId: String = "",
    val parentCommentId: String? = null,
    val text: String = "",
    val moderationStatus: String = "ACTIVE",
    val likeCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LikeReaction(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val userId: String = "",
    val reaction: String = "LIKE", // LIKE, DISLIKE
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Subscription(
    @DocumentId val id: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val channelAvatarUrl: String? = null,
    val subscriberId: String = "",
    val channelOwnerId: String = "",
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Playlist(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val description: String = "",
    val visibility: String = "PRIVATE",
    val playlistType: String = "CUSTOM", // CUSTOM, WATCH_LATER
    val videoIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WatchHistoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val videoId: String = "",
    val videoTitle: String = "",
    val thumbnailUrl: String? = null,
    val channelName: String = "",
    val progressSec: Long = 0L,
    val durationSec: Long = 0L,
    @ServerTimestamp val watchedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SearchHistoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val query: String = "",
    @ServerTimestamp val searchedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val notificationType: String = "SYSTEM",
    val targetVideoId: String? = null,
    val readFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Report(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "VIDEO", // VIDEO, USER, COMMENT, COPYRIGHT, APPEAL
    val targetId: String = "",
    val targetOwnerId: String? = null,
    val reason: String = "",
    val details: String = "",
    val copyrightWorkUrl: String? = null,
    val status: String = "OPEN", // OPEN, UNDER_REVIEW, RESOLVED, DISMISSED
    val resolutionNote: String? = null,
    val resolvedBy: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ModerationAction(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    val actionType: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val result: String = "SUCCESS",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PlatformSettings(
    @DocumentId val id: String = "global",
    val registrationsEnabled: Boolean = true,
    val uploadsEnabled: Boolean = true,
    val maintenanceMode: Boolean = false,
    val maxUploadSizeMb: Long = 512L,
    val dailyUploadLimit: Long = 20L,
    val allowedFormats: List<String> = listOf("mp4", "webm", "mov"),
    val categories: List<String> = listOf("Music", "Education", "Entertainment", "Technology", "Gaming", "Sports"),
    val supportedLanguages: List<String> = listOf("en", "bn", "hi"),
    val autoFlagKeywords: List<String> = listOf("spam", "scam"),
    val notificationsEnabled: Boolean = true,
    val transcoderConfigured: Boolean = false,
    val totalStorageQuotaMb: Long = 10240L,
    val updatedBy: String? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoProcessingJob(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val ownerId: String = "",
    val inputPath: String = "",
    val outputManifestPath: String? = null,
    val status: String = "FALLBACK_DIRECT",
    val progressPercent: Long = 100L,
    val errorMessage: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val blockerId: String = "",
    val blockedUserId: String = "",
    val blockedUserName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
