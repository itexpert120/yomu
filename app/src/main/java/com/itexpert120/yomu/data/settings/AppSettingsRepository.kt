package com.itexpert120.yomu.data.settings

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itexpert120.yomu.core.model.AccentSelection
import com.itexpert120.yomu.core.model.ColorStyle
import com.itexpert120.yomu.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App-level appearance preferences. Backed by Preferences DataStore. */
@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themePreference: Flow<ThemePreference> = dataStore.data.map { prefs ->
        prefs[KeyThemePreference]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
            ?: ThemePreference.System
    }

    /** Use pure-black surfaces when the resolved theme is dark. */
    val oledDark: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KeyOledDark] ?: false }

    /**
     * App colour source. Defaults to the wallpaper palette where Android supports it, unless the
     * user previously switched the old "Wallpaper colors" toggle off.
     */
    val accentSelection: Flow<AccentSelection> = dataStore.data.map { prefs ->
        AccentSelection.deserialize(prefs[KeyAccent]) ?: run {
            val legacyDynamic = prefs[LegacyKeyDynamicColors] ?: prefs[LegacyKeyDynamicDarkColors]
            if (legacyDynamic != false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AccentSelection.Wallpaper
            } else {
                AccentSelection.FallbackPreset
            }
        }
    }

    val colorStyle: Flow<ColorStyle> = dataStore.data.map { prefs ->
        prefs[KeyColorStyle]?.let { runCatching { ColorStyle.valueOf(it) }.getOrNull() }
            ?: ColorStyle.Balanced
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        dataStore.edit { it[KeyThemePreference] = preference.name }
    }

    suspend fun setOledDark(enabled: Boolean) {
        dataStore.edit { it[KeyOledDark] = enabled }
    }

    suspend fun setAccentSelection(selection: AccentSelection) {
        dataStore.edit {
            it[KeyAccent] = AccentSelection.serialize(selection)
            it.remove(LegacyKeyDynamicColors)
            it.remove(LegacyKeyDynamicDarkColors)
        }
    }

    suspend fun setColorStyle(style: ColorStyle) {
        dataStore.edit { it[KeyColorStyle] = style.name }
    }

    private companion object {
        val KeyThemePreference = stringPreferencesKey("theme_preference")
        val KeyOledDark = booleanPreferencesKey("theme_oled_dark")
        val LegacyKeyDynamicColors = booleanPreferencesKey("theme_dynamic_colors")
        val LegacyKeyDynamicDarkColors = booleanPreferencesKey("theme_dynamic_dark_colors")
        val KeyAccent = stringPreferencesKey("accent_color")
        val KeyColorStyle = stringPreferencesKey("color_style")
    }
}
