package com.itexpert120.yomu.feature.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.InstrumentSansCondensed
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.stackCoverShadow
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.model.ReadingStats
import java.io.File

/** Metric values: Title size on the condensed face, so long times still fit two to a row. */
internal val StatsValueStyle: TextStyle = StackType.Title.copy(fontFamily = InstrumentSansCondensed)

/** The tablet's larger hero figure. */
internal val StatsWideFigureStyle: TextStyle = StackType.Figure.copy(fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-2.4 / 64).em)

internal val ThumbShape = RoundedCornerShape(3.dp)

private val MaxBarWidth = 64.dp

internal val ReadingStats.hasActivity: Boolean
    get() = sessionCount != 0 || totalReadingSeconds != 0L

/**
 * The screen title and the period choice beside it, on the desk. With large text the choice
 * moves under the title rather than squeezing it.
 */
@Composable
internal fun StatsTitleRow(
    onBack: (() -> Unit)?,
    period: StatsPeriod?,
    onPeriodChange: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
    startPadding: Dp = YomuTheme.space.lg,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) YomuTheme.space.xxs else startPadding, end = YomuTheme.space.md, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(StackIcons.ArrowBack, contentDescription = "Back", tint = YomuTheme.stack.ink)
            }
        }
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Statistics",
                style = StackType.Display,
                color = YomuTheme.stack.ink,
                modifier = Modifier
                    .padding(end = YomuTheme.space.sm)
                    .semantics { heading() },
            )
            if (period != null) {
                PeriodPicker(selected = period, onSelect = onPeriodChange)
            }
        }
    }
}

/**
 * Stack's segmented choice on the desk: a track pill with a sheet-coloured chip under the chosen
 * period. The chip glides between options on the default spatial spring, read in the draw phase.
 */
@Composable
internal fun PeriodPicker(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
    // The compact header is 52dp tall; the full-size picker would nearly touch its edges.
    compact: Boolean = false,
) {
    val stack = YomuTheme.stack
    val options = StatsPeriod.entries
    val haptics = LocalHapticFeedback.current
    val position by animateFloatAsState(
        targetValue = options.indexOf(selected).toFloat(),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "periodChip",
    )
    // Each option is as wide as its word; the chip interpolates between their measured bounds.
    val starts = remember { FloatArray(options.size) }
    val widths = remember { FloatArray(options.size) }
    // Above the track in every theme (a sheet-coloured chip would sink below it in the dark).
    val chip = stack.lifted
    val shadow = Color.Black.copy(alpha = if (stack.isDark) 0.24f else 0.08f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (stack.isDark) stack.raised else stack.track.copy(alpha = 0.6f))
            .padding(if (compact) 2.dp else 3.dp)
            .drawBehind {
                val from = position.toInt().coerceIn(0, options.lastIndex)
                val to = (from + 1).coerceAtMost(options.lastIndex)
                val t = position - from
                val left = starts[from] + (starts[to] - starts[from]) * t
                val width = widths[from] + (widths[to] - widths[from]) * t
                if (width <= 0f) return@drawBehind
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(shadow, topLeft = Offset(left, 1.dp.toPx()), size = Size(width, size.height), cornerRadius = radius)
                drawRoundRect(chip, topLeft = Offset(left, 0f), size = Size(width, size.height), cornerRadius = radius)
            }
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .onPlaced {
                        starts[index] = it.positionInParent().x
                        widths[index] = it.size.width.toFloat()
                    }
                    .heightIn(min = if (compact) 32.dp else 42.dp)
                    .clip(RoundedCornerShape(50))
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = {
                            if (!isSelected) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(option)
                            }
                        },
                    )
                    .padding(horizontal = if (compact) 12.dp else 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = if (compact) 13.sp else 14.sp),
                    color = if (isSelected) stack.ink else stack.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

private val StatsPeriod.label: String
    get() = when (this) {
        StatsPeriod.Week -> "Week"
        StatsPeriod.Month -> "Month"
    }

/** The one figure: reading time for the period, with what it covers underneath. */
@Composable
internal fun HeroFigure(summary: PeriodSummary, wide: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = summary.spoken },
    ) {
        Text(
            text = summary.figure,
            style = if (wide) StatsWideFigureStyle else StackType.Figure,
            color = YomuTheme.stack.ink,
            maxLines = 1,
        )
        Text(
            text = summary.caption,
            style = if (wide) StackType.Body else MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = YomuTheme.stack.body,
            modifier = Modifier.padding(top = if (wide) 8.dp else 6.dp),
        )
    }
}

/**
 * Minutes per day as bars on the desk: sheet-coloured bars, an ink bar for today, a track sliver
 * for days without reading, nothing yet for days still to come. Bars grow to their height on the
 * spatial spring, read in the draw phase. A [dense] chart (a month) labels one bar a week.
 */
@Composable
internal fun TrendChart(
    bars: List<TrendBar>,
    heading: String,
    dense: Boolean,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    val max = bars.maxOfOrNull { it.seconds }?.coerceAtLeast(1L) ?: 1L
    val chartHeight = if (wide) 180.dp else 118.dp
    val gap = when {
        dense -> 3.dp
        wide -> 14.dp
        else -> 8.dp
    }
    val barColor = if (stack.isDark) stack.track else stack.sheet
    val emptyColor = if (stack.isDark) stack.rule else stack.track
    val description = remember(bars, heading, dense) { chartDescription(bars, heading, dense) }
    val animate = yomuAnimationsEnabled()
    val growSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight - 24.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bar ->
                val target = bar.seconds.toFloat() / max
                // Bars grow up from the baseline when the chart first shows, then follow new data.
                val fraction = remember { Animatable(if (animate) 0f else target) }
                LaunchedEffect(target) { fraction.animateTo(target, growSpec) }
                val color = when {
                    bar.isCurrent && bar.seconds > 0 -> stack.ink
                    bar.seconds == 0L -> emptyColor
                    else -> barColor
                }
                val radius = if (dense) {
                    3.dp
                } else if (wide) {
                    10.dp
                } else {
                    8.dp
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            if (bar.isFuture) return@drawBehind
                            val minHeight = 4.dp.toPx()
                            val shown = if (bar.seconds == 0L) minHeight else (size.height * fraction.value).coerceAtLeast(minHeight)
                            val r = radius.toPx().coerceAtMost(shown / 2f).coerceAtLeast(2.dp.toPx())
                            // Wide panes cap a bar at 64dp, centred in its slot.
                            val width = size.width.coerceAtMost(MaxBarWidth.toPx())
                            drawRoundRect(
                                color = color,
                                topLeft = Offset((size.width - width) / 2f, size.height - shown),
                                size = Size(width, shown),
                                cornerRadius = CornerRadius(r, r),
                            )
                        },
                )
            }
        }
        TrendLabels(bars = bars, wide = wide, dense = dense, gap = gap)
    }
}

@Composable
private fun TrendLabels(bars: List<TrendBar>, wide: Boolean, dense: Boolean, gap: Dp) {
    val stack = YomuTheme.stack
    if (dense) {
        // Too many bars for a label each: one label a week, centred under its bar.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                bars.forEach { bar ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (bar.showLabel) {
                            Text(
                                text = bar.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = labelColor(bar),
                                fontWeight = if (bar.isCurrent) FontWeight.Bold else null,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.wrapContentWidthUnbounded(),
                            )
                        }
                    }
                }
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            bars.forEach { bar ->
                Text(
                    text = if (wide) bar.label else bar.shortLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor(bar),
                    fontWeight = if (bar.isCurrent) FontWeight.Bold else null,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun labelColor(bar: TrendBar): Color {
    val stack = YomuTheme.stack
    return when {
        bar.isCurrent -> stack.ink
        bar.isFuture -> stack.faint
        else -> stack.muted
    }
}

/** Lets a label be wider than its bar's slot (it overhangs both neighbours, centred). */
private fun Modifier.wrapContentWidthUnbounded(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val width = constraints.maxWidth
        layout(width, placeable.height) {
            placeable.place((width - placeable.width) / 2, 0)
        }
    },
)

private fun chartDescription(bars: List<TrendBar>, heading: String, dense: Boolean): String {
    val past = bars.filter { !it.isFuture }
    // Thirty bars read aloud one by one is noise; the month chart names only the days with reading.
    val spoken = if (dense) past.filter { it.seconds > 0 } else past
    if (spoken.isEmpty() || spoken.all { it.seconds == 0L }) return "$heading: no reading"
    return heading + ": " + spoken.joinToString(", ") { "${it.spokenLabel} ${speakReadingTime(it.seconds)}" }
}

/** One fact: a big value with its caption underneath. */
@Composable
internal fun StatFact(value: String, label: String, modifier: Modifier = Modifier, valueStyle: TextStyle = StatsValueStyle) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(text = value, style = valueStyle, color = YomuTheme.stack.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = YomuTheme.stack.muted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

internal data class StatsMetric(val value: String, val label: String)

/** The three headline facts that sit under the figure. */
internal fun ReadingStats.headlineFacts(): List<StatsMetric> = listOf(
    StatsMetric(formatDays(currentStreakDays), "streak · best $longestStreakDays"),
    StatsMetric(booksFinishedThisYear.toString(), "finished this year"),
    StatsMetric(daysRead.toString(), if (daysRead == 1) "day with reading" else "days with reading"),
)

internal fun ReadingStats.detailMetrics(): List<StatsMetric> = listOf(
    StatsMetric(formatReadingTime(totalReadingSeconds), "in total"),
    StatsMetric(formatReadingTime(longestSessionSeconds), "longest session"),
    StatsMetric(sessionCount.toString(), if (sessionCount == 1) "reading session" else "reading sessions"),
    StatsMetric(chaptersRead.toString(), if (chaptersRead == 1) "chapter read" else "chapters read"),
)

@Composable
internal fun FactsRow(stats: ReadingStats, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
        stats.headlineFacts().forEach { fact ->
            StatFact(fact.value, fact.label, Modifier.weight(1f))
        }
    }
}

/** The tablet's facts: each one a sheet card on the desk. */
@Composable
internal fun FactCards(stats: ReadingStats, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
    ) {
        stats.headlineFacts().forEach { fact ->
            StatFact(
                value = fact.value,
                label = fact.label,
                valueStyle = StatsValueStyle.copy(fontSize = 30.sp, lineHeight = 32.sp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.large)
                    .background(YomuTheme.stack.sheet)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }
    }
}

/** Every other figure, as a ruled grid: value over caption, a rule under each cell. */
@Composable
internal fun ReadingDetailsGrid(stats: ReadingStats, columns: Int, modifier: Modifier = Modifier) {
    val metrics = stats.detailMetrics()
    Column(modifier.fillMaxWidth()) {
        val rows = metrics.chunked(columns)
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { metric ->
                    StatFact(
                        value = metric.value,
                        label = metric.label,
                        modifier = Modifier
                            .weight(1f)
                            .ruleBelow(show = rowIndex != rows.lastIndex)
                            .padding(vertical = 14.dp),
                    )
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** A 1dp divider in the rule colour along the bottom edge. */
@Composable
internal fun Modifier.ruleBelow(show: Boolean = true): Modifier {
    if (!show) return this
    val rule = YomuTheme.stack.rule
    return drawBehind {
        val stroke = 1.dp.toPx()
        drawRect(rule, topLeft = Offset(0f, size.height - stroke), size = Size(size.width, stroke))
    }
}

@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = text,
            style = StackType.Heading,
            color = YomuTheme.stack.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = YomuTheme.stack.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = YomuTheme.space.sm),
            )
        }
    }
}

/** A section with nothing to show yet: one muted line where its rows would be. */
@Composable
internal fun SectionEmpty(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = StackType.BodySmall,
        color = YomuTheme.stack.muted,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = YomuTheme.space.sm),
    )
}

/**
 * A book's real cover, small, with Stack's cover shadow. Without a cover it is a raised block
 * holding the title's initial, never a drawn fake cover.
 */
@Composable
internal fun CoverThumb(path: String?, title: String, width: Dp, modifier: Modifier = Modifier) {
    val stack = YomuTheme.stack
    Box(
        modifier = modifier
            .size(width = width, height = width * 1.5f)
            .stackCoverShadow(ThumbShape)
            .clip(ThumbShape)
            .background(stack.raised),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.trim().take(1).uppercase(),
                style = StackType.Heading,
                color = stack.muted,
            )
        }
    }
}
