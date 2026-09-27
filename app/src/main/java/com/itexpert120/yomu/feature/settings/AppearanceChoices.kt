@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.itexpert120.yomu.feature.settings

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuColorPicker
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.rememberSelectionMorphShape
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.designsystem.yomuSeededColorScheme
import com.itexpert120.yomu.core.model.AccentColor
import com.itexpert120.yomu.core.model.AccentSelection
import com.itexpert120.yomu.core.model.ColorStyle
import com.itexpert120.yomu.core.model.ThemePreference

/** Light/dark mode as a connected button group — mutually exclusive options, not a switch. */
@Composable
internal fun ThemeChoiceRow(selectedTheme: ThemePreference, onSelectTheme: (ThemePreference) -> Unit) {
    YomuConnectedChoiceGroup(
        options = ThemePreference.entries,
        height = ButtonDefaults.MediumContainerHeight,
        selected = selectedTheme,
        label = { it.label },
        icon = {
            when (it) {
                ThemePreference.System -> Icons.Rounded.BrightnessAuto
                ThemePreference.Light -> Icons.Rounded.LightMode
                ThemePreference.Dark -> Icons.Rounded.DarkMode
            }
        },
        onSelect = onSelectTheme,
    )
}

@Composable
internal fun ColorStyleRow(
    selected: ColorStyle,
    enabled: Boolean,
    onSelect: (ColorStyle) -> Unit,
) {
    YomuConnectedChoiceGroup(
        options = ColorStyle.entries,
        selected = selected,
        label = { it.label },
        enabled = enabled,
        onSelect = onSelect,
    )
}

/**
 * App colour theme picker: wallpaper palette (Android 12+), seeded presets, and a custom seed.
 * Each swatch previews the primary/secondary/tertiary roles the seed would generate; the selected
 * swatch morphs from a circle into a scalloped cookie.
 */
@Composable
internal fun ColorThemePicker(
    selection: AccentSelection,
    style: ColorStyle,
    onSelect: (AccentSelection) -> Unit,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val wallpaperAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var customDialogOpen by rememberSaveable { mutableStateOf(false) }

    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (wallpaperAvailable) {
            val scheme = remember(dark) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            ColorSwatch(
                label = "Wallpaper",
                scheme = scheme,
                selected = selection == AccentSelection.Wallpaper,
                badge = Icons.Rounded.Wallpaper,
                onClick = { onSelect(AccentSelection.Wallpaper) },
            )
        }
        AccentColor.entries.forEach { accent ->
            val scheme = remember(accent, dark, style) { yomuSeededColorScheme(accent.seed, dark, style) }
            ColorSwatch(
                label = accent.label,
                scheme = scheme,
                selected = selection == AccentSelection.Preset(accent),
                onClick = { onSelect(AccentSelection.Preset(accent)) },
            )
        }
        val custom = selection as? AccentSelection.Custom
        val customScheme = remember(custom, dark, style) {
            custom?.let { yomuSeededColorScheme(it.argb, dark, style) }
        }
        ColorSwatch(
            label = "Custom",
            scheme = customScheme,
            selected = custom != null,
            badge = Icons.Rounded.Colorize,
            onClick = { customDialogOpen = true },
        )
    }

    if (customDialogOpen) {
        CustomSeedDialog(
            initial = (selection as? AccentSelection.Custom)?.argb?.let { Color(it) }
                ?: MaterialTheme.colorScheme.primary,
            onDismiss = { customDialogOpen = false },
            onApply = {
                customDialogOpen = false
                onSelect(AccentSelection.Custom(it.toArgb().toLong() and 0xFFFFFFFFL))
            },
        )
    }
}

@Composable
private fun ColorSwatch(
    label: String,
    scheme: ColorScheme?,
    selected: Boolean,
    onClick: () -> Unit,
    badge: ImageVector? = null,
) {
    val haptics = LocalHapticFeedback.current
    val shape = rememberSelectionMorphShape(
        selected = selected,
        unselected = MaterialShapes.Circle,
        selectedPolygon = MaterialShapes.Cookie9Sided,
    )
    Column(
        modifier = Modifier
            .width(68.dp)
            .yomuPressable(
                onClick = {
                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
                role = Role.RadioButton,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$label colour theme"
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(shape)
                .background(scheme?.surfaceContainerHighest ?: MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (scheme != null) {
                Canvas(Modifier.fillMaxSize()) {
                    // Top half primary, bottom quarters secondary + tertiary — the tonal roles
                    // this seed produces, echoing Android's wallpaper-style picker.
                    drawRect(scheme.primary, size = Size(size.width, size.height / 2f))
                    drawRect(
                        scheme.secondaryContainer,
                        topLeft = Offset(0f, size.height / 2f),
                        size = Size(size.width / 2f, size.height / 2f),
                    )
                    drawRect(
                        scheme.tertiary,
                        topLeft = Offset(size.width / 2f, size.height / 2f),
                        size = Size(size.width / 2f, size.height / 2f),
                    )
                }
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFFE8674A),
                                Color(0xFFE0A030),
                                Color(0xFF3B7D2E),
                                Color(0xFF00897B),
                                Color(0xFF1F6FEB),
                                Color(0xFF6750A4),
                                Color(0xFFD1487A),
                                Color(0xFFE8674A),
                            ),
                        ),
                    ),
                )
            }
            val centerIcon = if (selected) Icons.Rounded.Check else badge
            if (centerIcon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = centerIcon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun CustomSeedDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit,
) {
    var color by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Colorize, contentDescription = null) },
        title = { Text("Custom colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Pick a seed. Open Reader builds a full light and dark palette from it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                YomuColorPicker(color = color, onColorChange = { color = it })
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(color),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(color) }, shapes = ButtonDefaults.shapes()) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
        },
    )
}
