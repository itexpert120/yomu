package com.itexpert120.yomu.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itexpert120.yomu.app.AppViewModel
import com.itexpert120.yomu.core.designsystem.supportsYomuTwoPane
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.model.ReaderFont
import com.itexpert120.yomu.feature.about.AboutPane
import com.itexpert120.yomu.feature.about.sendFeedback
import com.itexpert120.yomu.feature.reader.CustomThemeSheet
import com.itexpert120.yomu.feature.reader.FontLibraryRoute
import com.itexpert120.yomu.feature.reader.ReaderDefaultsScreen
import com.itexpert120.yomu.feature.reader.ReaderDefaultsViewModel
import com.itexpert120.yomu.feature.reader.SaveFailureNotice
import com.itexpert120.yomu.feature.sync.SyncRoute
import com.itexpert120.yomu.feature.sync.SyncViewModel

/** Pages a phone opens from the list in place (no app destination exists for them yet). */
private enum class SettingsPage { Home, ReadingDefaults, Sync }

/**
 * Settings. On phones the list opens Fonts and About as app destinations; Reading defaults and
 * Sync open as pages inside this route unless [onOpenReaderDefaults] / [onOpenSync] route them to
 * destinations of their own. From the two-pane width every section opens in the detail pane.
 */
@Composable
fun SettingsRoute(
    appViewModel: AppViewModel,
    onBack: (() -> Unit)?,
    onOpenFontLibrary: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenReaderDefaults: (() -> Unit)? = null,
    onOpenSync: (() -> Unit)? = null,
) {
    val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
    val readerDefaultsViewModel: ReaderDefaultsViewModel = hiltViewModel()
    val readerDefaults by readerDefaultsViewModel.state.collectAsStateWithLifecycle()
    val syncViewModel: SyncViewModel = hiltViewModel()
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var themeSheetVisible by rememberSaveable { mutableStateOf(false) }
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.Home.name) }
    val page = SettingsPage.valueOf(pageName)
    var paneName by rememberSaveable { mutableStateOf(SettingsPane.Appearance.name) }
    val selectedPane = SettingsPane.valueOf(paneName)

    val state = SettingsUiState(
        selectedTheme = appearance.themePreference,
        oledDark = appearance.oledDark,
        readerSettings = readerDefaults.settings,
        installedFontCount = ReaderFont.entries.size + readerDefaults.installedFonts.size,
        sync = sync,
    )
    val readingDefaults: @Composable (onBack: (() -> Unit)?) -> Unit = { back ->
        ReaderDefaultsScreen(
            settings = readerDefaults.settings,
            customThemes = readerDefaults.customThemes,
            customFonts = readerDefaults.installedFonts,
            onUpdate = readerDefaultsViewModel::onUpdate,
            onOpenCustomTheme = readerDefaultsViewModel::onOpenCustomTheme,
            onApplyCustomTheme = readerDefaultsViewModel::onApplyCustomTheme,
            onOpenFontLibrary = onOpenFontLibrary,
            onBack = back,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth.supportsYomuTwoPane()) {
            TabletSettingsLayout(
                state = state,
                selectedPane = selectedPane,
                actions = SettingsActions(
                    onOpenPane = { paneName = it.name },
                    onOpenTheme = { paneName = SettingsPane.Appearance.name },
                    onToggleOled = appViewModel::onSetOledDark,
                    onUpdateReaderSettings = readerDefaultsViewModel::onUpdate,
                    onSendFeedback = { sendFeedback(context) },
                ),
            ) { pane ->
                when (pane) {
                    SettingsPane.Sync -> SyncRoute(onBack = null, viewModel = syncViewModel)
                    SettingsPane.Appearance -> AppearancePane(
                        state = state,
                        onSelectTheme = appViewModel::onSelectTheme,
                        onToggleOled = appViewModel::onSetOledDark,
                    )
                    SettingsPane.ReadingDefaults -> readingDefaults(null)
                    SettingsPane.Fonts -> FontLibraryRoute(onBack = null)
                    SettingsPane.About -> AboutPane(onBack = null)
                }
            }
        } else {
            val home = { pageName = SettingsPage.Home.name }
            BackHandler(enabled = page != SettingsPage.Home, onBack = home)
            AnimatedContent(
                targetState = page,
                transitionSpec = { yomuContentSwap(forward = targetState != SettingsPage.Home) },
                label = "settingsPage",
            ) { current ->
                when (current) {
                    SettingsPage.Home -> SettingsScreen(
                        state = state,
                        actions = SettingsActions(
                            onOpenPane = { pane ->
                                when (pane) {
                                    SettingsPane.Sync -> onOpenSync?.invoke() ?: run { pageName = SettingsPage.Sync.name }
                                    SettingsPane.ReadingDefaults ->
                                        onOpenReaderDefaults?.invoke() ?: run { pageName = SettingsPage.ReadingDefaults.name }
                                    SettingsPane.Fonts -> onOpenFontLibrary()
                                    SettingsPane.About -> onOpenAbout()
                                    SettingsPane.Appearance -> themeSheetVisible = true
                                }
                            },
                            onOpenTheme = { themeSheetVisible = true },
                            onToggleOled = appViewModel::onSetOledDark,
                            onUpdateReaderSettings = readerDefaultsViewModel::onUpdate,
                            onSendFeedback = { sendFeedback(context) },
                        ),
                    )
                    SettingsPage.ReadingDefaults -> readingDefaults(home)
                    SettingsPage.Sync -> SyncRoute(onBack = home, viewModel = syncViewModel)
                }
            }
        }
    }

    ThemeSheet(
        visible = themeSheetVisible,
        selectedTheme = appearance.themePreference,
        oledDark = appearance.oledDark,
        onSelectTheme = appViewModel::onSelectTheme,
        onToggleOled = appViewModel::onSetOledDark,
        onDismiss = { themeSheetVisible = false },
    )
    SaveFailureNotice(readerDefaults.settingsError, readerDefaultsViewModel::onRetrySettings)
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
