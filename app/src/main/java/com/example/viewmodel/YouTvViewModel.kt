package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.BackendTranscodeResponse
import com.example.data.firebase.YouTvRepository
import com.example.data.firebase.unwrapFirestoreThrowable
import com.example.data.model.BlockedUser
import com.example.data.model.Channel
import com.example.data.model.Comment
import com.example.data.model.NotificationItem
import com.example.data.model.PlatformSettings
import com.example.data.model.Playlist
import com.example.data.model.SearchHistoryEntry
import com.example.data.model.ShortVideo
import com.example.data.model.Subscription
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.Video
import com.example.data.model.VideoProcessingJob
import com.example.data.model.WatchHistoryEntry
import com.example.localization.AppLanguage
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedSortMode { PERSONALIZED, CHRONOLOGICAL }

class YouTvViewModel(
    private val repository: YouTvRepository,
    val currentUserId: String
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("Home")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(FeedSortMode.PERSONALIZED)
    val sortMode: StateFlow<FeedSortMode> = _sortMode.asStateFlow()

    private val _activeWatchVideoId = MutableStateFlow<String?>(null)
    val activeWatchVideoId: StateFlow<String?> = _activeWatchVideoId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val platformSettings: StateFlow<PlatformSettings> = repository.observePlatformSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PlatformSettings())

    val publicVideosState: StateFlow<UiState<List<Video>>> = repository.observePublicVideos()
        .map<List<Video>, UiState<List<Video>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreThrowable(error)
            emit(UiState.Error(root.message ?: "Failed to load videos"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val myVideos: StateFlow<List<Video>> = repository.observeMyVideos(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allChannels: StateFlow<List<Channel>> = repository.observeAllChannels()
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myChannels: StateFlow<List<Channel>> = repository.observeMyChannels(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val publicShorts: StateFlow<List<ShortVideo>> = repository.observePublicShorts()
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val subscriptions: StateFlow<List<Subscription>> = repository.observeMySubscriptions(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myReactions: StateFlow<Map<String, String>> = repository.observeMyReactions(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    val playlists: StateFlow<List<Playlist>> = repository.observeMyPlaylists(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val watchHistory: StateFlow<List<WatchHistoryEntry>> = repository.observeWatchHistory(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntry>> = repository.observeSearchHistory(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<NotificationItem>> = repository.observeNotifications(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val blockedUsers: StateFlow<List<BlockedUser>> = repository.observeBlockedUsers(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val processingJobs: StateFlow<List<VideoProcessingJob>> = repository.observeMyProcessingJobs(currentUserId)
        .catch { error ->
            unwrapFirestoreThrowable(error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeVideoComments: StateFlow<List<Comment>> = _activeWatchVideoId
        .flatMapLatest { videoId ->
            if (videoId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeComments(videoId)
                .catch { error ->
                    unwrapFirestoreThrowable(error)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val filteredFeedVideos: StateFlow<UiState<List<Video>>> = combine(
        publicVideosState,
        _selectedCategory,
        _searchQuery,
        _sortMode,
        subscriptions,
        blockedUsers,
        userProfile
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val state = values[0] as UiState<List<Video>>
        val category = values[1] as String
        val query = (values[2] as String).trim()
        val sortMode = values[3] as FeedSortMode
        @Suppress("UNCHECKED_CAST")
        val subs = values[4] as List<Subscription>
        @Suppress("UNCHECKED_CAST")
        val blocked = values[5] as List<BlockedUser>
        val profile = values[6] as UserProfile?

        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> {
                val blockedIds = blocked.map { it.blockedUserId }.toSet()
                val subscribedChannelIds = subs.map { it.channelId }.toSet()
                val preferredLang = profile?.preferredLanguage ?: "en"
                val restrict18Plus = profile?.restrictedMode == true

                val filtered = state.data.filter { video ->
                    if (video.ownerId in blockedIds) return@filter false
                    if (restrict18Plus && video.ageRestricted) return@filter false
                    val categoryMatches = when (category) {
                        "Home", "Trending", "Latest" -> true
                        "Shorts" -> video.shortForm
                        else -> video.category.equals(category, ignoreCase = true)
                    }
                    val queryMatches = if (query.isBlank()) true else {
                        video.title.contains(query, ignoreCase = true) ||
                            video.channelName.contains(query, ignoreCase = true) ||
                            video.description.contains(query, ignoreCase = true) ||
                            video.tags.any { it.contains(query, ignoreCase = true) }
                    }
                    categoryMatches && queryMatches
                }

                val sorted = when {
                    category == "Trending" -> filtered.sortedByDescending { it.viewCount + (it.likeCount * 3L) }
                    category == "Latest" || sortMode == FeedSortMode.CHRONOLOGICAL -> filtered
                    else -> filtered.sortedByDescending { video ->
                        var score = (video.viewCount + video.likeCount * 4L).toDouble()
                        if (video.channelId in subscribedChannelIds) score += 5000.0
                        if (video.language == preferredLang) score += 1500.0
                        score
                    }
                }
                UiState.Success(sorted)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun syncSignedInUser(user: FirebaseUser, preferredLanguage: AppLanguage) {
        viewModelScope.launch {
            repository.ensureUserProfile(user, preferredLanguage.code)
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun commitSearch(query: String) {
        _searchQuery.value = query
        if (query.isNotBlank()) {
            viewModelScope.launch { repository.saveSearchQuery(query) }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory(searchHistory.value)
        }
    }

    fun setSortMode(mode: FeedSortMode) {
        _sortMode.value = mode
    }

    fun openVideo(video: Video) {
        _activeWatchVideoId.value = video.id
        viewModelScope.launch {
            repository.recordVideoViewAndHistory(video)
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun syncLanguagePreference(language: AppLanguage) {
        viewModelScope.launch {
            repository.updatePreferredLanguage(language.code)
        }
    }

    fun setRestrictedMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateRestrictedMode(enabled)
        }
    }

    fun createChannel(
        name: String,
        handle: String,
        description: String,
        avatarUrl: String?,
        coverUrl: String?,
        onDone: (Result<Channel>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.createChannel(name, handle, description, avatarUrl, coverUrl)
            res.onFailure { _statusBanner.value = it.localizedMessage }
            onDone(res)
        }
    }

    fun publishVideo(
        channel: Channel,
        title: String,
        description: String,
        playbackUrl: String,
        thumbnailUrl: String?,
        category: String,
        tagsCsv: String,
        language: String,
        visibility: String,
        shortForm: Boolean,
        ageRestricted: Boolean,
        durationSec: Long,
        fileSizeBytes: Long,
        onResult: (Result<Pair<Video, BackendTranscodeResponse>>) -> Unit
    ) {
        viewModelScope.launch {
            val tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val res = repository.publishVideo(
                channel = channel,
                title = title,
                description = description,
                playbackUrl = playbackUrl,
                thumbnailUrl = thumbnailUrl,
                category = category,
                tags = tags,
                language = language,
                visibility = visibility,
                shortForm = shortForm,
                ageRestricted = ageRestricted,
                durationSec = durationSec,
                fileSizeBytes = fileSizeBytes
            )
            res.onFailure { _statusBanner.value = it.localizedMessage }
            onResult(res)
        }
    }

    fun updateVideoDetails(videoId: String, title: String, description: String, category: String, visibility: String) {
        viewModelScope.launch {
            repository.updateVideoDetails(videoId, title, description, category, visibility)
                .onSuccess { _statusBanner.value = "Video updated" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun deleteVideo(video: Video) {
        viewModelScope.launch {
            repository.deleteOwnVideo(video)
                .onSuccess { _statusBanner.value = "Video deleted" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun toggleReaction(videoId: String, reaction: String) {
        viewModelScope.launch {
            repository.toggleVideoReaction(videoId, reaction)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun postComment(video: Video, text: String, parentCommentId: String? = null) {
        viewModelScope.launch {
            repository.postComment(video, text, parentCommentId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun hideComment(commentId: String) {
        viewModelScope.launch {
            repository.hideComment(commentId)
        }
    }

    fun deleteComment(comment: Comment) {
        viewModelScope.launch {
            repository.deleteComment(comment)
        }
    }

    fun toggleSubscription(channel: Channel) {
        viewModelScope.launch {
            repository.toggleSubscription(channel)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun saveToWatchLater(videoId: String) {
        viewModelScope.launch {
            repository.saveToWatchLater(videoId)
                .onSuccess { _statusBanner.value = "Saved to Watch Later" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun createPlaylist(title: String, description: String, initialVideoId: String? = null) {
        viewModelScope.launch {
            repository.createPlaylist(title, description, initialVideoId)
                .onSuccess { _statusBanner.value = "Playlist created" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }

    fun blockCreator(ownerId: String, name: String) {
        viewModelScope.launch {
            repository.blockCreator(ownerId, name)
                .onSuccess { _statusBanner.value = "Blocked $name" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun unblockCreator(blockId: String) {
        viewModelScope.launch {
            repository.unblockCreator(blockId)
        }
    }

    fun submitReport(
        targetType: String,
        targetId: String,
        targetOwnerId: String?,
        reason: String,
        details: String,
        copyrightUrl: String? = null
    ) {
        viewModelScope.launch {
            repository.submitReport(targetType, targetId, targetOwnerId, reason, details, copyrightUrl)
                .onSuccess { _statusBanner.value = "Submitted for review" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun requestAccountDeletion(reason: String) {
        viewModelScope.launch {
            repository.requestAccountDeletion(reason)
                .onSuccess { _statusBanner.value = "Account deletion request submitted" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }
}
