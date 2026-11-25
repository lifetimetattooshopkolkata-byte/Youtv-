package com.example.admit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AdmitRepository
import com.example.data.firebase.OwnerAuthStatus
import com.example.data.firebase.unwrapFirestoreThrowable
import com.example.data.model.AuditLog
import com.example.data.model.Channel
import com.example.data.model.ModerationAction
import com.example.data.model.PlatformSettings
import com.example.data.model.Report
import com.example.data.model.UserProfile
import com.example.data.model.Video
import com.example.data.model.VideoProcessingJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChartTimeframe { DAILY, WEEKLY, MONTHLY }

data class AdmitDashboardMetrics(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val suspendedUsers: Int = 0,
    val totalChannels: Int = 0,
    val totalVideos: Int = 0,
    val publicVideos: Int = 0,
    val privateVideos: Int = 0,
    val totalViews: Long = 0L,
    val totalSubscribers: Long = 0L,
    val storageUsedMb: Double = 0.0,
    val openReports: Int = 0
)

class AdmitViewModel(
    private val repository: AdmitRepository,
    val currentUserId: String
) : ViewModel() {

    private val _ownerAuthStatus = MutableStateFlow<OwnerAuthStatus>(OwnerAuthStatus.Checking)
    val ownerAuthStatus: StateFlow<OwnerAuthStatus> = _ownerAuthStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _timeframe = MutableStateFlow(ChartTimeframe.WEEKLY)
    val timeframe: StateFlow<ChartTimeframe> = _timeframe.asStateFlow()

    init {
        recheckOwnerAuthorization()
    }

    fun recheckOwnerAuthorization() {
        viewModelScope.launch {
            _ownerAuthStatus.value = OwnerAuthStatus.Checking
            _ownerAuthStatus.value = repository.verifyOwnerAuthorization()
        }
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun setTimeframe(tf: ChartTimeframe) {
        _timeframe.value = tf
    }

    val users: StateFlow<List<UserProfile>> = repository.observeAllUsers()
        .catch { error -> unwrapFirestoreThrowable(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val channels: StateFlow<List<Channel>> = repository.observeAllChannels()
        .catch { error -> unwrapFirestoreThrowable(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val videos: StateFlow<List<Video>> = repository.observeAllVideos()
        .catch { error -> unwrapFirestoreThrowable(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val reports: StateFlow<List<Report>> = repository.observeAllReports()
        .catch { error -> unwrapFirestoreThrowable(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = repository.observeAuditLogs()
        .catch { error -> unwrapFirestoreThrowable(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val moderationActions: StateFlow<List<ModerationAction>> = repository.observeModerationActions()
        .catch { error -> unwrapFirestoreThrowable(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val processingJobs: StateFlow<List<VideoProcessingJob>> = repository.observeProcessingJobs()
        .catch { error -> unwrapFirestoreThrowable(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val platformSettings: StateFlow<PlatformSettings> = repository.observePlatformSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PlatformSettings())

    val metrics: StateFlow<AdmitDashboardMetrics> = combine(
        users,
        channels,
        videos,
        reports
    ) { userList, channelList, videoList, reportList ->
        val storageBytes = userList.sumOf { it.storageUsedBytes } + videoList.sumOf { it.fileSizeBytes }
        AdmitDashboardMetrics(
            totalUsers = userList.size,
            activeUsers = userList.count { it.status == "active" },
            suspendedUsers = userList.count { it.status == "suspended" || it.status == "blocked" },
            totalChannels = channelList.size,
            totalVideos = videoList.size,
            publicVideos = videoList.count { it.visibility == "PUBLIC" },
            privateVideos = videoList.count { it.visibility != "PUBLIC" },
            totalViews = videoList.sumOf { it.viewCount },
            totalSubscribers = channelList.sumOf { it.subscriberCount },
            storageUsedMb = storageBytes / (1024.0 * 1024.0),
            openReports = reportList.count { it.status == "OPEN" || it.status == "UNDER_REVIEW" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdmitDashboardMetrics())

    fun moderateUser(user: UserProfile, actionType: String, reason: String) {
        viewModelScope.launch {
            repository.moderateUser(user, actionType, reason)
                .onSuccess { _statusMessage.value = "$actionType completed for ${user.displayName}" }
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun deleteUser(user: UserProfile, reason: String) {
        viewModelScope.launch {
            repository.safeDeleteUser(user, reason)
                .onSuccess { _statusMessage.value = "User ${user.displayName} deleted" }
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun moderateVideo(video: Video, actionType: String, newVisibility: String? = null, reason: String) {
        viewModelScope.launch {
            repository.moderateVideo(video, actionType, newVisibility, reason)
                .onSuccess { _statusMessage.value = "$actionType applied to '${video.title}'" }
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun resolveReport(report: Report, newStatus: String, note: String) {
        viewModelScope.launch {
            repository.resolveReport(report, newStatus, note)
                .onSuccess { _statusMessage.value = "Report marked $newStatus" }
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }

    fun saveSettings(settings: PlatformSettings) {
        viewModelScope.launch {
            repository.savePlatformSettings(settings)
                .onSuccess { _statusMessage.value = "Platform settings saved" }
                .onFailure { _statusMessage.value = it.localizedMessage }
        }
    }
}
