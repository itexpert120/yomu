package com.itexpert120.yomu.feature.stats

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.BookReadingTime
import kotlin.math.roundToInt

private const val CollapsedBookCount = 5

/** A row of "Time by book": one book, or every removed book folded together ([removedBooks] > 0). */
@Immutable
internal data class BookTimeEntry(
    val title: String,
    val author: String?,
    val coverImagePath: String?,
    val seconds: Long,
    val sessionCount: Int,
    val removedBooks: Int = 0,
)

/**
 * Books still in the library keep a row each; every removed book (no id any more) folds into one
 * "Removed books" row, placed by its combined time like any other. Longest first.
 */
internal fun foldRemovedBooks(books: List<BookReadingTime>): List<BookTimeEntry> {
    val (kept, removed) = books.partition { it.bookId != null }
    val rows = kept.mapTo(ArrayList(kept.size + 1)) {
        BookTimeEntry(it.title, it.author, it.coverImagePath, it.seconds, it.sessionCount)
    }
    if (removed.isNotEmpty()) {
        rows += BookTimeEntry(
            title = if (removed.size == 1) "Removed book" else "Removed books",
            author = if (removed.size == 1) null else plural(removed.size, "book"),
            coverImagePath = null,
            seconds = removed.sumOf { it.seconds },
            sessionCount = removed.sumOf { it.sessionCount },
            removedBooks = removed.size,
        )
    }
    // Stable: equal times keep the repository's order.
    rows.sortByDescending { it.seconds }
    return rows
}

/**
 * Lifetime reading time for every book, longest first: its cover, its share of all reading as a
 * thin ink bar, and the time. Removed books share one row. The list starts with the top few and
 * expands in place.
 */
@Composable
internal fun BookTimeSection(
    books: List<BookReadingTime>,
    modifier: Modifier = Modifier,
    thumbWidth: Dp = 32.dp,
) {
    Column(modifier.fillMaxWidth()) {
        val rows = remember(books) { foldRemovedBooks(books) }
        val total = remember(books) { books.sumOf { it.seconds } }
        SectionHeading(text = "Time by book", trailing = null)
        if (books.isEmpty()) {
            SectionEmpty("Time you spend in each book will add up here.")
            return@Column
        }
        var expanded by rememberSaveable { mutableStateOf(false) }
        val visible = if (expanded) rows else rows.take(CollapsedBookCount)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = YomuTheme.space.xxs)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            visible.forEachIndexed { index, book ->
                BookTimeRow(
                    book = book,
                    share = book.seconds.toFloat() / total.coerceAtLeast(1L),
                    thumbWidth = thumbWidth,
                    last = index == visible.lastIndex,
                )
            }
        }
        if (rows.size > CollapsedBookCount) {
            SectionTextAction(
                text = if (expanded) "Show fewer" else "Show ${rows.size - CollapsedBookCount} more",
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = YomuTheme.space.xxs),
            )
        }
    }
}

@Composable
private fun BookTimeRow(
    book: BookTimeEntry,
    share: Float,
    thumbWidth: Dp,
    last: Boolean,
) {
    val stack = YomuTheme.stack
    val time = formatReadingTime(book.seconds)
    val percent = (share * 100).roundToInt()
    // Bars grow in from zero the first time the row appears, a small cue that these are shares.
    val animatedShare = remember { Animatable(if (yomuAnimationsEnabled()) 0f else share) }
    val growSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(share) { animatedShare.animateTo(share, growSpec) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .ruleBelow(show = !last)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${book.title}, ${speakReadingTime(book.seconds)}, $percent percent of your reading"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (book.removedBooks > 0) {
            RemovedBooksThumb(width = thumbWidth)
        } else {
            CoverThumb(path = book.coverImagePath, title = book.title, width = thumbWidth)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = StackType.Body,
                color = stack.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Track and fill drawn in one pass; the animated share is read only while drawing.
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2f)
                        drawRoundRect(stack.rule, cornerRadius = radius)
                        val fill = size.width * animatedShare.value.coerceIn(0f, 1f)
                        if (fill > 0f) drawRoundRect(stack.ink, size = Size(fill.coerceAtLeast(size.height), size.height), cornerRadius = radius)
                    },
            )
        }
        Text(
            text = time,
            style = StackType.BodySmall.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
            color = stack.muted,
            maxLines = 1,
        )
    }
}

/** Removed books have no cover any more: an empty raised block with a quiet delete mark. */
@Composable
internal fun RemovedBooksThumb(width: Dp) {
    val stack = YomuTheme.stack
    Box(
        modifier = Modifier
            .size(width = width, height = width * 1.5f)
            .clip(ThumbShape)
            .background(stack.raised),
        contentAlignment = Alignment.Center,
    ) {
        Icon(StackIcons.Delete, contentDescription = null, tint = stack.faint, modifier = Modifier.size(width * 0.45f))
    }
}

/** A section's quiet text action ("Show more"), its label on the content's leading edge. */
@Composable
internal fun SectionTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .yomuPressable(onClick = onClick, role = Role.Button, shape = RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = StackType.Heading, color = YomuTheme.stack.ink)
    }
}
