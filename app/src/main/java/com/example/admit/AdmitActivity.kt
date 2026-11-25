package com.example.admit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.attemptAutoSignIn
import com.example.localization.AppLanguage
import com.example.localization.LanguagePreferenceStore
import com.example.localization.LocalLanguage
import com.example.localization.LocalStrings
import com.example.localization.stringsFor
import com.example.ui.components.AuthScreen
import com.example.ui.theme.AdmitTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class AdmitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdmitTheme {
                AdmitRoot()
            }
        }
    }
}

@Composable
fun AdmitRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefStore = remember(context) { LanguagePreferenceStore(context) }
    val selectedLanguage by prefStore.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.EN)
    val strings = remember(selectedLanguage) { stringsFor(selectedLanguage) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    CompositionLocalProvider(
        LocalLanguage provides selectedLanguage,
        LocalStrings provides strings
    ) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                isAdmitMode = true,
                onLanguageSelected = { lang ->
                    scope.launch { prefStore.setLanguage(lang) }
                },
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        } else {
            key(user.uid) {
                AdmitAuthenticatedApp(
                    firebaseUser = user,
                    currentLanguage = selectedLanguage,
                    onLanguageSelected = { lang ->
                        scope.launch { prefStore.setLanguage(lang) }
                    }
                )
            }
        }
    }
}
