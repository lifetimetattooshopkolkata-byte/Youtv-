package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Channel
import com.example.data.model.Comment
import com.example.data.model.Video
import com.example.localization.LocalStrings
import com.example.ui.components.BadgePill
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.VideoFeedCard
import com.example.ui.components.YouTvVideoPlayer
import com.example.ui.components.formatCount

@Composable
fun WatchScreen(
    video: Video,
    currentUserId: String,
    channel: Channel?,
    isSubscribed: Boolean,
    myReaction: String?,
    comments: List<Comment>,
    upNextVideos: List<Video>,
    onBack: () -> Unit,
    onOpenVideo: (Video) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onToggleSubscribe: (Channel) -> Unit,
    onSaveToWatchLater: (String) -> Unit,
    onPostComment: (Video, String, String?) -> Unit,
    onHideComment: (String) -> Unit,
    onDeleteComment: (Comment) -> Unit,
    onReportVideo: (String, String, String?) -> Unit,
    onBlockCreator: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    var replyTargetComment by remember { mutableStateOf<Comment?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("Inappropriate content") }
    var reportDetails by remember { mutableStateOf("") }
    var copyrightUrl by remember { mutableStateOf("") }
    var reportType by remember { mutableStateOf("VIDEO") }

    BackHandler {
        if (isFullscreen) isFullscreen = false else onBack()
    }

    if (isFullscreen) {
        YouTvVideoPlayer(
            streamUrl = video.playbackUrl,
            hlsUrl = video.hlsManifestUrl,
            captionLabelText = video.title,
            isFullscreen = true,
            onToggleFullscreen = { isFullscreen = false },
            onNextVideo = upNextVideos.firstOrNull()?.let { next -> { onOpenVideo(next) } },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top mini bar with back
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("watch_back_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        YouTvVideoPlayer(
            streamUrl = video.playbackUrl,
            hlsUrl = video.hlsManifestUrl,
            captionLabelText = "${video.title} (${video.language.uppercase()})",
            isFullscreen = false,
            onToggleFullscreen = { isFullscreen = true },
            onNextVideo = upNextVideos.firstOrNull()?.let { next -> { onOpenVideo(next) } }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatCount(video.viewCount)} ${strings.viewsLabel} • ${video.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BadgePill(text = video.processingStatus)
                }
                if (video.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = video.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action chips row
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        AssistChip(
                            onClick = { onToggleReaction(video.id, "LIKE") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.ThumbUp,
                                    contentDescription = strings.likesLabel,
                                    tint = if (myReaction == "LIKE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            label = { Text("${strings.likesLabel} (${video.likeCount})") },
                            modifier = Modifier.testTag("watch_like_btn")
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onToggleReaction(video.id, "DISLIKE") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.ThumbDown,
                                    contentDescription = strings.dislikesLabel,
                                    tint = if (myReaction == "DISLIKE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            label = { Text("${strings.dislikesLabel} (${video.dislikeCount})") },
                            modifier = Modifier.testTag("watch_dislike_btn")
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onSaveToWatchLater(video.id) },
                            leadingIcon = { Icon(Icons.Filled.BookmarkAdd, contentDescription = strings.watchLater) },
                            label = { Text(strings.watchLater) },
                            modifier = Modifier.testTag("watch_save_btn")
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Watch '${video.title}' on YouTV: ${video.playbackUrl}")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, strings.share))
                            },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = strings.share) },
                            label = { Text(strings.share) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                reportType = "VIDEO"
                                showReportDialog = true
                            },
                            leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = strings.reportVideo) },
                            label = { Text(strings.reportVideo) },
                            modifier = Modifier.testTag("watch_report_btn")
                        )
                    }
                    if (video.ownerId != currentUserId) {
                        item {
                            AssistChip(
                                onClick = { onBlockCreator(video.ownerId, video.channelName) },
                                leadingIcon = { Icon(Icons.Filled.Block, contentDescription = strings.blockUser) },
                                label = { Text(strings.blockUser) }
                            )
                        }
                    }
                }
            }

            // Channel Row
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (channel != null) onOpenChannel(channel) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChannelAvatar(name = video.channelName, avatarUrl = video.channelAvatarUrl, sizeDp = 44)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(video.channelName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${channel?.subscriberCount ?: 0} ${strings.subscribers}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (channel != null && channel.ownerId != currentUserId) {
                            Button(
                                onClick = { onToggleSubscribe(channel) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("watch_subscribe_btn")
                            ) {
                                Text(if (isSubscribed) strings.subscribed else strings.subscribe)
                            }
                        }
                    }
                }
            }

            // Comments Input & List
            item {
                Text(
                    text = "${strings.commentsLabel} (${comments.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                if (replyTargetComment != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${strings.reply}: ${replyTargetComment!!.authorName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = { replyTargetComment = null }) {
                            Text(strings.cancel)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = commentDraft,
                        onValueChange = { commentDraft = it },
                        placeholder = {
                            Text(if (replyTargetComment != null) strings.replyHint else strings.addCommentHint)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("watch_comment_input")
                    )
                    Button(
                        onClick = {
                            if (commentDraft.isNotBlank()) {
                                onPostComment(video, commentDraft, replyTargetComment?.id)
                                commentDraft = ""
                                replyTargetComment = null
                            }
                        },
                        modifier = Modifier.testTag("watch_comment_post_btn")
                    ) {
                        Text(strings.post)
                    }
                }
            }

            val topLevelComments = comments.filter { it.parentCommentId.isNullOrBlank() }
            val repliesByParent = comments.filter { !it.parentCommentId.isNullOrBlank() }.groupBy { it.parentCommentId }

            items(topLevelComments, key = { it.id }) { comment ->
                CommentItemCard(
                    comment = comment,
                    replies = repliesByParent[comment.id].orEmpty(),
                    currentUserId = currentUserId,
                    isVideoOwner = video.ownerId == currentUserId,
                    onReply = { replyTargetComment = comment },
                    onHide = { onHideComment(comment.id) },
                    onDelete = { onDeleteComment(comment) }
                )
            }

            if (upNextVideos.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(strings.nextVideo, style = MaterialTheme.typography.titleLarge)
                }
                items(upNextVideos.take(5), key = { "next_${it.id}" }) { nextVid ->
                    VideoFeedCard(video = nextVid, onClick = { onOpenVideo(nextVid) })
                }
            }
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(strings.reportVideo) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { reportType = "VIDEO" }) {
                            Text("Policy")
                        }
                        OutlinedButton(onClick = { reportType = "COPYRIGHT" }) {
                            Text("Copyright")
                        }
                    }
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text(strings.reasonLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it },
                        label = { Text(strings.detailsLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (reportType == "COPYRIGHT") {
                        OutlinedTextField(
                            value = copyrightUrl,
                            onValueChange = { copyrightUrl = it },
                            label = { Text("Original Work URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportVideo(reportType, "$reportReason: $reportDetails", copyrightUrl.takeIf { it.isNotBlank() })
                        showReportDialog = false
                    }
                ) {
                    Text(strings.submit)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun CommentItemCard(
    comment: Comment,
    replies: List<Comment>,
    currentUserId: String,
    isVideoOwner: Boolean,
    onReply: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(name = comment.authorName, avatarUrl = comment.authorAvatarUrl, sizeDp = 28)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onReply) {
                    Text(strings.reply)
                }
                if (isVideoOwner && comment.authorId != currentUserId) {
                    IconButton(onClick = onHide) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = strings.hideComment)
                    }
                }
                if (comment.authorId == currentUserId || isVideoOwner) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = strings.delete)
                    }
                }
            }
            Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
            if (replies.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    replies.forEach { reply ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(reply.authorName, style = MaterialTheme.typography.labelLarge)
                                Text(reply.text, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
