package com.itexpert120.yomu.feature.stats

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.model.ReadingSessionItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val HistoryPage = 12
private const val MergeGapSeconds = 20 * 60L
private const val ShortSessionSeconds = 3 * 60L

/**
 * Recent reading sessions, newest first, grouped by day. Short sessions of the same book close
 * together are shown as one row; the list grows a page at a time.
 */
@Composable
internal fun HistorySection(
    history: List<ReadingSessionItem>,
    historicalDetailIncomplete: Boolean,
    modifier: Modifier = Modifier,
    thumbWidth: Dp = 32.dp,
) {
    val consolidated = remember(history) { consolidateSessions(history) }
    var visibleCount by rememberSaveable { mutableIntStateOf(HistoryPage) }
    val byDay = remember(consolidated, visibleCount) {
        consolidated.take(visibleCount).groupBy { session ->
            Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    val totalSessions = consolidated.sumOf { it.sessionCount }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        SectionHeading(
            text = "Recent history",
            trailing = if (consolidated.isEmpty()) {
                null
            } else {
                "${plural(totalSessions, "session")} · ${formatReadingTime(consolidated.sumOf { it.seconds })}"
            },
        )
        if (consolidated.isEmpty()) {
            SectionEmpty("Each time you read, the session shows up here, newest first.")
        }
        val today = LocalDate.now()
        byDay.forEach { (day, sessions) ->
            HistoryDayHeader(day, today, sessions)
            sessions.forEach { session -> HistoryRow(session, thumbWidth) }
        }
        if (consolidated.size > visibleCount) {
            YomuButton(
                text = "Show more",
                onClick = { visibleCount += HistoryPage },
                emphasis = YomuButtonEmphasis.Ghost,
                modifier = Modifier.padding(top = YomuTheme.space.xxs),
            )
        }
        if (historicalDetailIncomplete) {
            YomuNotice(
                text = "Some older sessions are kept only as daily totals, so session and per-book figures " +
                    "leave them out. Your total time and streaks still count them.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = YomuTheme.space.sm),
                icon = StackIcons.Info,
                tone = YomuNoticeTone.Neutral,
            )
        }
    }
}

@Composable
private fun HistoryDayHeader(day: LocalDate, today: LocalDate, sessions: List<ReadingSessionItem>) {
    val stack = YomuTheme.stack
    val count = sessions.sumOf { it.sessionCount }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = YomuTheme.space.xxs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = formatDayHeader(day, today),
            style = StackType.Label,
            color = stack.muted,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Text(
            text = "${plural(count, "session")} · ${formatReadingTime(sessions.sumOf { it.seconds })}",
            style = MaterialTheme.typography.bodySmall,
            color = stack.faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistoryRow(session: ReadingSessionItem, thumbWidth: Dp) {
    val stack = YomuTheme.stack
    val merged = if (session.sessionCount > 1) " · ${plural(session.sessionCount, "session")}" else ""
    // A consolidated row spans gaps, so it shows when it started rather than a range.
    val range = if (session.sessionCount > 1) formatClock(session.startedAt) else formatClockRange(session.startedAt, session.seconds)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${session.bookTitle}, ${formatClock(session.startedAt)}, " +
                    speakReadingTime(session.seconds) + merged
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CoverThumb(path = session.coverImagePath, title = session.bookTitle, width = thumbWidth)
        Column(Modifier.weight(1f)) {
            Text(
                text = session.bookTitle,
                style = StackType.Body,
                color = stack.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = range + merged,
                style = MaterialTheme.typography.bodySmall,
                color = stack.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = formatMinutesRead(session.seconds),
            style = StackType.BodySmall.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
            color = stack.muted,
            maxLines = 1,
        )
    }
}

private fun formatMinutesRead(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes > 0) formatReadingTime(seconds) else "<1 min"
}

internal fun consolidateSessions(history: List<ReadingSessionItem>): List<ReadingSessionItem> {
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
