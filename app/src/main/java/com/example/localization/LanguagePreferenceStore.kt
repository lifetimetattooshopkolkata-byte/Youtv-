package com.example.localization

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.youTvPrefsDataStore by preferencesDataStore(name = "youtv_preferences")

class LanguagePreferenceStore(private val context: Context) {
    private val keyLanguage = stringPreferencesKey("preferred_language")
    private val keyRestrictedMode = booleanPreferencesKey("restricted_mode")

    val languageFlow: Flow<AppLanguage> = context.youTvPrefsDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[keyLanguage])
    }

    val restrictedModeFlow: Flow<Boolean> = context.youTvPrefsDataStore.data.map { prefs ->
        prefs[keyRestrictedMode] ?: false
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.youTvPrefsDataStore.edit { prefs ->
            prefs[keyLanguage] = language.code
        }
    }

    suspend fun setRestrictedMode(enabled: Boolean) {
        context.youTvPrefsDataStore.edit { prefs ->
            prefs[keyRestrictedMode] = enabled
        }
    }
}
