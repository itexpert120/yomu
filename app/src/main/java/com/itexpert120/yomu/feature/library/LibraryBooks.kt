package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme

@Composable
internal fun ContinueReadingSection(onBookLongPress: (LibraryBook) -> Unit) {
    val book = activeBook
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Continue")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(YomuTheme.radius.panel))
                .background(YomuTheme.colors.surface)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = { onBookLongPress(book) },
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book = book, modifier = Modifier.width(92.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = book.title,
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    color = YomuTheme.colors.textSecondary,
                    style = YomuTheme.type.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${(book.progress * 100).toInt()}% · ${book.remaining}",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
                ProgressLine(progress = book.progress)
            }
        }
    }
}

@Composable
internal fun ShelfSection(
    title: String,
    books: List<LibraryBook>,
    onBookLongPress: (LibraryBook) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(end = 20.dp),
        ) {
            items(books) { book ->
                LibraryBookItem(book = book, onLongPress = { onBookLongPress(book) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookItem(book: LibraryBook, onLongPress: () -> Unit) {
    Column(
        modifier = Modifier
            .width(116.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onLongPress,
            ),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        BookCover(book = book, modifier = Modifier.fillMaxWidth())
        Text(
            text = book.title,
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        ProgressLine(progress = book.progress)
    }
}

@Composable
private fun BookCover(book: LibraryBook, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f / 1.6f)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.verticalGradient(book.coverColors))
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.42f)
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.72f))
        )
        Column(modifier = Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = book.shortTitle,
                color = Color.White,
                style = YomuTheme.type.caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.authorLastName,
                color = Color.White.copy(alpha = 0.7f),
                style = YomuTheme.type.mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ProgressLine(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(YomuTheme.colors.textPrimary)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.section,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "View all",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
        )
    }
}
