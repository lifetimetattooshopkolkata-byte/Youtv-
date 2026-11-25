package com.example.admit

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.distribution.DistributionManager
import com.example.data.distribution.DistributionOverview
import kotlinx.coroutines.launch
import com.example.data.model.Report
import com.example.localization.AppLanguage
import com.example.localization.LocalStrings
import com.example.ui.components.LanguageSelectorRow
import com.example.ui.theme.AdmitAccentAmber
import com.example.ui.theme.AdmitAccentEmerald

@Composable
fun AdmitAuditAndReportsScreen(viewModel: AdmitViewModel) {
    val strings = LocalStrings.current
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val moderationActions by viewModel.moderationActions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    var subTab by remember { mutableStateOf(0) } // 0=Reports, 1=Moderation, 2=Audit Logs
    var resolvingReport by remember { mutableStateOf<Pair<Report, String>?>(null) }
    var resolutionNote by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                label = { Text("Reports (${reports.size})") }
            )
            FilterChip(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                label = { Text("Actions (${moderationActions.size})") }
            )
            FilterChip(
                selected = subTab == 2,
                onClick = { subTab = 2 },
                label = { Text("Audit (${auditLogs.size})") }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (subTab) {
                0 -> {
                    items(reports, key = { it.id }) { report ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${report.targetType} • ${report.reason}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = report.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (report.status == "OPEN") AdmitAccentAmber else AdmitAccentEmerald
                                    )
                                }
                                if (report.details.isNotBlank()) {
                                    Text(report.details, style = MaterialTheme.typography.bodySmall)
                                }
                                if (!report.copyrightWorkUrl.isNullOrBlank()) {
                                    Text(
                                        text = "Reference: ${report.copyrightWorkUrl}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (report.status == "OPEN" || report.status == "UNDER_REVIEW") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                resolutionNote = ""
                                                resolvingReport = report to "RESOLVED"
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) { Text("Resolve") }
                                        OutlinedButton(
                                            onClick = {
                                                resolutionNote = ""
                                                resolvingReport = report to "DISMISSED"
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) { Text("Dismiss") }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    items(moderationActions, key = { it.id }) { action ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${action.actionType} (${action.targetType})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text("Target: ${action.targetId}", style = MaterialTheme.typography.labelSmall)
                                Text("Reason: ${action.reason}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                else -> {
                    items(auditLogs, key = { it.id }) { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${log.action} • ${log.result}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(log.summary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    resolvingReport?.let { (report, status) ->
        AlertDialog(
            onDismissRequest = { resolvingReport = null },
            title = { Text("Mark Report $status") },
            text = {
                OutlinedTextField(
                    value = resolutionNote,
                    onValueChange = { resolutionNote = it },
                    label = { Text("Resolution Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resolveReport(report, status, resolutionNote)
                        resolvingReport = null
                    }
                ) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { resolvingReport = null }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun AdmitSettingsScreen(
    viewModel: AdmitViewModel,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val strings = LocalStrings.current
    val persisted by viewModel.platformSettings.collectAsStateWithLifecycle()

    var registrationsEnabled by remember { mutableStateOf(persisted.registrationsEnabled) }
    var uploadsEnabled by remember { mutableStateOf(persisted.uploadsEnabled) }
    var maintenanceMode by remember { mutableStateOf(persisted.maintenanceMode) }
    var notificationsEnabled by remember { mutableStateOf(persisted.notificationsEnabled) }
    var transcoderConfigured by remember { mutableStateOf(persisted.transcoderConfigured) }
    var maxUploadMbText by remember { mutableStateOf(persisted.maxUploadSizeMb.toString()) }
    var dailyLimitText by remember { mutableStateOf(persisted.dailyUploadLimit.toString()) }
    var quotaMbText by remember { mutableStateOf(persisted.totalStorageQuotaMb.toString()) }
    var keywordsText by remember { mutableStateOf(persisted.autoFlagKeywords.joinToString(", ")) }
    var categoriesText by remember { mutableStateOf(persisted.categories.joinToString(", ")) }

    LaunchedEffect(persisted) {
        registrationsEnabled = persisted.registrationsEnabled
        uploadsEnabled = persisted.uploadsEnabled
        maintenanceMode = persisted.maintenanceMode
        notificationsEnabled = persisted.notificationsEnabled
        transcoderConfigured = persisted.transcoderConfigured
        maxUploadMbText = persisted.maxUploadSizeMb.toString()
        dailyLimitText = persisted.dailyUploadLimit.toString()
        quotaMbText = persisted.totalStorageQuotaMb.toString()
        keywordsText = persisted.autoFlagKeywords.joinToString(", ")
        categoriesText = persisted.categories.joinToString(", ")
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val distributionManager = remember(context) { DistributionManager(context) }
    var distOverview by remember { mutableStateOf(DistributionOverview()) }
    var distStatus by remember { mutableStateOf<String?>(null) }
    var distBusy by remember { mutableStateOf(false) }
    var releaseVersionName by remember { mutableStateOf("1.0.0") }
    var releaseVersionCode by remember { mutableStateOf("1") }
    var releaseNotes by remember { mutableStateOf("YouTV + ADMIT release build") }
    var selectedBuildType by remember { mutableStateOf("release") }

    LaunchedEffect(Unit) {
        distOverview = distributionManager.fetchDistributionOverview()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium)
                LanguageSelectorRow(
                    selectedLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("APK Distribution Manager", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        enabled = !distBusy,
                        onClick = {
                            scope.launch {
                                distBusy = true
                                distOverview = distributionManager.fetchDistributionOverview()
                                distBusy = false
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh releases")
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
                Text(
                    text = "GCS Signed URLs: ${if (distOverview.gcsConfigured) "Active" else "Setup Needed"} • Firebase Hosting: ${if (distOverview.firebaseHostingConfigured) "Active" else "Setup Needed"} • Cloud Build: ${if (distOverview.cloudBuildConfigured) "Active" else "Setup Needed"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (distOverview.statusMessage.isNotBlank()) {
                    Text(
                        text = distOverview.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                distStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AdmitAccentEmerald)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = releaseVersionName,
                        onValueChange = { releaseVersionName = it },
                        label = { Text("Version Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = releaseVersionCode,
                        onValueChange = { releaseVersionCode = it.filter(Char::isDigit) },
                        label = { Text("Code") },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("release", "debug").forEach { bt ->
                        FilterChip(
                            selected = selectedBuildType == bt,
                            onClick = { selectedBuildType = bt },
                            label = { Text(bt.uppercase()) }
                        )
                    }
                }
                OutlinedTextField(
                    value = releaseNotes,
                    onValueChange = { releaseNotes = it },
                    label = { Text("Release Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !distBusy && releaseVersionName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            distBusy = true
                            val res = distributionManager.triggerApkBuild(
                                versionName = releaseVersionName,
                                versionCode = releaseVersionCode.toLongOrNull() ?: 1L,
                                buildType = selectedBuildType,
                                channel = if (selectedBuildType == "release") "production" else "beta",
                                releaseNotes = releaseNotes
                            )
                            distStatus = res.message
                            distOverview = distributionManager.fetchDistributionOverview()
                            distBusy = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admit_trigger_apk_build_button")
                ) {
                    Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Trigger Signed APK Build")
                }

                distOverview.releases.take(5).forEach { rel ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${rel.fileName} (${rel.status})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "v${rel.versionName} (${rel.versionCode}) • ${DistributionManager.formatFileSizeMb(rel.fileSizeBytes)} • Downloads: ${rel.downloadCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (rel.status == "READY") {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val dl = distributionManager.requestDirectDownloadUrl(rel.releaseId)
                                            if (dl.success && dl.downloadUrl != null) {
                                                distributionManager.openDownloadInBrowser(dl.downloadUrl)
                                            }
                                            distStatus = dl.message
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download Signed APK")
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Platform Controls", style = MaterialTheme.typography.titleMedium)

                SettingToggleRow(strings.registrationsToggle, registrationsEnabled) { registrationsEnabled = it }
                SettingToggleRow(strings.uploadsToggle, uploadsEnabled) { uploadsEnabled = it }
                SettingToggleRow(strings.maintenanceToggle, maintenanceMode) { maintenanceMode = it }
                SettingToggleRow(strings.notifications, notificationsEnabled) { notificationsEnabled = it }
                SettingToggleRow("Cloud Transcoder API Linked", transcoderConfigured) { transcoderConfigured = it }

                OutlinedTextField(
                    value = maxUploadMbText,
                    onValueChange = { maxUploadMbText = it.filter(Char::isDigit) },
                    label = { Text("Max Upload File Size (MB)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dailyLimitText,
                    onValueChange = { dailyLimitText = it.filter(Char::isDigit) },
                    label = { Text("Daily Upload Limit / User") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quotaMbText,
                    onValueChange = { quotaMbText = it.filter(Char::isDigit) },
                    label = { Text("Total Platform Storage Quota (MB)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = categoriesText,
                    onValueChange = { categoriesText = it },
                    label = { Text("Video Categories (comma separated)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { keywordsText = it },
                    label = { Text("Auto-Flag Keywords (comma separated)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.saveSettings(
                            persisted.copy(
                                registrationsEnabled = registrationsEnabled,
                                uploadsEnabled = uploadsEnabled,
                                maintenanceMode = maintenanceMode,
                                notificationsEnabled = notificationsEnabled,
                                transcoderConfigured = transcoderConfigured,
                                maxUploadSizeMb = maxUploadMbText.toLongOrNull() ?: 512L,
                                dailyUploadLimit = dailyLimitText.toLongOrNull() ?: 20L,
                                totalStorageQuotaMb = quotaMbText.toLongOrNull() ?: 10240L,
                                categories = categoriesText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                autoFlagKeywords = keywordsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("admit_save_settings_button")
                ) {
                    Text(strings.saveSettings)
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
