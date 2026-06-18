package com.itexpert120.yomu.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itexpert120.yomu.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-level preferences (theme choice + pure-black toggle). Backed by Preferences DataStore. */
class AppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val themePreference: Flow<ThemePreference> = dataStore.data.map { prefs ->
        prefs[KeyThemePreference]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
            ?: ThemePreference.System
    }

    /** Use pure-black surfaces when the resolved theme is dark. */
    val oledDark: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KeyOledDark] ?: false }

    suspend fun setThemePreference(preference: ThemePreference) {
        dataStore.edit { it[KeyThemePreference] = preference.name }
    }

    suspend fun setOledDark(enabled: Boolean) {
        dataStore.edit { it[KeyOledDark] = enabled }
    }

    private companion object {
        val KeyThemePreference = stringPreferencesKey("theme_preference")
        val KeyOledDark = booleanPreferencesKey("theme_oled_dark")
    }
}
