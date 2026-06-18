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
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val preference by appViewModel.themePreference.collectAsState()
    val oledDark by appViewModel.oledDark.collectAsState()
    val accentSelection by appViewModel.accentSelection.collectAsState()
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
        accentSelection = accentSelection,
        accentIsDark = darkActive,
        onSelectTheme = appViewModel::onSelectTheme,
        onToggleOled = appViewModel::onSetOledDark,
        onSelectAccent = appViewModel::onSelectAccent,
        onSelectCustomAccent = appViewModel::onSelectCustomAccent,
        onBack = onBack,
        onOpenAbout = onOpenAbout,
    )
}
