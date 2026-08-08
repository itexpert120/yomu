package com.itexpert120.yomu.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.core.model.ThemePreference

@Composable
fun SettingsRoute(
    appViewModel: AppViewModel,
    onBack: (() -> Unit)?,
    onOpenStats: () -> Unit,
    onOpenReaderDefaults: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val appearance by appViewModel.appearance.collectAsState()
    val preference = appearance.themePreference
    val oledDark = appearance.oledDark
    val systemDark = isSystemInDarkTheme()
    // The OLED toggle only has an effect when the resolved theme is dark.
    val darkActive = when (preference) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }

    SettingsScreen(
        selectedTheme = preference,
        oledDark = oledDark,
        oledEnabled = darkActive,
        onSelectTheme = appViewModel::onSelectTheme,
        onToggleOled = appViewModel::onSetOledDark,
        onBack = onBack,
        onOpenStats = onOpenStats,
        onOpenReaderDefaults = onOpenReaderDefaults,
        onOpenAbout = onOpenAbout,
    )
}
