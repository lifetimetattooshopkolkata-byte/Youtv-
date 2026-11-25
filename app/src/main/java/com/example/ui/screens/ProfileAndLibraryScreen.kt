package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.distribution.DistributionManager
import com.example.data.distribution.DistributionOverview
import com.example.data.model.BlockedUser
import com.example.data.model.Channel
import com.example.data.model.NotificationItem
import com.example.data.model.Playlist
import com.example.data.model.UserProfile
import com.example.data.model.WatchHistoryEntry
import com.example.localization.AppLanguage
import com.example.localization.LocalLanguage
import com.example.localization.LocalStrings
import com.example.ui.components.BadgePill
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.LanguageSelectorRow
import kotlinx.coroutines.launch

@Composable
fun ProfileAndLibraryScreen(
    userProfile: UserProfile?,
    myChannels: List<Channel>,
    watchHistory: List<WatchHistoryEntry>,
    playlists: List<Playlist>,
    notifications: List<NotificationItem>,
    blockedUsers: List<BlockedUser>,
    onLanguageSelected: (AppLanguage) -> Unit,
    onRestrictedModeChanged: (Boolean) -> Unit,
    onOpenStudio: () -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onCreateChannel: (String, String, String) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onUnblockUser: (String) -> Unit,
    onSubmitReport: (String, String, String, String, String?) -> Unit,
    onRequestDeletion: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val currentLanguage = LocalLanguage.current

    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var channelName by remember { mutableStateOf("") }
    var channelHandle by remember { mutableStateOf("") }
    var channelDesc by remember { mutableStateOf("") }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistTitle by remember { mutableStateOf("") }
    var playlistDesc by remember { mutableStateOf("") }

    var legalDialogTitle by remember { mutableStateOf<String?>(null) }
    var legalDialogBody by remember { mutableStateOf("") }

    var showComplaintDialog by remember { mutableStateOf<String?>(null) } // "COPYRIGHT" or "APPEAL"
    var complaintTargetId by remember { mutableStateOf("") }
    var complaintReason by remember { mutableStateOf("") }
    var complaintDetails by remember { mutableStateOf("") }
    var complaintWorkUrl by remember { mutableStateOf("") }

    var showDeletionDialog by remember { mutableStateOf(false) }
    var deletionReason by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val distributionManager = remember(context) { DistributionManager(context) }
    var distOverview by remember { mutableStateOf(DistributionOverview()) }
    var apkStatusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        distOverview = distributionManager.fetchDistributionOverview()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Profile Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChannelAvatar(
                        name = userProfile?.displayName ?: "YouTV",
                        avatarUrl = userProfile?.photoUrl,
                        sizeDp = 56
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile?.displayName ?: "YouTV Member",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = userProfile?.email.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BadgePill(text = userProfile?.status?.uppercase() ?: "ACTIVE")
                            if (userProfile?.emailVerified == true) {
                                BadgePill(text = "VERIFIED", container = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    IconButton(onClick = onSignOut, modifier = Modifier.testTag("profile_sign_out_btn")) {
                        Icon(Icons.Filled.Logout, contentDescription = strings.signOut)
                    }
                }
            }
        }

        // Language & Safety Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium)
                    LanguageSelectorRow(
                        selectedLanguage = currentLanguage,
                        onLanguageSelected = onLanguageSelected
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(strings.restrictedMode, modifier = Modifier.weight(1f))
                        Switch(
                            checked = userProfile?.restrictedMode == true,
                            onCheckedChange = onRestrictedModeChanged
                        )
                    }
                }
            }
        }

        // Creator Studio & Channels
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenStudio,
                    modifier = Modifier.weight(1f).testTag("open_creator_studio_btn")
                ) {
                    Icon(Icons.Filled.Analytics, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.creatorStudio)
                }
                OutlinedButton(
                    onClick = { showCreateChannelDialog = true },
                    modifier = Modifier.weight(1f).testTag("open_create_channel_btn")
                ) {
                    Icon(Icons.Filled.AddCircleOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.createChannel)
                }
            }
        }

        if (myChannels.isNotEmpty()) {
            item { Text(strings.channels, style = MaterialTheme.typography.titleLarge) }
            items(myChannels, key = { it.id }) { ch ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenChannel(ch) }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChannelAvatar(name = ch.name, avatarUrl = ch.avatarUrl, sizeDp = 42)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ch.name, style = MaterialTheme.typography.titleMedium)
                            Text("${ch.handle} • ${ch.subscriberCount} ${strings.subscribers}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Watch History
        item { Text("${strings.watchHistory} (${watchHistory.size})", style = MaterialTheme.typography.titleLarge) }
        items(watchHistory.take(5), key = { it.id }) { hist ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(hist.videoTitle, style = MaterialTheme.typography.titleSmall)
                    Text(hist.channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Playlists & Watch Later
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${strings.playlists} (${playlists.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCreatePlaylistDialog = true }) {
                    Text(strings.createPlaylist)
                }
            }
        }
        items(playlists, key = { it.id }) { pl ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pl.title, style = MaterialTheme.typography.titleSmall)
                        Text("${pl.videoIds.size} videos • ${pl.playlistType}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Notifications
        if (notifications.isNotEmpty()) {
            item { Text(strings.notifications, style = MaterialTheme.typography.titleLarge) }
            items(notifications.take(5), key = { it.id }) { ntf ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (ntf.readFlag) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onMarkNotificationRead(ntf.id) }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(ntf.title, style = MaterialTheme.typography.titleSmall)
                            Text(ntf.body, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Blocked Users
        if (blockedUsers.isNotEmpty()) {
            item { Text(strings.blockUser, style = MaterialTheme.typography.titleLarge) }
            items(blockedUsers, key = { it.id }) { blk ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Block, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(blk.blockedUserName, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onUnblockUser(blk.id) }) { Text(strings.unblockAccount) }
                    }
                }
            }
        }

        // Modular Live Streaming & Monetization Architecture Cards (Honest Coming Soon / Disabled state)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.comingSoonModules, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(strings.liveStreamingDisabled, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(strings.monetizationDisabled, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Direct APK Distribution (GCS Signed URLs / Firebase Hosting)
        item {
            val readyRelease = distOverview.releases.firstOrNull { it.status == "READY" }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("YouTV Android APK Distribution", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = readyRelease?.let {
                            "Latest Ready Build: v${it.versionName} (${DistributionManager.formatFileSizeMb(it.fileSizeBytes)})"
                        } ?: distOverview.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    apkStatusMsg?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (readyRelease != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val res = distributionManager.requestDirectDownloadUrl(readyRelease.releaseId)
                                    if (res.success && res.downloadUrl != null) {
                                        distributionManager.openDownloadInBrowser(res.downloadUrl)
                                    }
                                    apkStatusMsg = res.message
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("profile_download_apk_btn")
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Download Signed APK")
                        }
                    }
                }
            }
        }

        // Legal, Copyright, Appeals, Account Deletion
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(strings.legalSafety, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            legalDialogTitle = strings.termsOfService
                            legalDialogBody = "YouTV Terms of Service: Creators retain ownership of original content and grant YouTV a license to host and stream it. Impersonation, spam, illegal uploads, and privilege abuse result in suspension."
                        }) { Text(strings.termsOfService) }
                        TextButton(onClick = {
                            legalDialogTitle = strings.privacyPolicy
                            legalDialogBody = "YouTV Privacy Policy: Verified Google account info is stored privately in Firestore with zero-trust access rules. You may request full account deletion at any time."
                        }) { Text(strings.privacyPolicy) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            legalDialogTitle = strings.communityGuidelines
                            legalDialogBody = "YouTV Community Guidelines: Respect creators and viewers, label 18+ content accurately, and refrain from harassment, hate speech, or copyright infringement."
                        }) { Text(strings.communityGuidelines) }
                        TextButton(onClick = { showComplaintDialog = "COPYRIGHT" }) { Text(strings.copyrightComplaint) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showComplaintDialog = "APPEAL" }) { Text(strings.appealProcess) }
                        OutlinedButton(
                            onClick = { showDeletionDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(strings.requestAccountDeletion)
                        }
                    }
                }
            }
        }
    }

    if (showCreateChannelDialog) {
        AlertDialog(
            onDismissRequest = { showCreateChannelDialog = false },
            title = { Text(strings.createChannel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = channelName, onValueChange = { channelName = it }, label = { Text(strings.channelNameLabel) })
                    OutlinedTextField(value = channelHandle, onValueChange = { channelHandle = it }, label = { Text(strings.channelHandleLabel) })
                    OutlinedTextField(value = channelDesc, onValueChange = { channelDesc = it }, label = { Text(strings.descriptionLabel) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (channelName.isNotBlank() && channelHandle.isNotBlank()) {
                        onCreateChannel(channelName, channelHandle, channelDesc)
                        channelName = ""; channelHandle = ""; channelDesc = ""
                        showCreateChannelDialog = false
                    }
                }) { Text(strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { showCreateChannelDialog = false }) { Text(strings.cancel) } }
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(strings.createPlaylist) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = playlistTitle, onValueChange = { playlistTitle = it }, label = { Text("Playlist Title") })
                    OutlinedTextField(value = playlistDesc, onValueChange = { playlistDesc = it }, label = { Text(strings.descriptionLabel) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (playlistTitle.isNotBlank()) {
                        onCreatePlaylist(playlistTitle, playlistDesc)
                        playlistTitle = ""; playlistDesc = ""
                        showCreatePlaylistDialog = false
                    }
                }) { Text(strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text(strings.cancel) } }
        )
    }

    if (legalDialogTitle != null) {
        AlertDialog(
            onDismissRequest = { legalDialogTitle = null },
            title = { Text(legalDialogTitle!!) },
            text = { Text(legalDialogBody) },
            confirmButton = { TextButton(onClick = { legalDialogTitle = null }) { Text(strings.confirm) } }
        )
    }

    if (showComplaintDialog != null) {
        val type = showComplaintDialog!!
        AlertDialog(
            onDismissRequest = { showComplaintDialog = null },
            title = { Text(if (type == "COPYRIGHT") strings.copyrightComplaint else strings.appealProcess) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = complaintTargetId,
                        onValueChange = { complaintTargetId = it },
                        label = { Text("Video / Account ID") }
                    )
                    OutlinedTextField(
                        value = complaintReason,
                        onValueChange = { complaintReason = it },
                        label = { Text(strings.reasonLabel) }
                    )
                    OutlinedTextField(
                        value = complaintDetails,
                        onValueChange = { complaintDetails = it },
                        label = { Text(strings.detailsLabel) }
                    )
                    if (type == "COPYRIGHT") {
                        OutlinedTextField(
                            value = complaintWorkUrl,
                            onValueChange = { complaintWorkUrl = it },
                            label = { Text("Original Work URL") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSubmitReport(
                        type,
                        complaintTargetId.ifBlank { userProfile?.userId ?: "general" },
                        complaintReason.ifBlank { type },
                        complaintDetails,
                        complaintWorkUrl.takeIf { it.isNotBlank() }
                    )
                    showComplaintDialog = null
                }) { Text(strings.submit) }
            },
            dismissButton = { TextButton(onClick = { showComplaintDialog = null }) { Text(strings.cancel) } }
        )
    }

    if (showDeletionDialog) {
        AlertDialog(
            onDismissRequest = { showDeletionDialog = false },
            title = { Text(strings.requestAccountDeletion) },
            text = {
                OutlinedTextField(
                    value = deletionReason,
                    onValueChange = { deletionReason = it },
                    label = { Text(strings.reasonLabel) }
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onRequestDeletion(deletionReason)
                        showDeletionDialog = false
                    }
                ) { Text(strings.confirm) }
            },
            dismissButton = { TextButton(onClick = { showDeletionDialog = false }) { Text(strings.cancel) } }
        )
    }
}
