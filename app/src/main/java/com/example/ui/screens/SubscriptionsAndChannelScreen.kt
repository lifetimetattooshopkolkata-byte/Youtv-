package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.Channel
import com.example.data.model.Subscription
import com.example.data.model.Video
import com.example.localization.LocalStrings
import com.example.ui.components.ChannelAvatar
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.VideoFeedCard
import com.example.ui.components.formatCount

@Composable
fun SubscriptionsScreen(
    subscriptions: List<Subscription>,
    channels: List<Channel>,
    allVideos: List<Video>,
    onOpenChannel: (Channel) -> Unit,
    onOpenVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val subIds = subscriptions.map { it.channelId }.toSet()
    val subVideos = allVideos.filter { it.channelId in subIds }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.navSubscriptions, style = MaterialTheme.typography.headlineMedium)
        }

        if (subscriptions.isEmpty()) {
            item {
                EmptyStateBox(message = strings.emptySubscriptions)
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(subscriptions, key = { it.id }) { sub ->
                        val ch = channels.firstOrNull { it.id == sub.channelId }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(84.dp)
                                .clickable { if (ch != null) onOpenChannel(ch) }
                        ) {
                            ChannelAvatar(name = sub.channelName, avatarUrl = sub.channelAvatarUrl, sizeDp = 56)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = sub.channelName,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (subVideos.isEmpty()) {
                item { EmptyStateBox(message = strings.emptyVideos) }
            } else {
                items(subVideos, key = { it.id }) { video ->
                    VideoFeedCard(video = video, onClick = { onOpenVideo(video) })
                }
            }
        }
    }
}

@Composable
fun ChannelScreen(
    channel: Channel,
    currentUserId: String,
    isSubscribed: Boolean,
    channelVideos: List<Video>,
    onBack: () -> Unit,
    onToggleSubscribe: (Channel) -> Unit,
    onOpenVideo: (Video) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val longVideos = channelVideos.filter { !it.shortForm }
    val shorts = channelVideos.filter { it.shortForm }

    BackHandler { onBack() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF42080C), Color(0xFF1A1A22))))
            ) {
                if (!channel.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.coverUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelAvatar(name = channel.name, avatarUrl = channel.avatarUrl, sizeDp = 64)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(channel.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "${channel.handle} • ${formatCount(channel.subscriberCount)} ${strings.subscribers} • ${channelVideos.size} videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (channel.ownerId != currentUserId) {
                    Button(
                        onClick = { onToggleSubscribe(channel) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("channel_subscribe_btn")
                    ) {
                        Text(if (isSubscribed) strings.subscribed else strings.subscribe)
                    }
                }
            }
        }

        item {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Videos (${longVideos.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("${strings.navShorts} (${shorts.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("About") })
            }
        }

        when (selectedTab) {
            0 -> {
                if (longVideos.isEmpty()) {
                    item { EmptyStateBox(message = strings.emptyVideos) }
                } else {
                    items(longVideos, key = { it.id }) { vid ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            VideoFeedCard(video = vid, onClick = { onOpenVideo(vid) })
                        }
                    }
                }
            }
            1 -> {
                if (shorts.isEmpty()) {
                    item { EmptyStateBox(message = strings.emptyShorts) }
                } else {
                    items(shorts, key = { it.id }) { vid ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            VideoFeedCard(video = vid, onClick = { onOpenVideo(vid) })
                        }
                    }
                }
            }
            else -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(channel.description.ifBlank { "Welcome to ${channel.name} on YouTV." })
                            Text("${strings.totalViews}: ${formatCount(channel.totalViews)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
