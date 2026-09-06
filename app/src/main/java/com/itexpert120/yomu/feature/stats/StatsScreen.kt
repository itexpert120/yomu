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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuScreenHeader
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTwoPane
import com.itexpert120.yomu.core.designsystem.supportsYomuTwoPane
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
                    else -> StatsContent(stats = state.stats, history = state.history)
                }
            }
        }
    }
}

@Composable
private fun TabletStatsLayout(
    stats: ReadingStats,
    history: List<ReadingSessionItem>,
    onBack: (() -> Unit)?,
) {
    YomuAppSurface {
        Column(Modifier.fillMaxSize()) {
            YomuScreenHeader(title = "Statistics", onBack = onBack)
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
                        TabletMetricGrid(
                            metrics = listOf(
                                DetailMetric(formatDays(stats.currentStreakDays), "Current streak"),
                                DetailMetric(formatReadingTime(stats.totalReadingSeconds), "Total read"),
                                DetailMetric(stats.booksInLibrary.toString(), "Library items"),
                                DetailMetric(stats.booksFinished.toString(), "Completed books"),
                                DetailMetric(formatReadingTime(stats.secondsLast7Days), "Last 7 days"),
                                DetailMetric(formatReadingTime(stats.secondsLast30Days), "Last 30 days"),
                            ),
                        )
                        SectionHeader(
                            title = "Reading details",
                            supporting = "The small signals behind your routine.",
                        )
                        TabletMetricGrid(
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
private fun TabletMetricGrid(metrics: List<DetailMetric>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { metric ->
                    TabletMetricCard(metric, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TabletMetricCard(metric: DetailMetric, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 88.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = metric.value,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metric.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColumnScope.StatsContent(
    stats: ReadingStats,
    history: List<ReadingSessionItem>,
) {
    HistoricalDetailNotice(stats)
    SectionHeader(
        title = "At a glance",
        supporting = "A quick view of your reading.",
    )
    AtAGlanceCard(stats)
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
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
        )
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AtAGlanceMetric(
                value = formatDays(stats.currentStreakDays),
                label = "Current streak",
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primary,
            )
            AtAGlanceMetric(
                value = formatReadingTime(stats.totalReadingSeconds),
                label = "Total read",
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AtAGlanceMetric(
                value = stats.booksInLibrary.toString(),
                label = "Library items",
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primary,
            )
            AtAGlanceMetric(
                value = stats.booksFinished.toString(),
                label = "Completed books",
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AtAGlanceMetric(
                value = formatReadingTime(stats.secondsLast7Days),
                label = "Last 7 days",
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primary,
            )
            AtAGlanceMetric(
                value = formatReadingTime(stats.secondsLast30Days),
                label = "Last 30 days",
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun AtAGlanceMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(YomuTheme.radius.pill))
                .background(accent),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
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
                shape = MaterialTheme.shapes.large,
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
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private data class DetailMetric(
    val value: String,
    val label: String,
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
    metrics.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { metric ->
                DetailMetricCard(metric = metric, modifier = Modifier.weight(1f))
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailMetricCard(metric: DetailMetric, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.heightIn(min = 112.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = metric.value,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metric.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
                    style = MaterialTheme.typography.titleMedium,
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
