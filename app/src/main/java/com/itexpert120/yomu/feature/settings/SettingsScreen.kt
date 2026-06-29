package com.itexpert120.yomu.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.BuildConfig
import com.itexpert120.yomu.R
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuColorPicker
import com.itexpert120.yomu.core.designsystem.YomuColorSwatch
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.AccentColor
import com.itexpert120.yomu.core.model.AccentSelection
import com.itexpert120.yomu.core.model.ThemePreference

@Composable
fun SettingsScreen(
    selectedTheme: ThemePreference,
    oledDark: Boolean,
    oledEnabled: Boolean,
    accentSelection: AccentSelection,
    accentIsDark: Boolean,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    onSelectAccent: (AccentColor) -> Unit,
    onSelectCustomAccent: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenReaderDefaults: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var showCustomPicker by remember { mutableStateOf(false) }
    YomuScreenScaffold(title = "Settings", onBack = onBack) {
        YomuSettingGroup(title = "Appearance") {
            Text(
                text = "Theme",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreference.entries.forEach { pref ->
                    ThemeOptionTile(
                        icon = pref.themeIcon(),
                        label = pref.label,
                        selected = pref == selectedTheme,
                        onClick = { onSelectTheme(pref) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            YomuSettingRow(
                title = "Pure black (OLED)",
                subtitle = if (oledEnabled) {
                    "Uses true black surfaces in dark mode"
                } else {
                    "Available when the theme is dark"
                },
            ) {
                YomuTogglePill(
                    checked = oledDark && oledEnabled,
                    onCheckedChange = onToggleOled,
                    enabled = oledEnabled,
                )
            }

            Text(
                text = "Accent",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
            val choices = AccentColor.entries.map { AccentChoice.Preset(it) } + AccentChoice.Custom
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.chunked(3).forEach { rowChoices ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowChoices.forEach { choice ->
                            when (choice) {
                                is AccentChoice.Preset -> YomuColorSwatch(
                                    name = choice.accent.label,
                                    color = Color(
                                        if (accentIsDark) choice.accent.dark else choice.accent.light,
                                    ),
                                    selected = accentSelection is AccentSelection.Preset &&
                                        accentSelection.accent == choice.accent,
                                    onClick = { onSelectAccent(choice.accent) },
                                    modifier = Modifier.weight(1f),
                                )

                                AccentChoice.Custom -> YomuColorSwatch(
                                    name = "Custom",
                                    color = if (accentSelection is AccentSelection.Custom) {
                                        Color(accentSelection.argb)
                                    } else {
                                        YomuTheme.colors.textMuted
                                    },
                                    selected = accentSelection is AccentSelection.Custom,
                                    onClick = { showCustomPicker = true },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        repeat(3 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        NavigationGroup {
            NavigationRow(
                icon = rememberVectorPainter(Icons.Rounded.Tune),
                label = "Reading defaults",
                onClick = onOpenReaderDefaults,
            )
            NavigationDivider()
            NavigationRow(
                icon = rememberVectorPainter(Icons.Rounded.Insights),
                label = "Statistics",
                onClick = onOpenStats,
            )
            NavigationDivider()
            NavigationRow(
                icon = painterResource(R.drawable.ic_yomu_mark),
                label = "About Yomu",
                onClick = onOpenAbout,
            )
        }

        Text(
            text = "Yomu v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            textAlign = TextAlign.Center,
        )
    }

    val pickerInitial = if (accentSelection is AccentSelection.Custom) {
        Color(accentSelection.argb)
    } else {
        Color(accentSelection.resolve(accentIsDark))
    }
    // scrollable=false: the colour picker owns its vertical drag gesture (would fight a parent scroll).
    YomuBottomSheet(
        visible = showCustomPicker,
        onDismiss = { showCustomPicker = false },
        scrollable = false,
    ) { dismiss ->
        Text(
            text = "Custom accent",
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.title,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        YomuColorPicker(
            color = pickerInitial,
            onColorChange = { onSelectCustomAccent(it.toArgb().toLong() and 0xFFFFFFFFL) },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .yomuPressable(onClick = dismiss)
                .clip(RoundedCornerShape(YomuTheme.radius.md))
                .background(YomuTheme.colors.surface)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Done", color = YomuTheme.colors.textSecondary, style = YomuTheme.type.body)
        }
    }
}

private sealed interface AccentChoice {
    data class Preset(val accent: AccentColor) : AccentChoice
    data object Custom : AccentChoice
}

private fun ThemePreference.themeIcon(): ImageVector = when (this) {
    ThemePreference.System -> Icons.Rounded.BrightnessAuto
    ThemePreference.Light -> Icons.Rounded.LightMode
    ThemePreference.Dark -> Icons.Rounded.DarkMode
}

@Composable
private fun ThemeOptionTile(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YomuTheme.colors
    Column(
        modifier = modifier
            .yomuPressable(onClick = onClick)
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(if (selected) colors.surfaceSunken else colors.surfaceRaised)
            .border(
                1.dp,
                if (selected) colors.textPrimary else colors.border,
                RoundedCornerShape(YomuTheme.radius.md),
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            style = YomuTheme.type.caption,
        )
    }
}

/** A single rounded card holding the settings navigation rows, separated by hairline dividers. */
@Composable
private fun NavigationGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.lg))
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg)),
        content = content,
    )
}

/** Full-width hairline divider between navigation rows. */
@Composable
private fun NavigationDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(YomuTheme.colors.border.copy(alpha = 0.6f)),
    )
}

@Composable
private fun NavigationRow(icon: Painter, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .yomuPressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Leading icon sits in a soft chip — a small, deliberate touch that reads more crafted.
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(YomuTheme.radius.sm))
                .background(YomuTheme.colors.surfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = YomuTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.body,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = YomuTheme.colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
