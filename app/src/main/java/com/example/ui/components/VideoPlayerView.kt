package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.localization.LocalStrings
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun YouTvVideoPlayer(
    streamUrl: String,
    hlsUrl: String?,
    captionsEnabledDefault: Boolean = false,
    captionLabelText: String = "",
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onNextVideo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val effectiveUrl = hlsUrl?.takeIf { it.isNotBlank() } ?: streamUrl

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var selectedQuality by remember { mutableStateOf("Auto") }
    var captionsOn by remember { mutableStateOf(captionsEnabledDefault) }
    var playerError by remember { mutableStateOf<String?>(null) }

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(effectiveUrl) {
        playerError = null
        val uri = Uri.parse(effectiveUrl)
        val itemBuilder = MediaItem.Builder().setUri(uri)
        if (effectiveUrl.contains(".m3u8", ignoreCase = true)) {
            itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        exoPlayer.setMediaItem(itemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error.localizedMessage ?: "Playback unavailable for this media URI."
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            delay(500L)
        }
    }

    val containerModifier = if (isFullscreen) {
        modifier.fillMaxSize().background(Color.Black)
    } else {
        modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
    }

    Box(
        modifier = containerModifier.clickable { showControls = !showControls }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view -> view.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        if (captionsOn && captionLabelText.isNotBlank()) {
            Text(
                text = captionLabelText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showControls) 82.dp else 18.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = playerError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Top right controls: Captions, Speed, Quality
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            captionsOn = !captionsOn
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsOn)
                                .build()
                        },
                        modifier = Modifier.testTag("player_captions_btn")
                    ) {
                        Icon(
                            imageVector = if (captionsOn) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                            contentDescription = strings.captions,
                            tint = Color.White
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showSpeedMenu = true },
                            modifier = Modifier.testTag("player_speed_btn")
                        ) {
                            Icon(Icons.Filled.Speed, contentDescription = strings.speed, tint = Color.White)
                        }
                        DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                            listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                DropdownMenuItem(
                                    text = { Text("${spd}x" + if (spd == playbackSpeed) " ✓" else "") },
                                    onClick = {
                                        playbackSpeed = spd
                                        exoPlayer.setPlaybackSpeed(spd)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showQualityMenu = true },
                            modifier = Modifier.testTag("player_quality_btn")
                        ) {
                            Icon(Icons.Filled.HighQuality, contentDescription = strings.quality, tint = Color.White)
                        }
                        DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                            listOf("Auto" to Int.MAX_VALUE, "1080p" to 1080, "720p" to 720, "480p" to 480, "360p" to 360).forEach { (label, maxH) ->
                                DropdownMenuItem(
                                    text = { Text(label + if (label == selectedQuality) " ✓" else "") },
                                    onClick = {
                                        selectedQuality = label
                                        val paramsBuilder: TrackSelectionParameters.Builder = exoPlayer.trackSelectionParameters.buildUpon()
                                        if (maxH == Int.MAX_VALUE) {
                                            paramsBuilder.clearVideoSizeConstraints()
                                        } else {
                                            paramsBuilder.setMaxVideoSize(Int.MAX_VALUE, maxH)
                                        }
                                        exoPlayer.trackSelectionParameters = paramsBuilder.build()
                                        showQualityMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Center Play/Pause + Next
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .testTag("player_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play or pause",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    if (onNextVideo != null) {
                        IconButton(
                            onClick = onNextVideo,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .testTag("player_next_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = strings.nextVideo,
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom seek & volume bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Slider(
                        value = if (durationMs > 0L) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { frac ->
                            if (durationMs > 0L) {
                                exoPlayer.seekTo((frac * durationMs).toLong())
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatDuration(currentPosMs / 1000)} / ${formatDuration(durationMs / 1000)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else volume
                            },
                            modifier = Modifier.testTag("player_mute_btn")
                        ) {
                            Icon(
                                imageVector = if (isMuted || volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Mute toggle",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.testTag("player_fullscreen_btn")
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (isFullscreen) strings.exitFullscreen else strings.fullscreen,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
