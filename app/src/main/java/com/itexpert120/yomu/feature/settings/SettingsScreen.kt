package com.itexpert120.yomu.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.BuildConfig
import com.itexpert120.yomu.core.designsystem.YomuChip
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.feature.reader.ReaderPreferenceControls

@Composable
fun SettingsScreen(
    selectedTheme: ThemePreference,
    oledDark: Boolean,
    oledEnabled: Boolean,
    dynamicColors: Boolean,
    dynamicColorsAvailable: Boolean,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    onToggleDynamicColors: (Boolean) -> Unit,
    readerSettings: ReaderSettings,
    customThemes: List<CustomReaderTheme>,
    customFonts: List<CustomFontRef>,
    onUpdateReaderSettings: (ReaderSettings) -> Unit,
    onOpenCustomTheme: () -> Unit,
    onApplyCustomTheme: (CustomReaderTheme) -> Unit,
    onOpenFontLibrary: () -> Unit,
    onBack: (() -> Unit)?,
    onOpenAbout: () -> Unit,
) {
    YomuScreenScaffold(
        title = "Settings",
        onBack = onBack,
        showScrollEdgeShadow = false,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = YomuWidthClass.fromWidth(maxWidth).isWide
            val readerControls: @Composable () -> Unit = {
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

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(0.9f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppearanceGroup(
                            selectedTheme = selectedTheme,
                            oledDark = oledDark,
                            oledEnabled = oledEnabled,
                            dynamicColors = dynamicColors,
                            dynamicColorsAvailable = dynamicColorsAvailable,
                            onSelectTheme = onSelectTheme,
                            onToggleOled = onToggleOled,
                            onToggleDynamicColors = onToggleDynamicColors,
                        )
                        AboutGroup(onOpenAbout = onOpenAbout)
                        VersionFooter()
                    }
                    Column(
                        modifier = Modifier.weight(1.5f),
                    ) {
                        readerControls()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppearanceGroup(
                        selectedTheme = selectedTheme,
                        oledDark = oledDark,
                        oledEnabled = oledEnabled,
                        dynamicColors = dynamicColors,
                        dynamicColorsAvailable = dynamicColorsAvailable,
                        onSelectTheme = onSelectTheme,
                        onToggleOled = onToggleOled,
                        onToggleDynamicColors = onToggleDynamicColors,
                    )
                    readerControls()
                    AboutGroup(onOpenAbout = onOpenAbout)
                    VersionFooter()
                }
            }
        }
    }
}

@Composable
private fun AppearanceGroup(
    selectedTheme: ThemePreference,
    oledDark: Boolean,
    oledEnabled: Boolean,
    dynamicColors: Boolean,
    dynamicColorsAvailable: Boolean,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    onToggleDynamicColors: (Boolean) -> Unit,
) {
    YomuSettingGroup(
        title = "Appearance",
        subtitle = "Set the app’s tone. Reader page themes stay independent.",
    ) {
        Text(
            text = "App theme",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        ThemeChoiceRow(
            selectedTheme = selectedTheme,
            onSelectTheme = onSelectTheme,
        )
        DynamicColorsSettingRow(
            checked = dynamicColors && dynamicColorsAvailable,
            available = dynamicColorsAvailable,
            onCheckedChange = onToggleDynamicColors,
        )
        OledSettingRow(
            checked = oledDark && oledEnabled,
            enabled = oledEnabled,
            onCheckedChange = onToggleOled,
        )
    }
}

@Composable
private fun ThemeChoiceRow(
    selectedTheme: ThemePreference,
    onSelectTheme: (ThemePreference) -> Unit,
) {
    val themes = ThemePreference.entries
    val selectedIndex = themes.indexOf(selectedTheme).coerceAtLeast(0)
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        val layoutInfo = listState.layoutInfo
        val selectedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val fullyVisible = selectedItem != null &&
            selectedItem.offset >= layoutInfo.viewportStartOffset &&
            selectedItem.offset + selectedItem.size <= layoutInfo.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(themes, key = { it.name }) { theme ->
            YomuChip(
                text = theme.label,
                selected = theme == selectedTheme,
                onClick = { onSelectTheme(theme) },
            )
        }
    }
}

@Composable
private fun DynamicColorsSettingRow(
    checked: Boolean,
    available: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    YomuSettingRow(
        title = "Dynamic colors",
        subtitle = if (available) {
            "Use wallpaper colors for light and dark app themes"
        } else {
            "Requires Android 12 or newer"
        },
        enabled = available,
    ) {
        YomuTogglePill(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = available,
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
            subtitle = "Privacy, terms, acknowledgements, and version",
            modifier = Modifier.yomuPressable(onClick = onOpenAbout),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
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
            .padding(horizontal = 4.dp, vertical = 2.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
