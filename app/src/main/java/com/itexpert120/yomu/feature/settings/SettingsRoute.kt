package com.itexpert120.yomu.feature.settings

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.feature.reader.CustomThemeSheet
import com.itexpert120.yomu.feature.reader.ReaderDefaultsViewModel

@Composable
fun SettingsRoute(
    appViewModel: AppViewModel,
    onBack: (() -> Unit)?,
    onOpenFontLibrary: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val appearance by appViewModel.appearance.collectAsState()
    val readerDefaultsViewModel: ReaderDefaultsViewModel = hiltViewModel()
    val readerDefaults by readerDefaultsViewModel.state.collectAsState()
    val preference = appearance.themePreference
    val oledDark = appearance.oledDark
    val dynamicColors = appearance.dynamicColors
    val systemDark = isSystemInDarkTheme()
    // The OLED toggle only has an effect when the resolved theme is dark.
    val darkActive = when (preference) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val dynamicColorsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SettingsScreen(
        selectedTheme = preference,
        oledDark = oledDark,
        oledEnabled = darkActive,
        dynamicColors = dynamicColors,
        dynamicColorsAvailable = dynamicColorsAvailable,
        onSelectTheme = appViewModel::onSelectTheme,
        onToggleOled = appViewModel::onSetOledDark,
        onToggleDynamicColors = appViewModel::onSetDynamicColors,
        readerSettings = readerDefaults.settings,
        customThemes = readerDefaults.customThemes,
        customFonts = readerDefaults.installedFonts,
        onUpdateReaderSettings = readerDefaultsViewModel::onUpdate,
        onOpenCustomTheme = readerDefaultsViewModel::onOpenCustomTheme,
        onApplyCustomTheme = readerDefaultsViewModel::onApplyCustomTheme,
        onOpenFontLibrary = onOpenFontLibrary,
        onBack = onBack,
        onOpenAbout = onOpenAbout,
    )

    CustomThemeSheet(
        visible = readerDefaults.customSheetVisible,
        settings = readerDefaults.settings,
        customThemes = readerDefaults.customThemes,
        onDismiss = readerDefaultsViewModel::onCloseCustomTheme,
        onUpdateSettings = readerDefaultsViewModel::onUpdate,
        onSave = readerDefaultsViewModel::onSaveCustomTheme,
        onApply = readerDefaultsViewModel::onApplyCustomTheme,
        onDelete = readerDefaultsViewModel::onDeleteCustomTheme,
    )
}
