package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

@Composable
fun YomuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: YomuButtonEmphasis = YomuButtonEmphasis.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val buttonHeight = ButtonDefaults.MinHeight
    val label: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(text = text, style = ButtonDefaults.textStyleFor(buttonHeight))
    }
    when (emphasis) {
        YomuButtonEmphasis.Primary -> Button(
            enabled = enabled,
            onClick = onClick,
            modifier = modifier,
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            content = label,
        )

        YomuButtonEmphasis.Secondary -> FilledTonalButton(
            enabled = enabled,
            onClick = onClick,
            modifier = modifier,
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            content = label,
        )

        YomuButtonEmphasis.Ghost -> TextButton(
            enabled = enabled,
            onClick = onClick,
            modifier = modifier,
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            content = label,
        )
    }
}

enum class YomuButtonEmphasis { Primary, Secondary, Ghost }

@Composable
fun YomuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
        ),
    )
}

@Composable
fun YomuSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tabs and exclusive choices share one control: the connected button group.
    YomuSingleChoiceSegmentedControl(options, selectedIndex, onSelected, modifier)
}

/** Material 3 segmented choice control for compact, mutually exclusive settings. */
@Composable
fun YomuSingleChoiceSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val selected = selectedIndex.coerceIn(0, options.lastIndex)
    YomuConnectedChoiceGroup(
        options = options.indices.toList(),
        selected = selected,
        label = { options[it] },
        onSelect = onSelected,
        modifier = modifier,
    )
}

/**
 * M3 Expressive connected button group for mutually exclusive options (the replacement for
 * segmented buttons). The selected toggle morphs to its square selected shape and takes the
 * emphasized label style, so selection reads from shape and weight as well as colour.
 */
@Composable
fun <T> YomuConnectedChoiceGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
    enabled: Boolean = true,
    height: Dp = ButtonDefaults.MinHeight,
    // Stacks the icon above a smaller label, for groups with too many options to fit both inline.
    iconOnTop: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(option)
                },
                enabled = enabled,
                shapes = when {
                    options.size == 1 -> ToggleButtonDefaults.shapesFor(height)
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = height)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = checked
                    },
            ) {
                val optionIcon: @Composable () -> Unit = {
                    if (icon != null) {
                        Icon(
                            icon(option),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp).yomuSelectionPop(checked),
                        )
                    }
                }
                if (iconOnTop && icon != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        optionIcon()
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = label(option),
                            style = if (checked) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    if (icon != null) {
                        optionIcon()
                        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    }
                    Text(
                        text = label(option),
                        style = if (checked) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A springy "pop" when [selected] turns on: the element shrinks, tilts, and springs back on the
 * expressive fast spatial spec (whose overshoot gives the bounce). Driven from the layer block, so
 * the pop never recomposes the host. Off when system animations are disabled.
 */
@Composable
fun Modifier.yomuSelectionPop(selected: Boolean): Modifier {
    val pop = remember { Animatable(1f) }
    // The last seen value, so the first composition never pops.
    val last = remember { booleanArrayOf(selected) }
    val motion = yomuAnimationsEnabled()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(selected) {
        val turnedOn = selected && !last[0]
        last[0] = selected
        if (turnedOn && motion) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spec)
        } else if (pop.value != 1f) {
            pop.snapTo(1f)
        }
    }
    return graphicsLayer {
        val scale = pop.value
        scaleX = scale
        scaleY = scale
        rotationZ = (1f - scale) * -30f
    }
}

/**
 * Multi-select chip for independent on/off options (footer items, screen behaviours). The leading
 * slot always holds an icon so the chip never changes width: the option's [icon] while off, which
 * spins and shrinks away as a check springs in when on. The outlined pill fills with the secondary
 * container and squares its corners as it turns on, echoing the toggle-button morph. Every animated
 * value is read in the draw/layer phase, so toggling never recomposes the chip per frame.
 */
@Composable
fun YomuSelectableChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val motion = yomuAnimationsEnabled()
    val fill = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (motion) MaterialTheme.motionScheme.defaultEffectsSpec() else snap(),
        label = "chipFill",
    )
    val morph = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (motion) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
        label = "chipMorph",
    )
    val colors = MaterialTheme.colorScheme
    val disabledAlpha = if (enabled) 1f else 0.38f
    val container = colors.secondaryContainer
    val outline = if (enabled) colors.outlineVariant else colors.onSurface.copy(alpha = 0.12f)
    val restContent = colors.onSurfaceVariant
    val selectedContent = colors.onSecondaryContainer
    val contentColor = ColorProducer {
        val c = lerp(restContent, selectedContent, fill.value.coerceIn(0f, 1f))
        c.copy(alpha = c.alpha * disabledAlpha)
    }
    val optionPainter = rememberVectorPainter(icon)
    val checkPainter = rememberVectorPainter(Icons.Rounded.Check)
    val haptics = LocalHapticFeedback.current
    val selectedCorner = with(LocalDensity.current) { 8.dp.toPx() }

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .yomuPressable(
                onClick = {
                    haptics.performHapticFeedback(if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onSelectedChange(!selected)
                },
                enabled = enabled,
                role = Role.Checkbox,
            )
            .semantics { toggleableState = ToggleableState(selected) }
            .graphicsLayer {
                shape = RoundedCornerShape(lerp(size.height / 2f, selectedCorner, morph.value.coerceIn(0f, 1f)))
                clip = true
            }
            .drawBehind {
                val f = fill.value.coerceIn(0f, 1f)
                if (f > 0f) drawRect(container.copy(alpha = container.alpha * f * disabledAlpha))
                if (f < 1f) {
                    val stroke = 1.dp.toPx()
                    val radius = lerp(size.height / 2f, selectedCorner, morph.value.coerceIn(0f, 1f))
                    drawRoundRect(
                        color = outline.copy(alpha = outline.alpha * (1f - f)),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(radius - stroke / 2f),
                        style = Stroke(stroke),
                    )
                }
            }
            .heightIn(min = 32.dp)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(FilterChipDefaults.IconSize)
                .drawBehind {
                    val p = morph.value
                    val tint = ColorFilter.tint(contentColor())
                    // The option icon spins out one way as the check springs in from the other.
                    val outgoing = (1f - p).coerceIn(0f, 1f)
                    if (outgoing > 0f) {
                        rotate(degrees = p * 90f) {
                            scale(0.5f + 0.5f * outgoing) {
                                with(optionPainter) { draw(size, alpha = outgoing, colorFilter = tint) }
                            }
                        }
                    }
                    val incoming = p.coerceIn(0f, 1f)
                    if (incoming > 0f) {
                        rotate(degrees = (1f - p) * -90f) {
                            scale(p.coerceAtLeast(0f)) {
                                with(checkPainter) { draw(size, alpha = incoming, colorFilter = tint) }
                            }
                        }
                    }
                },
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun YomuRangeRow(
    label: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange((value - 0.05f).coerceIn(0f, 1f)) },
                modifier = Modifier.semantics {
                    contentDescription = "Decrease $label"
                },
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = null)
            }
            YomuSlider(
                value = value.coerceIn(0f, 1f),
                onValueChange = { onValueChange(it.coerceIn(0f, 1f)) },
                modifier = Modifier.weight(1f),
                steps = 19,
            )
            IconButton(
                onClick = { onValueChange((value + 0.05f).coerceIn(0f, 1f)) },
                modifier = Modifier.semantics {
                    contentDescription = "Increase $label"
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    }
}

@Composable
fun YomuColorSwatch(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .yomuPressable(onClick = onClick, role = Role.RadioButton)
            .semantics {
                contentDescription = name
                this.selected = selected
            }
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = MaterialTheme.shapes.medium,
            )
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.Black.copy(alpha = 0.18f), CircleShape),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
