package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Channel
import com.example.data.model.PlatformSettings
import com.example.data.model.UserProfile
import com.example.localization.AppLanguage
import com.example.localization.LocalStrings

@Composable
fun UploadScreen(
    userProfile: UserProfile?,
    myChannels: List<Channel>,
    platformSettings: PlatformSettings,
    defaultShortMode: Boolean = false,
    onCreateChannelQuick: (String, String, String) -> Unit,
    onPublishVideo: (
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
        onDone: (Boolean, String) -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val context = LocalContext.current

    var selectedChannelId by remember(myChannels) { mutableStateOf(myChannels.firstOrNull()?.id.orEmpty()) }
    var quickChannelName by remember { mutableStateOf("") }
    var quickChannelHandle by remember { mutableStateOf("") }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var playbackUriText by remember { mutableStateOf("") }
    var thumbnailUriText by remember { mutableStateOf<String?>(null) }
    var fileSizeBytes by remember { mutableLongStateOf(0L) }
    var selectedCategory by remember { mutableStateOf(platformSettings.categories.firstOrNull() ?: "Technology") }
    var tagsCsv by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("en") }
    var selectedVisibility by remember { mutableStateOf("PUBLIC") }
    var shortForm by remember { mutableStateOf(defaultShortMode) }
    var ageRestricted by remember { mutableStateOf(false) }

    var isUploading by remember { mutableStateOf(false) }
    var progressValue by remember { mutableStateOf(0f) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    // Zero-permission Photo Picker for Video and Thumbnail (Play Policy compliant)
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            playbackUriText = uri.toString()
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIdx >= 0) {
                    fileSizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }
    }

    val thumbnailPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            thumbnailUriText = uri.toString()
        }
    }

    val uploadsBlockedByModeration = userProfile?.isRestrictedAccount == true ||
        userProfile?.uploadDisabled == true ||
        !platformSettings.uploadsEnabled

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(strings.uploadTitle, style = MaterialTheme.typography.headlineMedium)
        Text(
            strings.uploadSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!platformSettings.transcoderConfigured) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        text = strings.transcoderFallbackNotice,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (uploadsBlockedByModeration) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(strings.suspendedBannerTitle, style = MaterialTheme.typography.titleMedium)
                    Text(strings.suspendedBannerBody, style = MaterialTheme.typography.bodySmall)
                }
            }
            return@Column
        }

        if (myChannels.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(strings.createChannel, style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = quickChannelName,
                        onValueChange = { quickChannelName = it },
                        label = { Text(strings.channelNameLabel) },
                        modifier = Modifier.fillMaxWidth().testTag("quick_channel_name_input")
                    )
                    OutlinedTextField(
                        value = quickChannelHandle,
                        onValueChange = { quickChannelHandle = it },
                        label = { Text(strings.channelHandleLabel) },
                        modifier = Modifier.fillMaxWidth().testTag("quick_channel_handle_input")
                    )
                    Button(
                        onClick = {
                            if (quickChannelName.isNotBlank() && quickChannelHandle.isNotBlank()) {
                                onCreateChannelQuick(quickChannelName, quickChannelHandle, "YouTV Creator Channel")
                            }
                        },
                        modifier = Modifier.testTag("quick_create_channel_btn")
                    ) {
                        Text(strings.createChannel)
                    }
                }
            }
            return@Column
        }

        // Channel Selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            myChannels.forEach { ch ->
                FilterChip(
                    selected = ch.id == selectedChannelId,
                    onClick = { selectedChannelId = ch.id },
                    label = { Text(ch.name) }
                )
            }
        }

        // Video & Thumbnail Pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                modifier = Modifier.weight(1f).testTag("pick_video_btn")
            ) {
                Icon(Icons.Filled.VideoFile, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(strings.pickVideo, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    thumbnailPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f).testTag("pick_thumbnail_btn")
            ) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(strings.pickThumbnail, maxLines = 1)
            }
        }

        OutlinedTextField(
            value = playbackUriText,
            onValueChange = { playbackUriText = it },
            label = { Text("MP4 / HLS (.m3u8) URI or Picked Video") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("upload_video_uri_input")
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(strings.videoTitleLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("upload_title_input")
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(strings.descriptionLabel) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("upload_description_input")
        )

        OutlinedTextField(
            value = tagsCsv,
            onValueChange = { tagsCsv = it },
            label = { Text(strings.tagsLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(strings.categoryLabel, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            platformSettings.categories.take(4).forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(strings.categories[cat] ?: cat) }
                )
            }
        }

        Text(strings.videoLanguageLabel, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = selectedLanguage == lang.code,
                    onClick = { selectedLanguage = lang.code },
                    label = { Text(lang.nativeLabel) }
                )
            }
        }

        Text(strings.visibilityLabel, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "PUBLIC" to strings.publicVisibility,
                "UNLISTED" to strings.unlistedVisibility,
                "PRIVATE" to strings.privateVisibility
            ).forEach { (code, label) ->
                FilterChip(
                    selected = selectedVisibility == code,
                    onClick = { selectedVisibility = code },
                    label = { Text(label) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.shortFormToggle, modifier = Modifier.weight(1f))
            Switch(checked = shortForm, onCheckedChange = { shortForm = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.ageRestrictedToggle, modifier = Modifier.weight(1f))
            Switch(checked = ageRestricted, onCheckedChange = { ageRestricted = it })
        }

        if (isUploading) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(strings.uploadProgress, style = MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(progress = { progressValue }, modifier = Modifier.fillMaxWidth())
            }
        }

        if (statusNotice != null) {
            Text(
                text = statusNotice!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick = {
                val ch = myChannels.firstOrNull { it.id == selectedChannelId } ?: myChannels.first()
                isUploading = true
                progressValue = 0.55f
                onPublishVideo(
                    ch,
                    title,
                    description,
                    playbackUriText,
                    thumbnailUriText,
                    selectedCategory,
                    tagsCsv,
                    selectedLanguage,
                    selectedVisibility,
                    shortForm,
                    ageRestricted,
                    if (shortForm) 30L else 120L,
                    fileSizeBytes.coerceAtLeast(1024L * 512L)
                ) { ok, msg ->
                    isUploading = false
                    progressValue = if (ok) 1f else 0f
                    statusNotice = msg
                    if (ok) {
                        title = ""
                        description = ""
                        playbackUriText = ""
                        tagsCsv = ""
                    }
                }
            },
            enabled = !isUploading && title.isNotBlank() && playbackUriText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("publish_video_btn")
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null)
            Spacer(Modifier.padding(6.dp))
            Text(strings.publishVideo)
        }
    }
}
