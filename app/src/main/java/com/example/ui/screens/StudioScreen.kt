package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Channel
import com.example.data.model.Video
import com.example.data.model.VideoProcessingJob
import com.example.localization.LocalStrings
import com.example.ui.components.BadgePill
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.formatCount

@Composable
fun StudioScreen(
    myChannels: List<Channel>,
    myVideos: List<Video>,
    processingJobs: List<VideoProcessingJob>,
    onBack: () -> Unit,
    onEditVideo: (String, String, String, String, String) -> Unit,
    onDeleteVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var editingVideo by remember { mutableStateOf<Video?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editVisibility by remember { mutableStateOf("PUBLIC") }

    BackHandler { onBack() }

    val totalViews = myVideos.sumOf { it.viewCount }
    val totalSubscribers = myChannels.sumOf { it.subscriberCount }
    val totalLikes = myVideos.sumOf { it.likeCount }
    val totalComments = myVideos.sumOf { it.commentCount }
    val watchTimeHours = (myVideos.sumOf { it.viewCount * (it.durationSec.coerceAtLeast(30L) / 2) } / 3600.0)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(strings.creatorStudio, style = MaterialTheme.typography.headlineMedium)
            }
        }

        // KPI Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioMetricCard(strings.totalViews, formatCount(totalViews), Modifier.weight(1f))
                StudioMetricCard(strings.subscribers, formatCount(totalSubscribers), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioMetricCard(strings.watchTimeHours, String.format("%.1f", watchTimeHours), Modifier.weight(1f))
                StudioMetricCard("${strings.likesLabel} / ${strings.commentsLabel}", "${formatCount(totalLikes)} / ${formatCount(totalComments)}", Modifier.weight(1f))
            }
        }

        // Performance Chart
        if (myVideos.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Audience & Video Performance", style = MaterialTheme.typography.titleMedium)
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val topVideos = myVideos.take(6)
                        val maxVal = (topVideos.maxOfOrNull { it.viewCount + it.likeCount } ?: 1L).coerceAtLeast(1L).toFloat()
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            val barWidth = (size.width / topVideos.size.coerceAtLeast(1)) * 0.55f
                            val step = size.width / topVideos.size.coerceAtLeast(1)
                            topVideos.forEachIndexed { idx, vid ->
                                val ratio = ((vid.viewCount + vid.likeCount).toFloat() / maxVal).coerceIn(0.08f, 1f)
                                val barH = size.height * ratio
                                drawRoundRect(
                                    color = primaryColor,
                                    topLeft = Offset(idx * step + (step - barWidth) / 2f, size.height - barH),
                                    size = Size(barWidth, barH),
                                    cornerRadius = CornerRadius(10f, 10f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (processingJobs.isNotEmpty()) {
            item {
                Text(strings.uploadStatus, style = MaterialTheme.typography.titleLarge)
            }
            items(processingJobs.take(5), key = { it.id }) { job ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Video: ${job.videoId}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = job.errorMessage?.takeIf { it.isNotBlank() } ?: "Progress: ${job.progressPercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BadgePill(text = job.status)
                    }
                }
            }
        }

        item {
            Text("Manage Uploads (${myVideos.size})", style = MaterialTheme.typography.titleLarge)
        }

        if (myVideos.isEmpty()) {
            item { EmptyStateBox(message = strings.emptyVideos) }
        } else {
            items(myVideos, key = { it.id }) { video ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(video.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${video.visibility} • ${video.moderationStatus} • ${formatCount(video.viewCount)} ${strings.viewsLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                editingVideo = video
                                editTitle = video.title
                                editDescription = video.description
                                editVisibility = video.visibility
                            },
                            modifier = Modifier.testTag("studio_edit_btn_${video.id}")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = strings.edit)
                        }
                        IconButton(
                            onClick = { onDeleteVideo(video) },
                            modifier = Modifier.testTag("studio_delete_btn_${video.id}")
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (editingVideo != null) {
        val target = editingVideo!!
        AlertDialog(
            onDismissRequest = { editingVideo = null },
            title = { Text(strings.edit) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text(strings.videoTitleLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text(strings.descriptionLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("PUBLIC", "UNLISTED", "PRIVATE").forEach { vis ->
                            FilterChip(
                                selected = editVisibility == vis,
                                onClick = { editVisibility = vis },
                                label = { Text(vis) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditVideo(target.id, editTitle, editDescription, target.category, editVisibility)
                        editingVideo = null
                    }
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingVideo = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun StudioMetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}
