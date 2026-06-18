package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
fun ContinueReadingCard(book: LibraryBook) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.panel))
            .background(YomuTheme.colors.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCoverImage(book = book, modifier = Modifier.width(68.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Continue reading",
                color = YomuTheme.colors.accent,
                style = YomuTheme.type.caption,
            )
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
            ProgressLine(progress = book.progress)
            Text(
                text = "${(book.progress * 100).toInt()}% · ${book.remaining}",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.mono,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridBookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookCoverImage(book = book, modifier = Modifier.fillMaxWidth())
        Text(
            text = book.title,
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.progress > 0f) {
            ProgressLine(progress = book.progress)
        }
    }
}

@Composable
fun ImportEmptyCard(onImport: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(YomuTheme.colors.surface)
            .border(1.5.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.md))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onImport,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "+",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.title,
            )
            Text(
                text = "Import EPUB",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.body,
            )
        }
    }
}

@Composable
fun GroupSectionHeader(title: String) {
    Text(
        text = title,
        color = YomuTheme.colors.textMuted,
        style = YomuTheme.type.section,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun BookCoverImage(book: LibraryBook, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f / 1.6f)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(book.coverColors))
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.42f)
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.72f))
        )
        Column(modifier = Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
fun ProgressLine(progress: Float) {
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
