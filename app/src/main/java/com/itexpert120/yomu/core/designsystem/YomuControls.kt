package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

/**
 * Stack's buttons. [YomuButtonEmphasis.Primary] is the ink pill; [YomuButtonEmphasis.Secondary] the
 * sheet-coloured pill with a 1.5dp track border; [YomuButtonEmphasis.Ghost] an ink text button;
 * [YomuButtonEmphasis.Danger] a danger text button and [YomuButtonEmphasis.DangerFilled] the danger
 * pill used for a dialog's destructive confirm. [size] picks the height: 56 standalone, 52 in rows,
 * 44 in dialogs.
 */
@Composable
fun YomuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: YomuButtonEmphasis = YomuButtonEmphasis.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    size: YomuButtonSize = YomuButtonSize.Regular,
) {
    val stack = YomuTheme.stack
    val (container, content) = when (emphasis) {
        YomuButtonEmphasis.Primary -> stack.ink to stack.onInk
        YomuButtonEmphasis.Secondary -> stack.sheet to stack.ink
        YomuButtonEmphasis.Ghost -> Color.Transparent to stack.ink
        YomuButtonEmphasis.Danger -> Color.Transparent to stack.danger
        YomuButtonEmphasis.DangerFilled -> stack.danger to MaterialTheme.colorScheme.onError
    }
    val isText = emphasis == YomuButtonEmphasis.Ghost || emphasis == YomuButtonEmphasis.Danger
    // Text buttons are as tall as a touch target needs; pills take the size's full height.
    val height = if (isText) minOf(size.height, 48.dp) else size.height
    YomuPill(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = container,
        border = if (emphasis == YomuButtonEmphasis.Secondary) stack.track else null,
        height = height,
        horizontalPadding = if (isText) 12.dp else size.padding,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(if (size == YomuButtonSize.Small) 20.dp else 22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = if (size == YomuButtonSize.Small) StackType.ButtonSmall else StackType.Button,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class YomuButtonEmphasis { Primary, Secondary, Ghost, Danger, DangerFilled }

/** Button heights: 56 on their own, 52 inside rows, 44 in dialogs and dense spots. */
enum class YomuButtonSize(internal val height: Dp, internal val padding: Dp) {
    Regular(56.dp, 24.dp),
    InRow(52.dp, 22.dp),
    Small(44.dp, 18.dp),
}

/** The pill every Stack button is built on: subtle press, full-radius clip, optional 1.5dp border. */
@Composable
internal fun YomuPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color,
    border: Color? = null,
    height: Dp = 56.dp,
    horizontalPadding: Dp = 24.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .yomuPressable(onClick = onClick, enabled = enabled, role = Role.Button, shape = CircleShape)
            .background(container)
            .then(if (border != null) Modifier.border(1.5.dp, border, CircleShape) else Modifier)
            .defaultMinSize(minWidth = height, minHeight = height)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

/** Stack's icon button: a 48dp target holding a 24dp ink icon (weight 300). */
@Composable
fun YomuIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .yomuPressable(onClick = onClick, enabled = enabled, role = Role.Button, shape = CircleShape)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/**
 * Stack's chip/tag: 28dp, raised fill, radius 14, 12.5/600. Selected fills with ink, reads in
 * on-ink and gains a check. The touch target stays 48dp around the 28dp chip.
 */
@Composable
fun YomuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val stack = YomuTheme.stack
    val content = if (selected) stack.onInk else stack.body
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(StackChipShape)
                .yomuPressable(onClick = onClick, role = Role.Button, shape = StackChipShape)
                .background(if (selected) stack.ink else stack.raised)
                .height(28.dp)
                .padding(start = if (selected || icon != null) 8.dp else 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val leading = if (selected) StackIcons.Check else icon
            if (leading != null) Icon(leading, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Text(text = text, style = StackType.Chip, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val StackChipShape = RoundedCornerShape(14.dp)

@Composable
fun YomuSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tabs and exclusive choices share one control: the segmented pill.
    YomuSingleChoiceSegmentedControl(options, selectedIndex, onSelected, modifier)
}

/** Stack segmented choice for compact, mutually exclusive settings. */
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
 * Stack's segmented choice: a pill track in the raised colour with one sheet-coloured chip (a tiny
 * static shadow under it) marking the selected option. The chip glides between options on the
 * default spatial spring; colours cross-fade on the effects spring — all read while drawing, so a
 * selection change never recomposes per frame. Icons, when given, sit beside the label (or above
 * it with [iconOnTop], for groups with too many options to fit both inline).
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
    iconOnTop: Boolean = false,
) {
    if (options.isEmpty()) return
    val stack = YomuTheme.stack
    val haptics = LocalHapticFeedback.current
    val motion = yomuAnimationsEnabled()
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val position = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (motion) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
        label = "segmentPosition",
    )
    val chip = stack.sheet
    val shadow = Color.Black.copy(alpha = if (stack.isDark) 0.16f else 0.08f)
    val count = options.size
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(stack.raised)
            .drawBehind {
                val pad = SegmentTrackPadding.toPx()
                val w = (size.width - pad * 2) / count
                val h = size.height - pad * 2
                val x = pad + w * position.value
                val r = CornerRadius(minOf(h, w) / 2f)
                drawRoundRect(shadow, topLeft = Offset(x, pad + 1.dp.toPx()), size = Size(w, h), cornerRadius = r)
                drawRoundRect(chip, topLeft = Offset(x, pad), size = Size(w, h), cornerRadius = r)
            }
            .padding(SegmentTrackPadding)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val checked = index == selectedIndex
            val ink = stack.ink
            val muted = stack.muted
            // 1 when this option is under the chip, falling off as the chip glides away.
            val color = ColorProducer {
                val near = (1f - kotlin.math.abs(position.value - index)).coerceIn(0f, 1f)
                lerp(muted, ink, near)
            }
            val painter = icon?.let { rememberVectorPainter(it(option)) }
            val iconSlot: @Composable () -> Unit = {
                if (painter != null) {
                    Spacer(
                        Modifier.size(if (iconOnTop) 20.dp else 18.dp).drawBehind {
                            with(painter) { draw(size, colorFilter = ColorFilter.tint(color())) }
                        },
                    )
                }
            }
            val text: @Composable () -> Unit = {
                BasicText(
                    text = label(option),
                    style = (if (iconOnTop) MaterialTheme.typography.labelMedium else StackType.Label).copy(textAlign = TextAlign.Center),
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = (height - SegmentTrackPadding * 2).coerceAtLeast(32.dp))
                    .clip(CircleShape)
                    .yomuPressable(
                        onClick = {
                            if (!checked) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(option)
                        },
                        enabled = enabled,
                        role = Role.RadioButton,
                        shape = CircleShape,
                    )
                    .semantics { this.selected = checked }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (iconOnTop && painter != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        iconSlot()
                        Spacer(Modifier.height(4.dp))
                        text()
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        iconSlot()
                        text()
                    }
                }
            }
        }
    }
}

private val SegmentTrackPadding = 3.dp

/**
 * A springy "pop" when [selected] turns on: the element shrinks, tilts, and springs back on the
 * expressive fast spatial spec. Driven from the layer block, so the pop never recomposes the host.
 * Off when system animations are disabled. (Stack's own controls no longer use it.)
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
 * Multi-select chip for independent on/off options (footer items, screen behaviours), in the Stack
 * chip look: 28dp raised pill, 12.5/600 label; on, it fills with ink and reads in on-ink. The
 * leading slot always holds an icon so the chip never changes width: the option's [icon] while off,
 * which turns away as a check turns in when on. Every animated value is read while drawing.
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
    val swap = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (motion) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
        label = "chipSwap",
    )
    val stack = YomuTheme.stack
    val raised = stack.raised
    val ink = stack.ink
    val restContent = stack.body
    val selectedContent = stack.onInk
    val contentColor = ColorProducer { lerp(restContent, selectedContent, fill.value.coerceIn(0f, 1f)) }
    val optionPainter = rememberVectorPainter(icon)
    val checkPainter = rememberVectorPainter(StackIcons.Check)
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(StackChipShape)
                .yomuPressable(
                    onClick = {
                        haptics.performHapticFeedback(if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        onSelectedChange(!selected)
                    },
                    enabled = enabled,
                    role = Role.Checkbox,
                    shape = StackChipShape,
                )
                .semantics { toggleableState = ToggleableState(selected) }
                .drawBehind { drawRect(lerp(raised, ink, fill.value.coerceIn(0f, 1f))) }
                .height(28.dp)
                .padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier
                    .size(16.dp)
                    .drawBehind {
                        val p = swap.value.coerceIn(0f, 1f)
                        val tint = ColorFilter.tint(contentColor())
                        if (p < 1f) {
                            rotate(degrees = p * 90f) {
                                scale(1f - 0.5f * p) { with(optionPainter) { draw(size, alpha = 1f - p, colorFilter = tint) } }
                            }
                        }
                        if (p > 0f) {
                            rotate(degrees = (1f - p) * -90f) {
                                scale(0.5f + 0.5f * p) { with(checkPainter) { draw(size, alpha = p, colorFilter = tint) } }
                            }
                        }
                    },
            )
            Spacer(Modifier.width(4.dp))
            BasicText(
                text = label,
                style = StackType.Chip,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                style = StackType.Value,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            YomuIconButton(
                icon = StackIcons.Remove,
                contentDescription = "Decrease $label",
                onClick = { onValueChange((value - 0.05f).coerceIn(0f, 1f)) },
            )
            YomuSlider(
                value = value.coerceIn(0f, 1f),
                onValueChange = { onValueChange(it.coerceIn(0f, 1f)) },
                modifier = Modifier.weight(1f),
                steps = 19,
            )
            YomuIconButton(
                icon = StackIcons.Add,
                contentDescription = "Increase $label",
                onClick = { onValueChange((value + 0.05f).coerceIn(0f, 1f)) },
            )
        }
    }
}

/**
 * A choice tile showing a colour (theme pickers): sheet-coloured with a 1.5dp track border, the
 * selected one ringed in ink at 2dp.
 */
@Composable
fun YomuColorSwatch(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(shape)
            .yomuPressable(onClick = onClick, role = Role.RadioButton, shape = shape)
            .semantics {
                contentDescription = name
                this.selected = selected
            }
            .background(stack.sheet)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) stack.ink else stack.track, shape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, stack.track, CircleShape),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            color = stack.ink,
            style = StackType.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
