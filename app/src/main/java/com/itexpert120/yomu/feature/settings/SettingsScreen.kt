@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.BuildConfig
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuScreenHeader
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuSettingContainer
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.YomuTwoPane
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.supportsYomuTwoPane
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.model.AccentSelection
import com.itexpert120.yomu.core.model.ColorStyle
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.feature.about.AboutContent
import com.itexpert120.yomu.feature.reader.ReaderPreferenceControls

private enum class SettingsPane(val title: String, val subtitle: String) {
    Appearance("Appearance", "Mode, colour theme, and OLED"),
    Reading("Reading", "Reader defaults and font library"),
    Sync("Sync", "Sync your reading across devices"),
    About("About", "Privacy, terms, and acknowledgements"),
}

@Composable
fun SettingsScreen(
    selectedTheme: ThemePreference,
    oledDark: Boolean,
    oledEnabled: Boolean,
    accentSelection: AccentSelection,
    colorStyle: ColorStyle,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    onSelectAccent: (AccentSelection) -> Unit,
    onSelectColorStyle: (ColorStyle) -> Unit,
    readerSettings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    customFonts: List<CustomFontRef>,
    onUpdateReaderSettings: (ReaderSettings) -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onOpenFontLibrary: () -> Unit,
    onBack: (() -> Unit)?,
    onOpenAbout: () -> Unit,
    // Stateful slot (it owns its ViewModel) so this screen stays preview-driven.
    syncSection: @Composable () -> Unit = {},
    diagnosticsSection: @Composable () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth.supportsYomuTwoPane()) {
            TabletSettingsLayout(
                selectedTheme = selectedTheme,
                oledDark = oledDark,
                oledEnabled = oledEnabled,
                accentSelection = accentSelection,
                colorStyle = colorStyle,
                onSelectTheme = onSelectTheme,
                onToggleOled = onToggleOled,
                onSelectAccent = onSelectAccent,
                onSelectColorStyle = onSelectColorStyle,
                readerSettings = readerSettings,
                customThemes = customThemes,
                customFonts = customFonts,
                onUpdateReaderSettings = onUpdateReaderSettings,
                onOpenCustomTheme = onOpenCustomTheme,
                onApplyCustomTheme = onApplyCustomTheme,
                onOpenFontLibrary = onOpenFontLibrary,
                onBack = onBack,
                syncSection = syncSection,
                diagnosticsSection = diagnosticsSection,
            )
        } else {
            PhoneSettingsLayout(
                selectedTheme = selectedTheme,
                oledDark = oledDark,
                oledEnabled = oledEnabled,
                accentSelection = accentSelection,
                colorStyle = colorStyle,
                onSelectTheme = onSelectTheme,
                onToggleOled = onToggleOled,
                onSelectAccent = onSelectAccent,
                onSelectColorStyle = onSelectColorStyle,
                readerSettings = readerSettings,
                customThemes = customThemes,
                customFonts = customFonts,
                onUpdateReaderSettings = onUpdateReaderSettings,
                onOpenCustomTheme = onOpenCustomTheme,
                onApplyCustomTheme = onApplyCustomTheme,
                onOpenFontLibrary = onOpenFontLibrary,
                onBack = onBack,
                onOpenAbout = onOpenAbout,
                syncSection = syncSection,
            )
        }
    }
}

@Composable
private fun PhoneSettingsLayout(
    selectedTheme: ThemePreference,
    oledDark: Boolean,
    oledEnabled: Boolean,
    accentSelection: AccentSelection,
    colorStyle: ColorStyle,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    onSelectAccent: (AccentSelection) -> Unit,
    onSelectColorStyle: (ColorStyle) -> Unit,
    readerSettings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    customFonts: List<CustomFontRef>,
    onUpdateReaderSettings: (ReaderSettings) -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onOpenFontLibrary: () -> Unit,
    onBack: (() -> Unit)?,
    onOpenAbout: () -> Unit,
    // Stateful slot (it owns its ViewModel) so this screen stays preview-driven.
    syncSection: @Composable () -> Unit = {},
) {
    YomuScreenScaffold(
        title = "Settings",
        onBack = onBack,
        showScrollEdgeShadow = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md)) {
            AppearanceGroup(
                selectedTheme = selectedTheme,
                oledDark = oledDark,
                oledEnabled = oledEnabled,
                accentSelection = accentSelection,
                colorStyle = colorStyle,
                onSelectTheme = onSelectTheme,
                onToggleOled = onToggleOled,
                onSelectAccent = onSelectAccent,
                onSelectColorStyle = onSelectColorStyle,
            )
            ReaderControls(
                readerSettings = readerSettings,
                customThemes = customThemes,
                customFonts = customFonts,
                onUpdateReaderSettings = onUpdateReaderSettings,
                onOpenCustomTheme = onOpenCustomTheme,
                onApplyCustomTheme = onApplyCustomTheme,
                onOpenFontLibrary = onOpenFontLibrary,
            )
            syncSection()
            AboutGroup(onOpenAbout = onOpenAbout)
            VersionFooter()
        }
    }
}

@Composable
private fun TabletSettingsLayout(
    selectedTheme: ThemePreference,
    oledDark: Boolean,
    oledEnabled: Boolean,
    accentSelection: AccentSelection,
    colorStyle: ColorStyle,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    onSelectAccent: (AccentSelection) -> Unit,
    onSelectColorStyle: (ColorStyle) -> Unit,
    readerSettings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    customFonts: List<CustomFontRef>,
    onUpdateReaderSettings: (ReaderSettings) -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onOpenFontLibrary: () -> Unit,
    onBack: (() -> Unit)?,
    syncSection: @Composable () -> Unit,
    diagnosticsSection: @Composable () -> Unit,
) {
    var selectedPaneName by rememberSaveable { mutableStateOf(SettingsPane.Appearance.name) }
    val selectedPane = SettingsPane.entries.first { it.name == selectedPaneName }
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
        .calculateBottomPadding()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    YomuAppSurface {
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            YomuScreenHeader(title = "Settings", onBack = onBack, scrollBehavior = scrollBehavior)
            YomuTwoPane(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                startModifier = Modifier.padding(top = YomuTheme.space.xs),
                endModifier = Modifier.padding(top = YomuTheme.space.xs),
                startContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = navBottom + YomuTheme.space.md),
                    ) {
                        YomuSettingList {
                            SettingsPane.entries.forEach { pane ->
                                SettingsPaneRow(
                                    pane = pane,
                                    selected = pane == selectedPane,
                                    onClick = { selectedPaneName = pane.name },
                                )
                            }
                        }
                    }
                },
                endContent = {
                    AnimatedContent(
                        targetState = selectedPane,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = { yomuContentSwap(forward = targetState.ordinal > initialState.ordinal) },
                        label = "settingsPane",
                    ) { pane ->
                        // Each pane owns its scroll state, so offsets never carry over between panes.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            // Detail content keeps a readable line length, centred in its pane.
                            Column(
                                modifier = Modifier
                                    .widthIn(max = YomuReadableMaxWidth)
                                    .fillMaxWidth()
                                    .padding(bottom = navBottom + YomuTheme.space.lg),
                                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
                            ) {
                                when (pane) {
                                    SettingsPane.Appearance -> AppearanceGroup(
                                        selectedTheme = selectedTheme,
                                        oledDark = oledDark,
                                        oledEnabled = oledEnabled,
                                        accentSelection = accentSelection,
                                        colorStyle = colorStyle,
                                        onSelectTheme = onSelectTheme,
                                        onToggleOled = onToggleOled,
                                        onSelectAccent = onSelectAccent,
                                        onSelectColorStyle = onSelectColorStyle,
                                    )

                                    SettingsPane.Reading -> ReaderControls(
                                        readerSettings = readerSettings,
                                        customThemes = customThemes,
                                        customFonts = customFonts,
                                        onUpdateReaderSettings = onUpdateReaderSettings,
                                        onOpenCustomTheme = onOpenCustomTheme,
                                        onApplyCustomTheme = onApplyCustomTheme,
                                        onOpenFontLibrary = onOpenFontLibrary,
                                    )

                                    SettingsPane.Sync -> syncSection()

                                    SettingsPane.About -> AboutContent(diagnosticsSection = diagnosticsSection)
                                }
                                if (pane != SettingsPane.About) VersionFooter()
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsPaneRow(
    pane: SettingsPane,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon = when (pane) {
        SettingsPane.Appearance -> StackIcons.Palette
        SettingsPane.Reading -> StackIcons.MenuBook
        SettingsPane.Sync -> StackIcons.Sync
        SettingsPane.About -> StackIcons.Info
    }
    YomuSettingRow(
        title = pane.title,
        subtitle = pane.subtitle,
        selected = selected,
        position = when (pane) {
            SettingsPane.Appearance -> YomuSettingPosition.First
            SettingsPane.Reading -> YomuSettingPosition.Middle
            SettingsPane.Sync -> YomuSettingPosition.Middle
            SettingsPane.About -> YomuSettingPosition.Last
        },
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    ) {
        // The selected row already reads as "you are here"; only the others point onward.
        if (!selected) {
            Icon(
                imageVector = StackIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ReaderControls(
    readerSettings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    customFonts: List<CustomFontRef>,
    onUpdateReaderSettings: (ReaderSettings) -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onOpenFontLibrary: () -> Unit,
) {
    ReaderPreferenceControls(
        settings = readerSettings,
        customThemes = customThemes,
        onUpdateSettings = onUpdateReaderSettings,
        onOpenCustomTheme = onOpenCustomTheme,
        onApplyCustomTheme = onApplyCustomTheme,
        customFonts = customFonts,
        onManageFonts = onOpenFontLibrary,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AppearanceGroup(
    selectedTheme: ThemePreference,
    oledDark: Boolean,
    oledEnabled: Boolean,
    accentSelection: AccentSelection,
    colorStyle: ColorStyle,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    onSelectAccent: (AccentSelection) -> Unit,
    onSelectColorStyle: (ColorStyle) -> Unit,
) {
    YomuSettingGroup(
        title = "Appearance",
        subtitle = "Set the app’s tone. Reader page themes stay independent.",
    ) {
        YomuSectionLabel("Mode")
        YomuSettingContainer {
            ThemeChoiceRow(
                selectedTheme = selectedTheme,
                onSelectTheme = onSelectTheme,
            )
        }
        YomuSectionLabel("Colour theme")
        YomuSettingContainer {
            ColorThemePicker(
                selection = accentSelection,
                style = colorStyle,
                onSelect = onSelectAccent,
            )
        }
        YomuSectionLabel(
            if (accentSelection == AccentSelection.Wallpaper) "Palette style · set by your wallpaper" else "Palette style",
        )
        YomuSettingContainer {
            ColorStyleRow(
                selected = colorStyle,
                enabled = accentSelection != AccentSelection.Wallpaper,
                onSelect = onSelectColorStyle,
            )
        }
        OledSettingRow(
            checked = oledDark && oledEnabled,
            enabled = oledEnabled,
            onCheckedChange = onToggleOled,
        )
    }
}

@Composable
private fun OledSettingRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    YomuSettingRow(
        title = "OLED black",
        position = YomuSettingPosition.Single,
        subtitle = if (enabled) {
            "Use true black surfaces in dark mode"
        } else {
            "Choose Dark or use a dark system theme to enable"
        },
        enabled = enabled,
    ) {
        YomuTogglePill(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun AboutGroup(onOpenAbout: () -> Unit) {
    YomuSettingGroup(
        title = "Open Reader",
        subtitle = "Learn about the app and how it keeps your books private.",
    ) {
        YomuSettingRow(
            title = "About Open Reader",
            position = YomuSettingPosition.Single,
            subtitle = "Privacy, terms, acknowledgements, and version",
            onClick = onOpenAbout,
        ) {
            Icon(
                imageVector = StackIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun VersionFooter() {
    Text(
        text = "Open Reader v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(YomuTheme.space.xxs),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Preview(widthDp = 720, heightDp = 900, showBackground = true)
@Composable
private fun TabletSettingsPreview() {
    YomuDesignTheme {
        SettingsScreen(
            selectedTheme = ThemePreference.System,
            oledDark = false,
            oledEnabled = true,
            accentSelection = AccentSelection.FallbackPreset,
            colorStyle = ColorStyle.Balanced,
            onSelectTheme = {},
            onToggleOled = {},
            onSelectAccent = {},
            onSelectColorStyle = {},
            readerSettings = ReaderSettings(),
            customThemes = emptyList(),
            customFonts = emptyList(),
            onUpdateReaderSettings = {},
            onOpenCustomTheme = {},
            onApplyCustomTheme = {},
            onOpenFontLibrary = {},
            onBack = {},
            onOpenAbout = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun PhoneSettingsPreview() {
    TabletSettingsPreview()
}
