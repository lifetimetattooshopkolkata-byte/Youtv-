package com.example.admit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.firebase.AdmitRepository
import com.example.data.firebase.OwnerAuthStatus
import com.example.data.firebase.signOutUser
import com.example.localization.AppLanguage
import com.example.localization.LocalStrings
import com.example.ui.components.AdmitBrandLockup
import com.example.ui.components.LanguageSelectorRow
import com.google.firebase.auth.FirebaseUser

enum class AdmitTab(val icon: ImageVector, val testTag: String) {
    DASHBOARD(Icons.Filled.Analytics, "admit_tab_dashboard"),
    USERS(Icons.Filled.People, "admit_tab_users"),
    VIDEOS(Icons.Filled.VideoLibrary, "admit_tab_videos"),
    AUDIT(Icons.Filled.Policy, "admit_tab_audit"),
    SETTINGS(Icons.Filled.Settings, "admit_tab_settings")
}

@Composable
fun AdmitAuthenticatedApp(
    firebaseUser: FirebaseUser,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: AdmitViewModel = viewModel(
        key = "admit_vm_${firebaseUser.uid}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AdmitViewModel(AdmitRepository(context), firebaseUser.uid) as T
            }
        }
    )

    val ownerStatus by viewModel.ownerAuthStatus.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    when (val status = ownerStatus) {
        OwnerAuthStatus.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .safeDrawingPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AdmitBrandLockup()
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = strings.loading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is OwnerAuthStatus.Denied -> {
            AdmitAccessDeniedScreen(
                email = firebaseUser.email.orEmpty(),
                uid = firebaseUser.uid,
                reason = status.reason,
                setupRequired = status.setupRequired,
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                onRecheck = { viewModel.recheckOwnerAuthorization() },
                onSignOut = {
                    signOutUser(credentialManager, scope)
                }
            )
        }

        is OwnerAuthStatus.Verified -> {
            AdmitOwnerConsole(
                viewModel = viewModel,
                ownerEmail = firebaseUser.email ?: status.adminRecord.email,
                backendStatusMessage = status.backendMessage,
                statusMessage = statusMessage,
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                onSignOut = {
                    signOutUser(credentialManager, scope)
                }
            )
        }
    }
}

@Composable
fun AdmitAccessDeniedScreen(
    email: String,
    uid: String,
    reason: String,
    setupRequired: Boolean,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onRecheck: () -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AdmitBrandLockup()
                Icon(
                    imageVector = Icons.Filled.GppBad,
                    contentDescription = strings.accessDeniedOwnerOnly,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = if (setupRequired) strings.ownerSetupRequiredTitle else strings.accessDeniedOwnerOnly,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.ownerSetupRequiredBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Signed in: ${email.ifBlank { uid }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "UID: $uid",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Bootstrap command:\nOWNER_EMAIL=\"your-owner-email@domain.com\" npm --prefix backend run bootstrap-owner",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                LanguageSelectorRow(
                    selectedLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("admit_denied_sign_out_button")
                    ) {
                        Text(strings.signOut)
                    }
                    Button(
                        onClick = onRecheck,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("admit_recheck_owner_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.retry)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdmitOwnerConsole(
    viewModel: AdmitViewModel,
    ownerEmail: String,
    backendStatusMessage: String,
    statusMessage: String?,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalStrings.current
    var activeTab by remember { mutableStateOf(AdmitTab.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage)
            viewModel.clearMessage()
        }
    }

    val tabLabel: (AdmitTab) -> String = { tab ->
        when (tab) {
            AdmitTab.DASHBOARD -> strings.admitDashboard
            AdmitTab.USERS -> strings.admitUsers
            AdmitTab.VIDEOS -> strings.admitVideos
            AdmitTab.AUDIT -> strings.admitAudit
            AdmitTab.SETTINGS -> strings.admitSettings
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = { AdmitBrandLockup() },
                    actions = {
                        Text(
                            text = ownerEmail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("admit_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = strings.signOut,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        AdmitTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tabLabel(tab)) },
                                label = { Text(tabLabel(tab), maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.height(12.dp))
                        AdmitTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tabLabel(tab)) },
                                label = { Text(tabLabel(tab)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (activeTab) {
                        AdmitTab.DASHBOARD -> AdmitDashboardScreen(
                            viewModel = viewModel,
                            backendStatusMessage = backendStatusMessage
                        )
                        AdmitTab.USERS -> AdmitUsersScreen(viewModel = viewModel)
                        AdmitTab.VIDEOS -> AdmitVideosScreen(viewModel = viewModel)
                        AdmitTab.AUDIT -> AdmitAuditAndReportsScreen(viewModel = viewModel)
                        AdmitTab.SETTINGS -> AdmitSettingsScreen(
                            viewModel = viewModel,
                            currentLanguage = currentLanguage,
                            onLanguageSelected = onLanguageSelected
                        )
                    }
                }
            }
        }
    }
}
