package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.firebase.YouTvRepository
import com.example.data.firebase.signOutUser
import com.example.data.model.Channel
import com.example.data.model.UiState
import com.example.data.model.Video
import com.example.localization.AppLanguage
import com.example.localization.LocalStrings
import com.example.viewmodel.YouTvViewModel
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

enum class YouTvTab { HOME, SHORTS, UPLOAD, SUBSCRIPTIONS, PROFILE }

@Composable
fun YouTvAuthenticatedApp(
    firebaseUser: FirebaseUser,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val strings = LocalStrings.current

    val viewModel: YouTvViewModel = viewModel(
        key = firebaseUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                YouTvViewModel(YouTvRepository(db), firebaseUser.uid)
            }
        }
    )

    LaunchedEffect(firebaseUser.uid) {
        viewModel.syncSignedInUser(firebaseUser, currentLanguage)
    }

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val platformSettings by viewModel.platformSettings.collectAsStateWithLifecycle()
    val feedState by viewModel.filteredFeedVideos.collectAsStateWithLifecycle()
    val publicVideosState by viewModel.publicVideosState.collectAsStateWithLifecycle()
    val myVideos by viewModel.myVideos.collectAsStateWithLifecycle()
    val allChannels by viewModel.allChannels.collectAsStateWithLifecycle()
    val myChannels by viewModel.myChannels.collectAsStateWithLifecycle()
    val publicShorts by viewModel.publicShorts.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val myReactions by viewModel.myReactions.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val processingJobs by viewModel.processingJobs.collectAsStateWithLifecycle()
    val activeComments by viewModel.activeVideoComments.collectAsStateWithLifecycle()

    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(YouTvTab.HOME) }
    var activeWatchVideo by remember { mutableStateOf<Video?>(null) }
    var activeChannel by remember { mutableStateOf<Channel?>(null) }
    var showStudio by remember { mutableStateOf(false) }
    var uploadShortDefault by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    if (platformSettings.maintenanceMode) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(strings.maintenanceTitle, style = MaterialTheme.typography.headlineSmall)
                    Text(strings.maintenanceBody, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        return
    }

    val allPublicVideos = (publicVideosState as? UiState.Success)?.data.orEmpty()
    val subscribedChannelIds = subscriptions.map { it.channelId }.toSet()
    val likedVideoIds = myReactions.filterValues { it == "LIKE" }.keys
    val unreadNotifications = notifications.count { !it.readFlag }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp
        val columnsCount = when {
            maxWidth >= 1000.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isExpanded && activeWatchVideo == null && activeChannel == null && !showStudio) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentTab == YouTvTab.HOME,
                            onClick = { currentTab = YouTvTab.HOME },
                            icon = { Icon(Icons.Filled.Home, contentDescription = strings.navHome) },
                            label = { Text(strings.navHome) },
                            modifier = Modifier.testTag("nav_home")
                        )
                        NavigationBarItem(
                            selected = currentTab == YouTvTab.SHORTS,
                            onClick = { currentTab = YouTvTab.SHORTS },
                            icon = { Icon(Icons.Filled.PlayCircle, contentDescription = strings.navShorts) },
                            label = { Text(strings.navShorts) },
                            modifier = Modifier.testTag("nav_shorts")
                        )
                        NavigationBarItem(
                            selected = currentTab == YouTvTab.UPLOAD,
                            onClick = {
                                uploadShortDefault = false
                                currentTab = YouTvTab.UPLOAD
                            },
                            icon = { Icon(Icons.Filled.AddCircle, contentDescription = strings.navUpload) },
                            label = { Text(strings.navUpload) },
                            modifier = Modifier.testTag("nav_upload")
                        )
                        NavigationBarItem(
                            selected = currentTab == YouTvTab.SUBSCRIPTIONS,
                            onClick = { currentTab = YouTvTab.SUBSCRIPTIONS },
                            icon = { Icon(Icons.Filled.Subscriptions, contentDescription = strings.navSubscriptions) },
                            label = { Text(strings.navSubscriptions) },
                            modifier = Modifier.testTag("nav_subscriptions")
                        )
                        NavigationBarItem(
                            selected = currentTab == YouTvTab.PROFILE,
                            onClick = { currentTab = YouTvTab.PROFILE },
                            icon = { Icon(Icons.Filled.Person, contentDescription = strings.navProfile) },
                            label = { Text(strings.navProfile) },
                            modifier = Modifier.testTag("nav_profile")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpanded && activeWatchVideo == null && activeChannel == null && !showStudio) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = currentTab == YouTvTab.HOME,
                            onClick = { currentTab = YouTvTab.HOME },
                            icon = { Icon(Icons.Filled.Home, contentDescription = strings.navHome) },
                            label = { Text(strings.navHome) }
                        )
                        NavigationRailItem(
                            selected = currentTab == YouTvTab.SHORTS,
                            onClick = { currentTab = YouTvTab.SHORTS },
                            icon = { Icon(Icons.Filled.PlayCircle, contentDescription = strings.navShorts) },
                            label = { Text(strings.navShorts) }
                        )
                        NavigationRailItem(
                            selected = currentTab == YouTvTab.UPLOAD,
                            onClick = {
                                uploadShortDefault = false
                                currentTab = YouTvTab.UPLOAD
                            },
                            icon = { Icon(Icons.Filled.AddCircle, contentDescription = strings.navUpload) },
                            label = { Text(strings.navUpload) }
                        )
                        NavigationRailItem(
                            selected = currentTab == YouTvTab.SUBSCRIPTIONS,
                            onClick = { currentTab = YouTvTab.SUBSCRIPTIONS },
                            icon = { Icon(Icons.Filled.Subscriptions, contentDescription = strings.navSubscriptions) },
                            label = { Text(strings.navSubscriptions) }
                        )
                        NavigationRailItem(
                            selected = currentTab == YouTvTab.PROFILE,
                            onClick = { currentTab = YouTvTab.PROFILE },
                            icon = { Icon(Icons.Filled.Person, contentDescription = strings.navProfile) },
                            label = { Text(strings.navProfile) }
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (userProfile?.isRestrictedAccount == true) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(strings.suspendedBannerTitle, style = MaterialTheme.typography.titleSmall)
                                Text(strings.suspendedBannerBody, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    when {
                        activeWatchVideo != null -> {
                            val liveVid = allPublicVideos.firstOrNull { it.id == activeWatchVideo!!.id }
                                ?: myVideos.firstOrNull { it.id == activeWatchVideo!!.id }
                                ?: activeWatchVideo!!
                            val vidChannel = allChannels.firstOrNull { it.id == liveVid.channelId }
                            WatchScreen(
                                video = liveVid,
                                currentUserId = firebaseUser.uid,
                                channel = vidChannel,
                                isSubscribed = liveVid.channelId in subscribedChannelIds,
                                myReaction = myReactions[liveVid.id],
                                comments = activeComments,
                                upNextVideos = allPublicVideos.filter { it.id != liveVid.id },
                                onBack = { activeWatchVideo = null },
                                onOpenVideo = { next ->
                                    activeWatchVideo = next
                                    viewModel.openVideo(next)
                                },
                                onOpenChannel = { ch ->
                                    activeWatchVideo = null
                                    activeChannel = ch
                                },
                                onToggleReaction = viewModel::toggleReaction,
                                onToggleSubscribe = viewModel::toggleSubscription,
                                onSaveToWatchLater = viewModel::saveToWatchLater,
                                onPostComment = viewModel::postComment,
                                onHideComment = viewModel::hideComment,
                                onDeleteComment = viewModel::deleteComment,
                                onReportVideo = { type, details, copyUrl ->
                                    viewModel.submitReport(type, liveVid.id, liveVid.ownerId, type, details, copyUrl)
                                },
                                onBlockCreator = viewModel::blockCreator
                            )
                        }

                        activeChannel != null -> {
                            val ch = allChannels.firstOrNull { it.id == activeChannel!!.id } ?: activeChannel!!
                            ChannelScreen(
                                channel = ch,
                                currentUserId = firebaseUser.uid,
                                isSubscribed = ch.id in subscribedChannelIds,
                                channelVideos = allPublicVideos.filter { it.channelId == ch.id },
                                onBack = { activeChannel = null },
                                onToggleSubscribe = viewModel::toggleSubscription,
                                onOpenVideo = { vid ->
                                    activeChannel = null
                                    activeWatchVideo = vid
                                    viewModel.openVideo(vid)
                                }
                            )
                        }

                        showStudio -> {
                            StudioScreen(
                                myChannels = myChannels,
                                myVideos = myVideos,
                                processingJobs = processingJobs,
                                onBack = { showStudio = false },
                                onEditVideo = viewModel::updateVideoDetails,
                                onDeleteVideo = viewModel::deleteVideo
                            )
                        }

                        else -> when (currentTab) {
                            YouTvTab.HOME -> HomeScreen(
                                feedState = feedState,
                                selectedCategory = selectedCategory,
                                searchQuery = searchQuery,
                                sortMode = sortMode,
                                searchHistory = searchHistory,
                                unreadNotificationsCount = unreadNotifications,
                                columnsCount = columnsCount,
                                onSelectCategory = viewModel::selectCategory,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onCommitSearch = viewModel::commitSearch,
                                onClearSearchHistory = viewModel::clearSearchHistory,
                                onToggleSortMode = viewModel::setSortMode,
                                onOpenNotifications = { currentTab = YouTvTab.PROFILE },
                                onOpenVideo = { vid ->
                                    activeWatchVideo = vid
                                    viewModel.openVideo(vid)
                                },
                                onOpenUpload = {
                                    uploadShortDefault = false
                                    currentTab = YouTvTab.UPLOAD
                                }
                            )

                            YouTvTab.SHORTS -> ShortsScreen(
                                shorts = publicShorts,
                                channels = allChannels,
                                subscribedChannelIds = subscribedChannelIds,
                                likedVideoIds = likedVideoIds,
                                onToggleLike = { id -> viewModel.toggleReaction(id, "LIKE") },
                                onToggleSubscribe = viewModel::toggleSubscription,
                                onPostComment = { vid, txt -> viewModel.postComment(vid, txt) },
                                onOpenUploadShort = {
                                    uploadShortDefault = true
                                    currentTab = YouTvTab.UPLOAD
                                }
                            )

                            YouTvTab.UPLOAD -> UploadScreen(
                                userProfile = userProfile,
                                myChannels = myChannels,
                                platformSettings = platformSettings,
                                defaultShortMode = uploadShortDefault,
                                onCreateChannelQuick = { name, handle, desc ->
                                    viewModel.createChannel(name, handle, desc, null, null) {}
                                },
                                onPublishVideo = { ch, title, desc, url, thumb, cat, tags, lang, vis, isShort, age, dur, size, cb ->
                                    viewModel.publishVideo(ch, title, desc, url, thumb, cat, tags, lang, vis, isShort, age, dur, size) { res ->
                                        res.onSuccess { (vid, transcode) ->
                                            cb(true, transcode.message)
                                            if (!isShort && vis == "PUBLIC") {
                                                activeWatchVideo = vid
                                                viewModel.openVideo(vid)
                                            }
                                        }.onFailure { err ->
                                            cb(false, err.localizedMessage ?: "Upload failed")
                                        }
                                    }
                                }
                            )

                            YouTvTab.SUBSCRIPTIONS -> SubscriptionsScreen(
                                subscriptions = subscriptions,
                                channels = allChannels,
                                allVideos = allPublicVideos,
                                onOpenChannel = { ch -> activeChannel = ch },
                                onOpenVideo = { vid ->
                                    activeWatchVideo = vid
                                    viewModel.openVideo(vid)
                                }
                            )

                            YouTvTab.PROFILE -> ProfileAndLibraryScreen(
                                userProfile = userProfile,
                                myChannels = myChannels,
                                watchHistory = watchHistory,
                                playlists = playlists,
                                notifications = notifications,
                                blockedUsers = blockedUsers,
                                onLanguageSelected = { lang ->
                                    onLanguageSelected(lang)
                                    viewModel.syncLanguagePreference(lang)
                                },
                                onRestrictedModeChanged = viewModel::setRestrictedMode,
                                onOpenStudio = { showStudio = true },
                                onOpenChannel = { ch -> activeChannel = ch },
                                onCreateChannel = { name, handle, desc ->
                                    viewModel.createChannel(name, handle, desc, null, null) {}
                                },
                                onCreatePlaylist = { t, d -> viewModel.createPlaylist(t, d) },
                                onMarkNotificationRead = viewModel::markNotificationRead,
                                onUnblockUser = viewModel::unblockCreator,
                                onSubmitReport = { type, targetId, reason, details, copyUrl ->
                                    viewModel.submitReport(type, targetId, null, reason, details, copyUrl)
                                },
                                onRequestDeletion = viewModel::requestAccountDeletion,
                                onSignOut = {
                                    signOutUser(credentialManager, scope)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
