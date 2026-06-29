package com.itexpert120.yomu.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSegmentedControl
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.DailyReading
import com.itexpert120.yomu.core.model.HeatmapDay
import com.itexpert120.yomu.core.model.HourlyReading
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import com.itexpert120.yomu.core.model.WeekdayReading
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun StatsRoute(onBack: () -> Unit) {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    StatsScreen(
        state = state,
        onBack = onBack,
        onSelectTrendRange = viewModel::onSelectTrendRange,
    )
}

@Composable
fun StatsScreen(
    state: StatsUiState,
    onBack: () -> Unit,
    onSelectTrendRange: (TrendRange) -> Unit,
) {
    val stats = state.stats
    YomuScreenScaffold(title = "Statistics", onBack = onBack) {
        HeroCard(stats)

        TrendSection(
            range = state.trendRange,
            trend = state.trend,
            onSelectRange = onSelectTrendRange,
        )

        StatTileGrid(stats)

        if (state.weekday.any { it.seconds > 0L }) {
            WeekdaySection(state.weekday)
        }

        if (state.hourly.any { it.seconds > 0L }) {
            HourOfDaySection(state.hourly)
        }

        if (state.heatmap.any { it.seconds > 0L }) {
            HeatmapSection(state.heatmap)
        }

        if (state.history.isNotEmpty()) {
            HistorySection(state.history)
        }
    }
}

// region Hero

@Composable
private fun HeroCard(stats: ReadingStats) {
    StatCard(padding = 20.dp) {
        Text(
            text = "Total reading time",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
        )
        Text(
            text = formatReadingTime(stats.totalReadingSeconds),
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.display,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stats.currentStreakDays > 0) {
                HeroStatChip(text = "${stats.currentStreakDays}-day streak", accent = true)
            }
            if (stats.secondsLast7Days > 0L) {
                HeroStatChip(text = "${formatReadingTime(stats.secondsLast7Days)} this week")
            }
        }
    }
}

/** A compact supporting stat under the hero number; accent variant highlights the active streak. */
@Composable
private fun HeroStatChip(text: String, accent: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(if (accent) YomuTheme.colors.accentSoft else YomuTheme.colors.surfaceSunken)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (accent) YomuTheme.colors.accent else YomuTheme.colors.textSecondary,
            style = YomuTheme.type.caption,
        )
    }
}

// endregion

// region Daily trend chart

@Composable
private fun TrendSection(
    range: TrendRange,
    trend: List<DailyReading>,
    onSelectRange: (TrendRange) -> Unit,
) {
    StatCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel("Reading per day")
            YomuSegmentedControl(
                options = TrendRange.entries.map { it.label },
                selectedIndex = TrendRange.entries.indexOf(range),
                onSelected = { onSelectRange(TrendRange.entries[it]) },
                modifier = Modifier.width(168.dp),
            )
        }
        if (trend.all { it.seconds == 0L }) {
            EmptyChartHint("No reading recorded yet")
        } else {
            ColumnChart(
                values = trend.map { it.seconds / 60.0 },
                bottomFormatter = dayAxisFormatter(trend),
                startFormatter = minutesAxisFormatter(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}

// endregion

// region Stat tiles

@Composable
private fun ColumnScope.StatTileGrid(stats: ReadingStats) {
    SectionLabel("Overview")
    val tiles = buildList {
        add("${stats.longestStreakDays}" to "Longest streak")
        add("${stats.daysRead}" to "Days read")
        add("${stats.booksFinished}" to "Books finished")
        add("${stats.booksStarted}" to "Books started")
        add("${stats.chaptersRead}" to "Chapters read")
        add("~${formatCount(stats.estimatedWordsRead)}" to "Words read")
        add("${stats.sessionCount}" to "Sessions")
        add(formatReadingTime(stats.averageSessionSeconds) to "Avg. session")
        add(formatReadingTime(stats.averageSecondsPerActiveDay) to "Avg. per day")
        add("${stats.booksInLibrary}" to "In library")
    }
    tiles.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { (value, label) -> StatTile(value = value, label = label) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowScope.StatTile(value: String, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(YomuTheme.colors.surfaceRaised)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = value, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.title)
        Text(text = label, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
    }
}

// endregion

// region Weekday chart

@Composable
private fun WeekdaySection(weekday: List<WeekdayReading>) {
    StatCard {
        SectionLabel("By day of week")
        ColumnChart(
            values = weekday.map { it.seconds / 60.0 },
            bottomFormatter = labelFormatter(WEEKDAY_LABELS),
            startFormatter = minutesAxisFormatter(),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }
}

// endregion

// region Hour-of-day chart

@Composable
private fun HourOfDaySection(hourly: List<HourlyReading>) {
    StatCard {
        SectionLabel("When you read")
        ColumnChart(
            values = hourly.map { it.seconds / 60.0 },
            bottomFormatter = hourAxisFormatter(),
            startFormatter = minutesAxisFormatter(),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CaptionMuted("12 AM")
            CaptionMuted("12 PM")
            CaptionMuted("11 PM")
        }
    }
}

// endregion

// region Activity heatmap

@Composable
private fun HeatmapSection(heatmap: List<HeatmapDay>) {
    StatCard {
        SectionLabel("Activity")
        // Columns are weeks (7 rows, Monday on top). The repository emits week-aligned full weeks.
        val weeks = heatmap.chunked(7)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            weeks.forEach { week ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    week.forEach { day -> HeatCell(day) }
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CaptionMuted("Less")
            (0..4).forEach { level ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(heatColor(level)),
                )
            }
            CaptionMuted("More")
        }
    }
}

@Composable
private fun ColumnScope.HeatCell(day: HeatmapDay) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .background(heatColor(day.level)),
    )
}

@Composable
private fun heatColor(level: Int): Color {
    val colors = YomuTheme.colors
    return when (level) {
        0 -> colors.surfaceSunken
        1 -> colors.accent.copy(alpha = 0.25f)
        2 -> colors.accent.copy(alpha = 0.45f)
        3 -> colors.accent.copy(alpha = 0.7f)
        else -> colors.accent
    }
}

// endregion

// region History

/** How many sessions to reveal per "Show more" press. */
private const val HISTORY_PAGE = 12

/**
 * Recent reading, grouped by day: a date heading, then a compact timeline of that day's sessions
 * (when it was read · how long). Paginated so a long history stays tidy instead of an endless list.
 */
@Composable
private fun ColumnScope.HistorySection(history: List<ReadingSessionItem>) {
    SectionLabel("Recent reading", modifier = Modifier.padding(top = 8.dp))

    var visibleCount by remember { mutableIntStateOf(HISTORY_PAGE) }
    val shown = history.take(visibleCount)
    // groupBy keeps key order; history is newest-first, so days come newest-first too.
    val byDay = shown.groupBy {
        Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        byDay.forEach { (day, sessions) -> DayGroup(day, sessions) }
    }

    if (history.size > visibleCount) {
        Text(
            text = "Show more",
            color = YomuTheme.colors.accent,
            style = YomuTheme.type.control,
            modifier = Modifier
                .padding(top = 12.dp)
                .align(Alignment.CenterHorizontally)
                .yomuPressable(onClick = { visibleCount += HISTORY_PAGE })
                .clip(RoundedCornerShape(YomuTheme.radius.pill))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DayGroup(day: LocalDate, sessions: List<ReadingSessionItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = formatDayHeader(day),
            color = YomuTheme.colors.textSecondary,
            style = YomuTheme.type.control,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(YomuTheme.radius.lg))
                .background(YomuTheme.colors.surfaceRaised)
                .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg))
                .padding(vertical = 4.dp),
        ) {
            sessions.forEach { session -> SessionTimelineRow(session) }
        }
    }
}

@Composable
private fun SessionTimelineRow(session: ReadingSessionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // When it was read, first.
        Text(
            text = formatClock(session.startedAt),
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.mono,
            modifier = Modifier.width(68.dp),
        )
        Text(
            text = session.bookTitle,
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatReadingTime(session.seconds),
            color = YomuTheme.colors.textSecondary,
            style = YomuTheme.type.mono,
        )
    }
}

// endregion

// region Vico column chart (themed)

/**
 * A reusable, Yomu-themed Vico column chart. Columns use the accent colour, axes/labels use the
 * design-system muted/border tokens, and the y-axis values are minutes.
 */
@Composable
private fun ColumnChart(
    values: List<Double>,
    bottomFormatter: CartesianValueFormatter,
    startFormatter: CartesianValueFormatter,
    modifier: Modifier = Modifier,
) {
    val colors = YomuTheme.colors
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        modelProducer.runTransaction {
            columnSeries { series(values.ifEmpty { listOf(0.0) }) }
        }
    }
    val column = rememberLineComponent(
        fill = fill(colors.accent),
        thickness = 12.dp,
        shape = CorneredShape.rounded(topLeftPercent = 30, topRightPercent = 30),
    )
    val labelComponent = rememberAxisLabelComponent(color = colors.textMuted)
    val lineComponent = rememberAxisLineComponent(fill = fill(colors.border))
    val tickComponent = rememberAxisTickComponent(fill = fill(colors.border))
    val guidelineComponent = rememberAxisGuidelineComponent(fill = fill(colors.border))
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(column),
            ),
            startAxis = VerticalAxis.rememberStart(
                label = labelComponent,
                line = lineComponent,
                tick = tickComponent,
                guideline = guidelineComponent,
                valueFormatter = startFormatter,
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = labelComponent,
                line = lineComponent,
                tick = tickComponent,
                guideline = null,
                valueFormatter = bottomFormatter,
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
        scrollState = rememberVicoScrollState(scrollEnabled = true),
        zoomState = rememberVicoZoomState(zoomEnabled = false),
    )
}

private fun minutesAxisFormatter(): CartesianValueFormatter = CartesianValueFormatter { _, value, _ ->
    val minutes = value.toInt()
    if (minutes >= 60) "${minutes / 60}h" else "${minutes}m"
}

/** Labels every few days so the 30-day window isn't a wall of text. */
private fun dayAxisFormatter(trend: List<DailyReading>): CartesianValueFormatter {
    val labels = trend.map { runCatching { LocalDate.parse(it.date) }.getOrNull() }
    val step = if (trend.size > 10) 5 else 1
    val fmt = SimpleDateFormat("d", Locale.getDefault())
    return CartesianValueFormatter { _, value, _ ->
        val index = value.toInt()
        val date = labels.getOrNull(index)
        if (date != null && index % step == 0) {
            fmt.format(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))
        } else {
            // Vico forbids empty labels; a blank space hides the label without crashing.
            " "
        }
    }
}

private fun hourAxisFormatter(): CartesianValueFormatter = CartesianValueFormatter { _, value, _ ->
    when (value.toInt()) {
        0 -> "12a"
        6 -> "6a"
        12 -> "12p"
        18 -> "6p"
        else -> " "
    }
}

private fun labelFormatter(labels: List<String>): CartesianValueFormatter = CartesianValueFormatter { _, value, _ -> labels.getOrElse(value.toInt()) { " " } }

private val WEEKDAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

// endregion

// region Shared primitives

@Composable
private fun StatCard(padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.lg))
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = YomuTheme.colors.textMuted,
        style = YomuTheme.type.caption,
        modifier = modifier,
    )
}

@Composable
private fun CaptionMuted(text: String) {
    Text(text = text, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
}

@Composable
private fun EmptyChartHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = YomuTheme.colors.textMuted, style = YomuTheme.type.body)
    }
}

// endregion

/** "Today" / "Yesterday" / "Jun 20, 2026" for a session-group day heading. */
private fun formatDayHeader(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val date = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant())
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        }
    }
}

private fun formatClock(millis: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

private fun formatReadingTime(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        seconds > 0 -> "<1m"
        else -> "—"
    }
}

private fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}
