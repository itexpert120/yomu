package com.itexpert120.yomu.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackColors
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.model.ThemePreference

/** Stack names the follow-the-system mode "Auto". */
internal val ThemePreference.stackLabel: String
    get() = when (this) {
        ThemePreference.System -> "Auto"
        ThemePreference.Light -> "Light"
        ThemePreference.Dark -> "Dark"
    }

/** Light, Dark, Auto: the order the choices read in. */
private val ThemeOrder = listOf(ThemePreference.Light, ThemePreference.Dark, ThemePreference.System)

/** Pure black applies only once the app is dark, so it's offered with Dark and Auto. */
internal fun ThemePreference.allowsPureBlack(): Boolean = this != ThemePreference.Light

/** Settings → Theme: the three modes as miniature screens, and the pure-black switch under them. */
@Composable
internal fun ThemeSheet(
    visible: Boolean,
    selectedTheme: ThemePreference,
    oledDark: Boolean,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { dismiss ->
        Column {
            YomuSheetHeader(title = "Theme", onClose = dismiss)
            AppearanceControls(
                selectedTheme = selectedTheme,
                oledDark = oledDark,
                onSelectTheme = onSelectTheme,
                onToggleOled = onToggleOled,
            )
        }
    }
}

/** The theme options plus the pure-black row; the body of the Theme sheet and the tablet pane. */
@Composable
internal fun AppearanceControls(
    selectedTheme: ThemePreference,
    oledDark: Boolean,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleOled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    Column(modifier) {
        Row(
            modifier = Modifier
                .padding(top = 6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeOrder.forEach { theme ->
                ThemeOption(
                    theme = theme,
                    selected = theme == selectedTheme,
                    onClick = { onSelectTheme(theme) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = "Auto follows your phone’s dark theme.",
            style = StackType.Label.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
            color = stack.muted,
            modifier = Modifier.padding(top = 12.dp),
        )
        val allowed = selectedTheme.allowsPureBlack()
        Box(
            Modifier
                .padding(top = 14.dp)
                .drawBehind { drawLine(stack.rule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) },
        ) {
            SettingsSwitchRow(
                title = "Pure black at night",
                subtitle = if (allowed) "For OLED screens" else "Available with the Dark or Auto theme",
                icon = StackIcons.DarkMode,
                checked = oledDark && allowed,
                enabled = allowed,
                onCheckedChange = onToggleOled,
                divider = false,
            )
        }
    }
}

@Composable
private fun ThemeOption(
    theme: ThemePreference,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(stack.sheet)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) stack.ink else stack.track, shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeMiniature(theme)
        Text(text = theme.stackLabel, style = StackType.Label, color = stack.ink)
    }
}

/** A tiny screen in that mode's own colours: a title bar, a sheet, a line of text. Auto is split. */
@Composable
private fun ThemeMiniature(theme: ThemePreference) {
    val light = StackColors.Light
    val dark = StackColors.Dark
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                when (theme) {
                    ThemePreference.Light -> drawRect(light.desk)
                    ThemePreference.Dark -> drawRect(dark.desk)
                    ThemePreference.System -> {
                        drawRect(light.desk, size = Size(size.width / 2, size.height))
                        drawRect(dark.desk, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
                    }
                }
            }
            .padding(horizontal = 8.dp, vertical = 9.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            MiniBar(theme, fraction = 0.6f, lightColor = light.ink, darkColor = dark.ink, height = 5)
            MiniBar(theme, fraction = 1f, lightColor = light.sheet, darkColor = dark.sheet, height = 40, radius = 6)
            MiniBar(theme, fraction = 0.8f, lightColor = light.track, darkColor = dark.track, height = 5)
        }
    }
}

@Composable
private fun MiniBar(
    theme: ThemePreference,
    fraction: Float,
    lightColor: Color,
    darkColor: Color,
    height: Int,
    radius: Int = 3,
) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height.dp)
            .clip(RoundedCornerShape(radius.dp))
            .drawBehind {
                when (theme) {
                    ThemePreference.Light -> drawRect(lightColor)
                    ThemePreference.Dark -> drawRect(darkColor)
                    ThemePreference.System -> {
                        // Split on the miniature's centre line (the bar starts at its 8dp inset).
                        val split = (size.width / fraction / 2f).coerceAtMost(size.width)
                        drawRect(lightColor, size = Size(split, size.height))
                        drawRect(darkColor, topLeft = Offset(split, 0f), size = Size(size.width - split, size.height))
                    }
                }
            },
    )
}

@Preview(widthDp = 390)
@Composable
private fun AppearanceControlsPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Light) {
        Column(Modifier.background(YomuTheme.stack.sheet).padding(24.dp)) {
            AppearanceControls(ThemePreference.Light, oledDark = false, onSelectTheme = {}, onToggleOled = {})
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun AppearanceControlsDarkPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
        Column(Modifier.background(YomuTheme.stack.sheet).padding(24.dp)) {
            AppearanceControls(ThemePreference.System, oledDark = true, onSelectTheme = {}, onToggleOled = {})
        }
    }
}
