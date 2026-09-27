@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuScreenHeader
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuTwoPane
import com.itexpert120.yomu.core.designsystem.supportsYomuTwoPane
import com.itexpert120.yomu.core.model.BookReadingTime
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun StatsRoute(onBack: (() -> Unit)?) {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    StatsScreen(state = state, onBack = onBack)
}

@Composable
fun StatsScreen(state: StatsUiState, onBack: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth.supportsYomuTwoPane() && !state.isLoading && state.error == null) {
            TabletStatsLayout(
                stats = state.stats,
                history = state.history,
                bookTimes = state.bookTimes,
                onBack = onBack,
            )
        } else {
            YomuScreenScaffold(
                title = "Statistics",
                onBack = onBack,
                showScrollEdgeShadow = false,
            ) {
                when {
                    state.isLoading -> LoadingState()
                    state.error != null -> StatusText(state.error)
                    else -> StatsContent(stats = state.stats, history = state.history, bookTimes = state.bookTimes)
                }
            }
        }
    }
}

@Composable
private fun TabletStatsLayout(
    stats: ReadingStats,
    history: List<ReadingSessionItem>,
    bookTimes: List<BookReadingTime>,
    onBack: (() -> Unit)?,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    YomuAppSurface {
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            YomuScreenHeader(title = "Statistics", onBack = onBack, scrollBehavior = scrollBehavior)
            HistoricalDetailNotice(stats)
            YomuTwoPane(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                startModifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                endModifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                startContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SectionHeader(
                            title = "At a glance",
                            supporting = "A quick view of your reading.",
                        )
                        AtAGlanceCard(stats)
                        BookTimeHeaderAndList(bookTimes)
                        SectionHeader(
                            title = "Reading details",
                            supporting = "The small signals behind your routine.",
                        )
                        ReadingMetricGrid(
                            metrics = listOf(
                                DetailMetric(stats.chaptersRead.toString(), "Chapters read"),
                                DetailMetric(stats.sessionCount.toString(), "Reading sessions"),
                                DetailMetric(stats.daysRead.toString(), "Active days"),
                                DetailMetric(formatDays(stats.longestStreakDays), "Longest streak"),
                                DetailMetric(formatReadingTime(stats.averageSessionSeconds), "Average session"),
                                DetailMetric(formatReadingTime(stats.longestSessionSeconds), "Longest session"),
                                DetailMetric(formatReadingTime(stats.averageSecondsPerActiveDay), "Average per day"),
                                DetailMetric(stats.booksStarted.toString(), "Books started"),
                                DetailMetric(formatCount(stats.estimatedWordsRead), "Estimated words"),
                            ),
                        )
                    }
                },
                endContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (history.isEmpty()) {
                            SectionHeader(
                                title = "Recent history",
                                supporting = "Your reading sessions will appear here.",
                            )
                            EmptyActivityCard()
                        } else {
                            History(history)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ReadingMetricGrid(metrics: List<DetailMetric>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val columns = (maxWidth.value / (160f * fontScale)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { metric -> ReadingMetricCard(metric, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ReadingMetricCard(metric: DetailMetric, modifier: Modifier = Modifier) {
    // Highlighted metrics borrow an accent container; the rest stay on neutral surfaces so the
    // accents keep their pull.
    val (container, content) = when (metric.accent) {
        MetricAccent.None -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurface
        MetricAccent.Secondary -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        MetricAccent.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = modifier.heightIn(min = 96.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (metric.icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(metric.shape.toShape())
                        .background(content.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(metric.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                text = metric.value,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Text(
                text = metric.label,
                color = content.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ColumnScope.StatsContent(
    stats: ReadingStats,
    history: List<ReadingSessionItem>,
    bookTimes: List<BookReadingTime>,
) {
    HistoricalDetailNotice(stats)
    SectionHeader(
        title = "At a glance",
        supporting = "A quick view of your reading.",
    )
    AtAGlanceCard(stats)
    BookTimeHeaderAndList(bookTimes)
    if (stats.sessionCount == 0 && stats.totalReadingSeconds == 0L) {
        EmptyActivityCard()
    } else {
        SectionHeader(
            title = "Reading details",
            supporting = "The small signals behind your routine.",
        )
        DetailsGrid(stats)
        if (history.isNotEmpty()) {
            History(history)
        }
    }
}

@Composable
private fun BookTimeHeaderAndList(bookTimes: List<BookReadingTime>) {
    if (bookTimes.isEmpty()) return
    val total = bookTimes.sumOf { it.seconds }
    SectionHeader(
        title = "Time by book",
        supporting = "${formatReadingTime(total)} across ${bookTimes.size} " +
            "${if (bookTimes.size == 1) "book" else "books"}.",
    )
    BookTimeSection(books = bookTimes, formatTime = ::formatReadingTime)
}

@Composable
private fun HistoricalDetailNotice(stats: ReadingStats) {
    if (stats.historicalSessionDetailIncomplete) {
        Text(
            "Older session details were previously removed. Session and per-book figures include only recoverable history; total reading time is preserved.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LoadingIndicator()
        Text(
            text = "Gathering your reading history…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AtAGlanceCard(stats: ReadingStats) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Hero moment: editorial display type plus a sunny shape motif, on the brightest accent.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLargeIncreased,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .size(72.dp)
                        .clip(MaterialShapes.Sunny.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total reading time", style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        formatReadingTime(stats.totalReadingSeconds),
                        style = MaterialTheme.typography.displayLargeEmphasized,
                    )
                    Text("Across your reading sessions", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        ReadingMetricGrid(
            listOf(
                DetailMetric(
                    formatDays(stats.currentStreakDays),
                    "Current streak",
                    icon = Icons.Rounded.LocalFireDepartment,
                    shape = MaterialShapes.Cookie6Sided,
                    accent = MetricAccent.Tertiary,
                ),
                DetailMetric(
                    formatReadingTime(stats.secondsLast7Days),
                    "Last 7 days",
                    icon = Icons.Rounded.DateRange,
                    shape = MaterialShapes.Cookie4Sided,
                    accent = MetricAccent.Secondary,
                ),
                DetailMetric(stats.booksInLibrary.toString(), "Library items", icon = Icons.AutoMirrored.Rounded.LibraryBooks),
                DetailMetric(stats.booksFinished.toString(), "Completed books", icon = Icons.Rounded.TaskAlt),
                DetailMetric(formatReadingTime(stats.secondsLast30Days), "Last 30 days", icon = Icons.Rounded.CalendarMonth),
            ),
        )
    }
}

@Composable
private fun EmptyActivityCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmark,
                        contentDescription = null,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "No reading activity yet",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Open a book to start building your reading history.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    supporting: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text = supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private enum class MetricAccent { None, Secondary, Tertiary }

private data class DetailMetric(
    val value: String,
    val label: String,
    val icon: ImageVector? = null,
    val shape: RoundedPolygon = MaterialShapes.Circle,
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
private fun ColumnScope.History(history: List<ReadingSessionItem>) {
    val consolidated = remember(history) { consolidateSessions(history) }
    val totalSessions = consolidated.sumOf { it.sessionCount }
    val totalSeconds = consolidated.sumOf { it.seconds }
    SectionHeader(
        title = "Recent history",
        supporting = "$totalSessions ${if (totalSessions == 1) "session" else "sessions"} · " +
            "${formatReadingTime(totalSeconds)} read.",
    )
    var visibleCount by remember { mutableIntStateOf(HistoryPage) }
    val byDay = consolidated.take(visibleCount).groupBy { session ->
        Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        byDay.forEach { (day, sessions) -> HistoryCard(day, sessions) }
    }
    if (consolidated.size > visibleCount) {
        TextButton(
            onClick = { visibleCount += HistoryPage },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = "Show more")
        }
    }
}

@Composable
private fun HistoryCard(day: LocalDate, sessions: List<ReadingSessionItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDayHeader(day),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatSessionSummary(sessions),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            sessions.forEachIndexed { index, session ->
                if (index > 0) HistoryDivider()
                HistoryRow(session)
            }
        }
    }
}

@Composable
private fun HistoryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    )
}

@Composable
private fun HistoryRow(session: ReadingSessionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverThumbnail(session.coverImagePath)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = session.bookTitle,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatClock(session.startedAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = formatMinutesRead(session.seconds),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun CoverThumbnail(path: String?) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .aspectRatio(1f / 1.5f)
            .clip(MaterialTheme.shapes.small)
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

private fun formatClock(millis: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

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
