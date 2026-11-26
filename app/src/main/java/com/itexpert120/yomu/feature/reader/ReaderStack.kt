package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.ReaderSettings
import kotlin.math.roundToInt

/**
 * Stack's reader chrome takes the page colour as its surface: every chrome colour is derived from
 * the reading theme, so Paper, Sepia, Night, Black and custom pages each get matching bars.
 */
@Immutable
internal data class ReaderChromeColors(
    /** The page, and the top bar / footer surface. */
    val page: Color,
    /** The controls panel: the page itself on light pages, one faint step up on dark ones. */
    val panel: Color,
    /** Titles, icons and the slider fill. */
    val ink: Color,
    /** Captions, inactive actions and footer text. */
    val muted: Color,
    /** The hairline under the top bar and above the panel. */
    val rule: Color,
    /** Slider track. */
    val track: Color,
    val isLight: Boolean,
)

internal fun ReaderSettings.chromeColors(): ReaderChromeColors {
    val page = Color(backgroundArgb)
    val ink = Color(textArgb)
    val light = isLightBackground
    return ReaderChromeColors(
        page = page,
        // Dark pages (Night, Black) need clear steps: the panel visibly above the page, and the
        // rule and slider track readable on it — a near-black panel and track vanished on Black.
        panel = if (light) page else lerp(page, ink, 0.12f),
        ink = ink,
        muted = Color(colorPalette.secondaryTextArgb),
        rule = if (light) Color(colorPalette.borderArgb) else lerp(page, ink, 0.18f),
        track = lerp(page, ink, if (light) 0.13f else 0.3f),
        isLight = light,
    )
}

/** Stack's tabular figures, for numbers that tick (clock, battery, percentages). */
internal val ReaderFigures: TextStyle = StackType.Caption.copy(fontFeatureSettings = "tnum")

/** The bundled Lora, for reading-text previews in sheets (highlights, notes, search context). */
@Composable
internal fun rememberReadingSerif(): FontFamily {
    val assets = LocalContext.current.assets
    return remember(assets) {
        FontFamily(Font("fonts/Lora-Regular.ttf", assets), Font("fonts/Lora-Italic.ttf", assets, style = FontStyle.Italic))
    }
}

/** A sheet's section label: Stack's 13/600 muted, sentence case. */
@Composable
internal fun ReaderSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = YomuTheme.stack.muted,
        style = StackType.Label,
        modifier = modifier,
    )
}

/** A full-bleed 1dp rule. */
@Composable
internal fun ReaderRule(modifier: Modifier = Modifier, color: Color = YomuTheme.stack.rule) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/**
 * Stack's slider in explicit colours (4dp track, ink fill, 6×24 handle), for chrome drawn in the
 * page's colours. The drag position is held here and read only while drawing; [onDrag] reports it
 * and [onSeek] fires once on release. Accessible as an adjustable range.
 */
@Composable
internal fun ReaderChromeSlider(
    fraction: Float,
    onSeek: (Float) -> Unit,
    fill: Color,
    track: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
    enabled: Boolean = true,
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(fraction) }
    val shown = { if (dragging) dragFraction else fraction.coerceIn(0f, 1f) }
    Box(
        modifier = modifier
            .height(28.dp)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription?.let { this.stateDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                if (enabled) {
                    setProgress { value ->
                        currentOnSeek(value.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                currentOnSeek((offset.x / size.width).coerceIn(0f, 1f))
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    dragging = true
                                    dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    currentOnDrag?.invoke(dragFraction)
                                },
                                onDragEnd = {
                                    dragging = false
                                    currentOnSeek(dragFraction)
                                    currentOnDragEnd?.invoke()
                                },
                                onDragCancel = {
                                    dragging = false
                                    currentOnDragEnd?.invoke()
                                },
                            ) { change, amount ->
                                change.consume()
                                dragFraction = (dragFraction + amount / size.width).coerceIn(0f, 1f)
                                currentOnDrag?.invoke(dragFraction)
                            }
                        }
                } else {
                    Modifier
                },
            )
            .drawBehind {
                val trackHeight = 4.dp.toPx()
                val handleWidth = 6.dp.toPx()
                val handleHeight = 24.dp.toPx()
                val y = size.height / 2
                val x = shown() * size.width
                drawRoundRect(track, Offset(0f, y - trackHeight / 2), Size(size.width, trackHeight), CornerRadius(trackHeight / 2))
                drawRoundRect(fill, Offset(0f, y - trackHeight / 2), Size(x, trackHeight), CornerRadius(trackHeight / 2))
                drawRoundRect(
                    fill,
                    Offset((x - handleWidth / 2).coerceIn(0f, size.width - handleWidth), y - handleHeight / 2),
                    Size(handleWidth, handleHeight),
                    CornerRadius(handleWidth / 2),
                )
            },
    )
}

/** A 2dp indeterminate line — Stack's only loading indicator besides skeletons. */
@Composable
internal fun ReaderLoadingLine(color: Color, track: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier.height(2.dp),
        color = color,
        trackColor = track,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
    )
}

/** Stack's pill buttons, in explicit colours so they also work on a reading-theme page. */
internal enum class ReaderPillStyle { Primary, Secondary }

@Composable
internal fun ReaderPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ReaderPillStyle = ReaderPillStyle.Primary,
    icon: ImageVector? = null,
    ink: Color = YomuTheme.stack.ink,
    surface: Color = YomuTheme.stack.sheet,
    border: Color = YomuTheme.stack.track,
    height: Dp = 48.dp,
    enabled: Boolean = true,
) {
    val primary = style == ReaderPillStyle.Primary
    val container = if (primary) ink else surface
    val content = if (primary) surface else ink
    Row(
        modifier = modifier
            .heightIn(min = height)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(CircleShape)
            .yomuPressable(onClick = onClick, enabled = enabled, shape = CircleShape, pressColor = content)
            .background(container)
            .then(if (primary) Modifier else Modifier.border(BorderStroke(1.5.dp, border), CircleShape))
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text = text, color = content, style = StackType.Heading, maxLines = 1)
    }
}

/** A dashed outline (Stack's "add" and "custom" affordances), drawn behind the content. */
internal fun Modifier.readerDashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/**
 * Stack's choice chip: 36 tall, a 1.5dp track outline on the sheet, filled ink with on-ink text when
 * [selected] (with a check when [showCheck]). [role] sets whether it reads as a toggle or a radio.
 */
@Composable
internal fun ReaderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Checkbox,
    showCheck: Boolean = false,
    leadingIcon: ImageVector? = null,
    dashed: Boolean = false,
    labelStyle: TextStyle = StackType.Label,
) {
    val stack = YomuTheme.stack
    val content = if (selected) stack.onInk else stack.body
    val icon = if (selected && showCheck) StackIcons.Check else leadingIcon
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clip(CircleShape)
            .background(if (selected) stack.ink else stack.sheet)
            .then(
                when {
                    selected -> Modifier
                    dashed -> Modifier.readerDashedBorder(stack.track, 18.dp)
                    else -> Modifier.border(1.5.dp, stack.track, CircleShape)
                },
            )
            .then(
                if (role == Role.Checkbox) {
                    Modifier.toggleable(value = selected, enabled = enabled, role = role, onValueChange = { onClick() })
                } else {
                    Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
                },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(text = label, color = content, style = labelStyle, maxLines = 1)
    }
}

/** Percent text helper. */
internal fun Float.asPercent(): String = "${(this * 100).roundToInt()}%"
