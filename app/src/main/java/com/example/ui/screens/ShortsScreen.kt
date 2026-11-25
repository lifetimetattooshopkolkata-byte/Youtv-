package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Channel
import com.example.data.model.ShortVideo
import com.example.data.model.Video
import com.example.localization.LocalStrings
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.YouTvVideoPlayer
import com.example.ui.components.formatCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    shorts: List<ShortVideo>,
    channels: List<Channel>,
    subscribedChannelIds: Set<String>,
    likedVideoIds: Set<String>,
    onToggleLike: (String) -> Unit,
    onToggleSubscribe: (Channel) -> Unit,
    onPostComment: (Video, String) -> Unit,
    onOpenUploadShort: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var commentTargetShort by remember { mutableStateOf<ShortVideo?>(null) }
    var commentDraft by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (shorts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                EmptyStateBox(message = strings.emptyShorts)
                Button(
                    onClick = onOpenUploadShort,
                    modifier = Modifier.testTag("empty_short_upload_btn")
                ) {
                    Text(strings.uploadTitle)
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { shorts.size })
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val shortItem = shorts[page]
                val channel = channels.firstOrNull { it.id == shortItem.channelId }
                val isSubscribed = shortItem.channelId in subscribedChannelIds
                val isLiked = shortItem.id in likedVideoIds

                Box(modifier = Modifier.fillMaxSize()) {
                    YouTvVideoPlayer(
                        streamUrl = shortItem.videoUrl,
                        hlsUrl = null,
                        isFullscreen = true,
                        onToggleFullscreen = {},
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient scrim for creator overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )

                    // Right action rail
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { onToggleLike(shortItem.id) },
                                modifier = Modifier.testTag("short_like_btn_${shortItem.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ThumbUp,
                                    contentDescription = strings.likesLabel,
                                    tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Text(
                                text = formatCount(shortItem.likeCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { commentTargetShort = shortItem },
                                modifier = Modifier.testTag("short_comment_btn_${shortItem.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Comment,
                                    contentDescription = strings.commentsLabel,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = formatCount(shortItem.commentCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Watch '${shortItem.title}' on YouTV: ${shortItem.videoUrl}")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, strings.share))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = strings.share,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Bottom left creator info
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, end = 88.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelAvatar(
                                name = shortItem.channelName,
                                avatarUrl = shortItem.channelAvatarUrl,
                                sizeDp = 36
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = shortItem.channelName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            if (channel != null) {
                                Spacer(Modifier.width(10.dp))
                                Button(
                                    onClick = { onToggleSubscribe(channel) },
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(if (isSubscribed) strings.subscribed else strings.subscribe)
                                }
                            }
                        }
                        Text(
                            text = shortItem.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenUploadShort,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .testTag("shorts_upload_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = strings.uploadTitle)
        }
    }

    if (commentTargetShort != null) {
        val target = commentTargetShort!!
        ModalBottomSheet(onDismissRequest = { commentTargetShort = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.commentsLabel, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { commentDraft = it },
                    placeholder = { Text(strings.addCommentHint) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (commentDraft.isNotBlank()) {
                            onPostComment(
                                Video(
                                    id = target.id,
                                    ownerId = target.ownerId,
                                    channelId = target.channelId,
                                    channelName = target.channelName,
                                    title = target.title
                                ),
                                commentDraft
                            )
                            commentDraft = ""
                            commentTargetShort = null
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(strings.post)
                }
            }
        }
    }
}
