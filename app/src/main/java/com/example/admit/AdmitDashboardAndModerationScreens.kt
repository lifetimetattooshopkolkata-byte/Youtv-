package com.example.admit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserProfile
import com.example.data.model.Video
import com.example.localization.LocalStrings
import com.example.ui.theme.AdmitAccentAmber
import com.example.ui.theme.AdmitAccentCrimson
import com.example.ui.theme.AdmitAccentEmerald
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdmitDashboardScreen(
    viewModel: AdmitViewModel,
    backendStatusMessage: String
) {
    val strings = LocalStrings.current
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val settings by viewModel.platformSettings.collectAsStateWithLifecycle()
    val timeframe by viewModel.timeframe.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val jobs by viewModel.processingJobs.collectAsStateWithLifecycle()

    val quotaMb = settings.totalStorageQuotaMb.coerceAtLeast(100L).toFloat()
    val storageFraction = (metrics.storageUsedMb.toFloat() / quotaMb).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.admitDashboard,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = backendStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 3
            ) {
                KpiMetricCard(strings.totalUsers, metrics.totalUsers.toString(), Modifier.weight(1f))
                KpiMetricCard(strings.activeUsers, metrics.activeUsers.toString(), Modifier.weight(1f), AdmitAccentEmerald)
                KpiMetricCard(strings.suspendedUsers, metrics.suspendedUsers.toString(), Modifier.weight(1f), AdmitAccentCrimson)
                KpiMetricCard(strings.totalChannels, metrics.totalChannels.toString(), Modifier.weight(1f))
                KpiMetricCard(strings.totalVideos, "${metrics.totalVideos} (${metrics.publicVideos} pub)", Modifier.weight(1f))
                KpiMetricCard(strings.totalViews, metrics.totalViews.toString(), Modifier.weight(1f), AdmitAccentAmber)
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.storageUsage, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = String.format(Locale.US, "%.1f MB / %d MB", metrics.storageUsedMb, settings.totalStorageQuotaMb),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { storageFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = if (storageFraction > 0.85f) AdmitAccentCrimson else AdmitAccentEmerald
                    )
                    Text(
                        text = "Open Reports: ${metrics.openReports} • Processing Jobs: ${jobs.size} • Total Subscribers: ${metrics.totalSubscribers}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Platform Activity",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChartTimeframe.entries.forEach { tf ->
                                FilterChip(
                                    selected = timeframe == tf,
                                    onClick = { viewModel.setTimeframe(tf) },
                                    label = { Text(tf.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    modifier = Modifier.testTag("chart_tf_${tf.name.lowercase()}")
                                )
                            }
                        }
                    }

                    val bars = remember(timeframe, users.size, videos.size, metrics.totalViews) {
                        val base = (videos.size + users.size).coerceAtLeast(1).toFloat()
                        when (timeframe) {
                            ChartTimeframe.DAILY -> listOf(base * 0.4f, base * 0.6f, base * 0.8f, base * 0.5f, base * 0.9f, base * 1.1f, base)
                            ChartTimeframe.WEEKLY -> listOf(base * 1.2f, base * 1.5f, base * 1.1f, base * 1.8f, base * 2.1f, base * 1.9f, base * 2.4f)
                            ChartTimeframe.MONTHLY -> listOf(base * 2f, base * 2.8f, base * 3.1f, base * 2.9f, base * 3.5f, base * 4.0f)
                        }
                    }
                    ActivityBarChart(values = bars, barColor = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (auditLogs.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Owner Actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            items(auditLogs.take(5), key = { it.id }) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${log.action} • ${log.targetType}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(log.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier.width(148.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
        }
    }
}

@Composable
private fun ActivityBarChart(values: List<Float>, barColor: Color) {
    val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val count = values.size.coerceAtLeast(1)
        val slotWidth = size.width / count
        val barWidth = slotWidth * 0.55f
        values.forEachIndexed { index, v ->
            val ratio = (v / maxVal).coerceIn(0.08f, 1f)
            val barHeight = size.height * ratio
            val x = index * slotWidth + (slotWidth - barWidth) / 2f
            val y = size.height - barHeight
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(12f, 12f)
            )
        }
    }
}

@Composable
fun AdmitUsersScreen(viewModel: AdmitViewModel) {
    val strings = LocalStrings.current
    val users by viewModel.users.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<Pair<UserProfile, String>?>(null) }
    var reasonText by remember { mutableStateOf("") }

    val filtered = remember(users, query) {
        if (query.isBlank()) users else users.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.email.contains(query, ignoreCase = true) ||
                it.userId.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search users by name, email or UID") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("admit_user_search_input"),
            shape = RoundedCornerShape(14.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.userId }) { user ->
                val userChannels = channels.count { it.ownerId == user.userId }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.displayName.ifBlank { "Unnamed User" }, style = MaterialTheme.typography.titleMedium)
                                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "Status: ${user.status.uppercase()} • Channels: $userChannels • Uploads: ${if (user.uploadDisabled) "Disabled" else "Allowed"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (user.isRestrictedAccount) AdmitAccentCrimson else AdmitAccentEmerald
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    reasonText = ""
                                    pendingAction = user to if (user.status == "suspended") "RESTORE_USER" else "SUSPEND_USER"
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text(if (user.status == "suspended") strings.restoreUser else strings.suspendUser, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    reasonText = ""
                                    pendingAction = user to if (user.status == "blocked") "UNBLOCK_USER" else "BLOCK_USER"
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text(if (user.status == "blocked") strings.unblockAccount else strings.blockAccount, maxLines = 1)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    reasonText = ""
                                    pendingAction = user to if (user.uploadDisabled) "ENABLE_UPLOADS" else "DISABLE_UPLOADS"
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text(if (user.uploadDisabled) strings.enableUploads else strings.disableUploads, maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    reasonText = ""
                                    pendingAction = user to "DELETE_USER"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AdmitAccentCrimson),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.delete, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    pendingAction?.let { (target, action) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("$action • ${target.displayName}") },
            text = {
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text(strings.moderationReasonRequired) },
                    modifier = Modifier.fillMaxWidth().testTag("admit_user_reason_input")
                )
            },
            confirmButton = {
                Button(
                    enabled = reasonText.trim().length >= 2,
                    onClick = {
                        if (action == "DELETE_USER") {
                            viewModel.deleteUser(target, reasonText)
                        } else {
                            viewModel.moderateUser(target, action, reasonText)
                        }
                        pendingAction = null
                    },
                    modifier = Modifier.testTag("admit_user_confirm_button")
                ) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun AdmitVideosScreen(viewModel: AdmitViewModel) {
    val strings = LocalStrings.current
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var pendingVideoAction by remember { mutableStateOf<Triple<Video, String, String?>?>(null) }
    var reasonText by remember { mutableStateOf("") }

    val filtered = remember(videos, query) {
        if (query.isBlank()) videos else videos.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.channelName.contains(query, ignoreCase = true) ||
                it.ownerId.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search videos by title, channel or owner UID") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("admit_video_search_input"),
            shape = RoundedCornerShape(14.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { video ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(video.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${video.channelName} • ${video.category} • ${video.visibility} • ${video.processingStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Moderation: ${video.moderationStatus} • Views: ${video.viewCount} • Likes: ${video.likeCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (video.moderationStatus == "BLOCKED") AdmitAccentCrimson else AdmitAccentEmerald
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    reasonText = ""
                                    val action = if (video.moderationStatus == "BLOCKED") "RESTORE_VIDEO" else "BLOCK_VIDEO"
                                    pendingVideoAction = Triple(video, action, null)
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (video.moderationStatus == "BLOCKED") Icons.Filled.CheckCircle else Icons.Filled.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (video.moderationStatus == "BLOCKED") strings.restoreVideo else strings.blockVideo, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    reasonText = ""
                                    val nextVis = if (video.visibility == "PUBLIC") "PRIVATE" else "PUBLIC"
                                    pendingVideoAction = Triple(video, "CHANGE_VISIBILITY", nextVis)
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (video.visibility == "PUBLIC") "Make Private" else "Make Public", maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    reasonText = ""
                                    pendingVideoAction = Triple(video, "DELETE_VIDEO", null)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AdmitAccentCrimson),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Filled.DeleteForever, contentDescription = strings.deleteVideo)
                            }
                        }
                    }
                }
            }
        }
    }

    pendingVideoAction?.let { (video, action, nextVis) ->
        AlertDialog(
            onDismissRequest = { pendingVideoAction = null },
            title = { Text("$action • ${video.title}") },
            text = {
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text(strings.moderationReasonRequired) },
                    modifier = Modifier.fillMaxWidth().testTag("admit_video_reason_input")
                )
            },
            confirmButton = {
                Button(
                    enabled = reasonText.trim().length >= 2,
                    onClick = {
                        viewModel.moderateVideo(video, action, nextVis, reasonText)
                        pendingVideoAction = null
                    },
                    modifier = Modifier.testTag("admit_video_confirm_button")
                ) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { pendingVideoAction = null }) { Text(strings.cancel) }
            }
        )
    }
}
