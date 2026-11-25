@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.itexpert120.yomu.feature.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuBadgeShape
import com.itexpert120.yomu.core.designsystem.YomuBadgeTone
import com.itexpert120.yomu.core.designsystem.YomuBookCoverAspectRatio
import com.itexpert120.yomu.core.designsystem.YomuCard
import com.itexpert120.yomu.core.designsystem.YomuCardTone
import com.itexpert120.yomu.core.designsystem.YomuContentMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuLoadingState
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuPaneSpacer
import com.itexpert120.yomu.core.designsystem.YomuScreenHeader
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSectionHeader
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuShapeBadge
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughEnter
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughExit
import com.itexpert120.yomu.core.designsystem.yomuLeadingPaneWidth
import com.itexpert120.yomu.core.designsystem.yomuSettingPosition
import com.itexpert120.yomu.core.model.BookReadingTime
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

@Composable
fun StatsRoute(onBack: (() -> Unit)?) {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsScreen(state = state, onBack = onBack)
}

private enum class StatsPhase { Loading, Error, Content }

private val StatsUiState.phase: StatsPhase
    get() = when {
        isLoading -> StatsPhase.Loading
        error != null -> StatsPhase.Error
        else -> StatsPhase.Content
    }

@Composable
fun StatsScreen(state: StatsUiState, onBack: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Stats is information-dense, so per Material 3 it stays single-pane until expanded width.
        if (YomuWidthClass.fromWidth(maxWidth).isExpanded) {
            TabletStatsLayout(state = state, onBack = onBack)
        } else {
            YomuScreenScaffold(
                title = "Statistics",
                onBack = onBack,
                showScrollEdgeShadow = false,
                maxContentWidth = YomuContentMaxWidth,
            ) {
                AnimatedContent(
                    targetState = state.phase,
                    transitionSpec = { yomuFadeThroughEnter() togetherWith yomuFadeThroughExit() },
                    label = "statsPhase",
                ) { phase ->
                    when (phase) {
                        StatsPhase.Loading -> StatsLoading(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = YomuTheme.space.xxl),
                        )

                        StatsPhase.Error -> StatsError(state.error)

                        StatsPhase.Content -> Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
                        ) {
                            Dashboard(stats = state.stats, bookTimes = state.bookTimes)
                            // With no activity the dashboard already explains the empty state.
                            if (state.stats.hasActivity && state.history.isNotEmpty()) {
                                History(state.history)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletStatsLayout(state: StatsUiState, onBack: (() -> Unit)?) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    YomuAppSurface {
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            YomuScreenHeader(title = "Statistics", onBack = onBack, scrollBehavior = scrollBehavior)
            AnimatedContent(
                targetState = state.phase,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                transitionSpec = { yomuFadeThroughEnter() togetherWith yomuFadeThroughExit() },
                label = "statsPhase",
            ) { phase ->
                when (phase) {
                    StatsPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        StatsLoading()
                    }

                    StatsPhase.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        StatsError(state.error)
                    }

                    StatsPhase.Content -> TabletStatsPanes(
                        stats = state.stats,
                        history = state.history,
                        bookTimes = state.bookTimes,
                    )
                }
            }
        }
    }
}

/**
 * Dashboard in the wide flexible pane (capped and centred), session history in the narrower fixed
 * pane on the trailing side. Same margins and spacer as `YomuTwoPane`, with the fixed pane last.
 */
@Composable
private fun TabletStatsPanes(
    stats: ReadingStats,
    history: List<ReadingSessionItem>,
    bookTimes: List<BookReadingTime>,
) {
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navBottom + YomuTheme.space.lg
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val historyWidth = yomuLeadingPaneWidth(maxWidth)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = YomuPaneSpacer),
            horizontalArrangement = Arrangement.spacedBy(YomuPaneSpacer),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = YomuTheme.space.xs)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = YomuContentMaxWidth)
                        .fillMaxWidth()
                        .padding(bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
                ) {
                    Dashboard(stats = stats, bookTimes = bookTimes)
                }
            }
            Column(
                modifier = Modifier
                    .width(historyWidth)
                    .fillMaxHeight()
                    .padding(top = YomuTheme.space.xs)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
            ) {
                if (history.isEmpty()) {
                    YomuSectionHeader(title = "Recent history")
                    YomuCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                        YomuEmptyState(
                            icon = StackIcons.History,
                            title = "No sessions yet",
                            body = "Your reading sessions will appear here.",
                            compact = true,
                        )
                    }
                } else {
                    History(history)
                }
            }
        }
    }
}

private val ReadingStats.hasActivity: Boolean
    get() = sessionCount != 0 || totalReadingSeconds != 0L

/** Hero total, headline metrics, time by book and the detail grid — shared by phone and tablet. */
@Composable
private fun ColumnScope.Dashboard(stats: ReadingStats, bookTimes: List<BookReadingTime>) {
    HistoricalDetailNotice(stats)
    YomuSectionHeader(title = "At a glance", subtitle = "A quick view of your reading.")
    AtAGlanceCard(stats)
    BookTimeHeaderAndList(bookTimes)
    if (stats.hasActivity) {
        YomuSectionHeader(title = "Reading details", subtitle = "The small signals behind your routine.")
        DetailsGrid(stats)
    } else {
        EmptyActivityCard()
    }
}

@Composable
private fun ReadingMetricGrid(metrics: List<DetailMetric>) {
    if (metrics.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val maxColumns = (maxWidth.value / (160f * fontScale)).toInt().coerceIn(1, 5)
        // Balance the rows (five tiles in a four-wide pane become 3 + 2, not 4 + 1); a short last
        // row stretches to the full width, so on two-column phones an odd tile spans the row.
        val rows = (metrics.size + maxColumns - 1) / maxColumns
        val columns = (metrics.size + rows - 1) / rows
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
            metrics.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                ) {
                    row.forEach { metric ->
                        ReadingMetricCard(
                            metric,
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingMetricCard(metric: DetailMetric, modifier: Modifier = Modifier) {
    val tileModifier = modifier.heightIn(min = 96.dp)
    // Highlighted metrics borrow an accent container; the rest stay on the neutral group surface so
    // the accents keep their pull.
    val body: @Composable ColumnScope.() -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs)) {
            if (metric.icon != null) {
                YomuShapeBadge(
                    icon = metric.icon,
                    size = 36.dp,
                    shape = metric.shape,
                    tone = when (metric.accent) {
                        MetricAccent.None -> YomuBadgeTone.Neutral
                        MetricAccent.Secondary -> YomuBadgeTone.Tertiary
                        MetricAccent.Tertiary -> YomuBadgeTone.Secondary
                    },
                )
            }
            Text(
                text = metric.value,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Text(
                text = metric.label,
                color = if (metric.accent == MetricAccent.None) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    LocalContentColor.current
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    when (metric.accent) {
        MetricAccent.None -> YomuCard(modifier = tileModifier, tone = YomuCardTone.Group, content = body)

        MetricAccent.Tertiary -> YomuCard(
            modifier = tileModifier,
            tone = YomuCardTone.HeroAlt,
            shape = YomuShapeTokens.tile,
            content = body,
        )

        // The card ladder has no secondary-container step, so this tile sets its surface directly.
        MetricAccent.Secondary -> Surface(
            modifier = tileModifier,
            shape = YomuShapeTokens.tile,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Column(Modifier.padding(YomuTheme.space.md), content = body)
        }
    }
}

@Composable
private fun BookTimeHeaderAndList(bookTimes: List<BookReadingTime>) {
    if (bookTimes.isEmpty()) return
    val total = bookTimes.sumOf { it.seconds }
    YomuSectionHeader(
        title = "Time by book",
        subtitle = "${formatReadingTime(total)} across ${bookTimes.size} " +
            "${if (bookTimes.size == 1) "book" else "books"}.",
    )
    BookTimeSection(books = bookTimes, formatTime = ::formatReadingTime)
}

@Composable
private fun HistoricalDetailNotice(stats: ReadingStats) {
    if (stats.historicalSessionDetailIncomplete) {
        YomuNotice(
            text = "Older session details were previously removed. Session and per-book figures include only " +
                "recoverable history; total reading time is preserved.",
            modifier = Modifier.fillMaxWidth(),
            icon = StackIcons.Info,
            tone = YomuNoticeTone.Neutral,
        )
    }
}

@Composable
private fun StatsLoading(modifier: Modifier = Modifier) {
    YomuLoadingState(modifier = modifier, message = "Gathering your reading history…")
}

@Composable
private fun StatsError(message: String?) {
    YomuEmptyState(
        icon = StackIcons.Error,
        title = message ?: "Statistics couldn't be loaded.",
        shape = YomuBadgeShape.Burst,
        tone = YomuBadgeTone.Error,
    )
}

@Composable
private fun AtAGlanceCard(stats: ReadingStats) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
        // Hero moment: editorial display type plus the sunny time badge, on the brightest accent.
        YomuCard(
            modifier = Modifier.fillMaxWidth(),
            tone = YomuCardTone.Hero,
            contentPadding = PaddingValues(YomuTheme.space.lg),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // The badge shares a row with the text, so a long total wraps instead of running
                // under it; on very narrow cards it shrinks, then steps aside entirely.
                val badgeSize = when {
                    maxWidth < 240.dp -> null
                    maxWidth < 320.dp -> 56.dp
                    else -> 72.dp
                }
                val roomy = maxWidth >= 400.dp
                Row(horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.md)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                    ) {
                        Text("Total reading time", style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(
                            formatReadingTime(stats.totalReadingSeconds),
                            style = if (roomy) {
                                MaterialTheme.typography.displayLargeEmphasized
                            } else {
                                MaterialTheme.typography.displayMediumEmphasized
                            },
                        )
                        Text("Across your reading sessions", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (badgeSize != null) {
                        YomuShapeBadge(
                            icon = StackIcons.Schedule,
                            size = badgeSize,
                            shape = YomuBadgeShape.Sunny,
                            tone = YomuBadgeTone.Solid,
                        )
                    }
                }
            }
        }
        ReadingMetricGrid(
            listOf(
                DetailMetric(
                    formatDays(stats.currentStreakDays),
                    "Current streak",
                    icon = StackIcons.LocalFireDepartment,
                    shape = YomuBadgeShape.Sunny,
                    accent = MetricAccent.Tertiary,
                ),
                DetailMetric(
                    formatReadingTime(stats.secondsLast7Days),
                    "Last 7 days",
                    icon = StackIcons.DateRange,
                    shape = YomuBadgeShape.Scallop,
                    accent = MetricAccent.Secondary,
                ),
                DetailMetric(stats.booksInLibrary.toString(), "Library items", icon = StackIcons.LibraryBooks),
                DetailMetric(stats.booksFinished.toString(), "Completed books", icon = StackIcons.TaskAlt),
                DetailMetric(formatReadingTime(stats.secondsLast30Days), "Last 30 days", icon = StackIcons.CalendarMonth),
            ),
        )
    }
}

@Composable
private fun EmptyActivityCard() {
    YomuCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        YomuEmptyState(
            icon = StackIcons.Bookmark,
            title = "No reading activity yet",
            body = "Open a book to start building your reading history.",
            compact = true,
        )
    }
}

private enum class MetricAccent { None, Secondary, Tertiary }

private data class DetailMetric(
    val value: String,
    val label: String,
    val icon: ImageVector? = null,
    val shape: YomuBadgeShape = YomuBadgeShape.Clover,
    val accent: MetricAccent = MetricAccent.None,
)

@Composable
private fun DetailsGrid(stats: ReadingStats) {
    val metrics = listOf(
        DetailMetric(stats.chaptersRead.toString(), "Chapters read"),
        DetailMetric(stats.sessionCount.toString(), "Reading sessions"),
        DetailMetric(stats.daysRead.toString(), "Active days"),
        DetailMetric(formatDays(stats.longestStreakDays), "Longest streak"),
        DetailMetric(formatReadingTime(stats.averageSessionSeconds), "Average session"),
        DetailMetric(formatReadingTime(stats.longestSessionSeconds), "Longest session"),
        DetailMetric(formatReadingTime(stats.averageSecondsPerActiveDay), "Average per day"),
        DetailMetric(stats.booksStarted.toString(), "Books started"),
        DetailMetric(formatCount(stats.estimatedWordsRead), "Estimated words"),
    )
    ReadingMetricGrid(metrics)
}

@Composable
private fun History(history: List<ReadingSessionItem>) {
    val consolidated = remember(history) { consolidateSessions(history) }
    val totalSessions = consolidated.sumOf { it.sessionCount }
    val totalSeconds = consolidated.sumOf { it.seconds }
    var visibleCount by remember { mutableIntStateOf(HistoryPage) }
    val byDay = consolidated.take(visibleCount).groupBy { session ->
        Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        YomuSectionHeader(
            title = "Recent history",
            subtitle = "$totalSessions ${if (totalSessions == 1) "session" else "sessions"} · " +
                "${formatReadingTime(totalSeconds)} read.",
        )
        byDay.forEach { (day, sessions) -> HistoryDay(day, sessions) }
        if (consolidated.size > visibleCount) {
            FilledTonalButton(
                onClick = { visibleCount += HistoryPage },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = YomuTheme.space.xxs),
            ) {
                Icon(
                    imageVector = StackIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = "Show more")
            }
        }
    }
}

/** One day of sessions: a label line, then the same segmented list idiom as "Time by book". */
@Composable
private fun HistoryDay(day: LocalDate, sessions: List<ReadingSessionItem>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
        ) {
            YomuSectionLabel(formatDayHeader(day))
            Text(
                text = formatSessionSummary(sessions),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = YomuTheme.space.xxs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        YomuSettingList {
            sessions.forEachIndexed { index, session ->
                HistoryRow(session, yomuSettingPosition(index, sessions.size))
            }
        }
    }
}

@Composable
private fun HistoryRow(session: ReadingSessionItem, position: YomuSettingPosition) {
    YomuSettingRow(
        title = session.bookTitle,
        subtitle = formatClock(session.startedAt),
        position = position,
        leadingContent = { CoverThumbnail(session.coverImagePath) },
    ) {
        Surface(
            shape = YomuShapeTokens.pill,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = formatMinutesRead(session.seconds),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = YomuTheme.space.sm, vertical = YomuTheme.space.xxs),
            )
        }
    }
}

@Composable
private fun CoverThumbnail(path: String?) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .aspectRatio(YomuBookCoverAspectRatio)
            .clip(YomuShapeTokens.cover)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun formatDays(days: Int): String = "$days ${if (days == 1) "day" else "days"}"

private fun formatSessionSummary(sessions: List<ReadingSessionItem>): String {
    val count = sessions.sumOf { it.sessionCount }
    val totalSeconds = sessions.sumOf { it.seconds }
    return "$count ${if (count == 1) "session" else "sessions"} · ${formatReadingTime(totalSeconds)} read"
}

private fun consolidateSessions(history: List<ReadingSessionItem>): List<ReadingSessionItem> {
    if (history.size < 2) return history
    val merged = mutableListOf<ReadingSessionItem>()
    var current = history.first()
    for (next in history.drop(1)) {
        val gapSeconds = current.startedAt / 1000 - (next.startedAt / 1000 + next.seconds)
        val bothShort = current.seconds <= ShortSessionSeconds && next.seconds <= ShortSessionSeconds
        current = if (
            current.bookId != null &&
            current.bookId == next.bookId &&
            bothShort &&
            gapSeconds <= MergeGapSeconds
        ) {
            current.copy(
                seconds = current.seconds + next.seconds,
                sessionCount = current.sessionCount + next.sessionCount,
            )
        } else {
            merged += current
            next
        }
    }
    merged += current
    return merged
}

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

private fun formatMinutesRead(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes > 0) "$minutes min" else "<1 min"
}

private val ClockFormat = LocaleDateFormatter("h:mm a")
private val DayHeaderFormat = LocaleDateFormatter("MMM d, yyyy")

/** A [DateTimeFormatter] built once per default locale instead of once per history row. */
private class LocaleDateFormatter(private val pattern: String) {
    @Volatile
    private var cached: Pair<Locale, DateTimeFormatter>? = null

    fun format(value: TemporalAccessor): String {
        val locale = Locale.getDefault()
        val formatter = cached?.takeIf { it.first == locale }?.second
            ?: DateTimeFormatter.ofPattern(pattern, locale).also { cached = locale to it }
        return formatter.format(value)
    }
}

private fun formatClock(millis: Long): String = ClockFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun formatDayHeader(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DayHeaderFormat.format(day)
    }
}

private fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}

private const val HistoryPage = 12
private const val MergeGapSeconds = 20 * 60L
private const val ShortSessionSeconds = 3 * 60L

@Preview(widthDp = 720, heightDp = 900, showBackground = true)
@Composable
private fun TabletStatsPreview() {
    com.itexpert120.yomu.core.designsystem.YomuDesignTheme {
        StatsScreen(
            state = StatsUiState(isLoading = false),
            onBack = {},
        )
    }
}

@Preview(widthDp = 1280, heightDp = 900, showBackground = true)
@Composable
private fun WideStatsPreview() {
    com.itexpert120.yomu.core.designsystem.YomuDesignTheme {
        StatsScreen(
            state = StatsUiState(isLoading = false),
            onBack = {},
        )
    }
}
