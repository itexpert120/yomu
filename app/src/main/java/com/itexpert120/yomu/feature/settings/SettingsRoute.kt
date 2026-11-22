package com.itexpert120.yomu.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.feature.about.DiagnosticsSection
import com.itexpert120.yomu.feature.reader.CustomThemeSheet
import com.itexpert120.yomu.feature.reader.ReaderDefaultsViewModel
import com.itexpert120.yomu.feature.sync.SyncSection

@Composable
fun SettingsRoute(
    appViewModel: AppViewModel,
    onBack: (() -> Unit)?,
    onOpenFontLibrary: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
    val readerDefaultsViewModel: ReaderDefaultsViewModel = hiltViewModel()
    val readerDefaults by readerDefaultsViewModel.state.collectAsStateWithLifecycle()
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
        accentSelection = appearance.accentSelection,
        colorStyle = appearance.colorStyle,
        onSelectTheme = appViewModel::onSelectTheme,
        onToggleOled = appViewModel::onSetOledDark,
        onSelectAccent = { appViewModel.onSelectAccent(it) },
        onSelectColorStyle = { appViewModel.onSelectColorStyle(it) },
        readerSettings = readerDefaults.settings,
        customThemes = readerDefaults.customThemes,
        customFonts = readerDefaults.installedFonts,
        onUpdateReaderSettings = readerDefaultsViewModel::onUpdate,
        onOpenCustomTheme = readerDefaultsViewModel::onOpenCustomTheme,
        onApplyCustomTheme = readerDefaultsViewModel::onApplyCustomTheme,
        onOpenFontLibrary = onOpenFontLibrary,
        onBack = onBack,
        onOpenAbout = onOpenAbout,
        syncSection = { SyncSection() },
        diagnosticsSection = { DiagnosticsSection() },
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
