package com.itexpert120.yomu.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.model.ThemePreference

@Composable
fun SettingsScreen(
    selectedTheme: ThemePreference,
    oledDark: Boolean,
    oledEnabled: Boolean,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
) {
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
        }

        NavigationRow(icon = Icons.Rounded.Info, label = "About Yomu", onClick = onOpenAbout)
    }
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
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(if (selected) colors.surfaceSunken else colors.surfaceRaised)
            .border(
                1.dp,
                if (selected) colors.textPrimary else colors.border,
                RoundedCornerShape(YomuTheme.radius.md),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
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

@Composable
private fun NavigationRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.lg))
            .background(YomuTheme.colors.surfaceRaised)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = YomuTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
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
