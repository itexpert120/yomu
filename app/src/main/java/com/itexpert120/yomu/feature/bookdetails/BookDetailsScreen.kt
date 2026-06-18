package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.ReadingState
import java.io.File

@Composable
fun BookDetailsScreen(
    book: BookDetailsUi?,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onRemove: () -> Unit,
) {
    YomuScreenScaffold(
        title = "Details",
        onBack = onBack,
        trailing = { if (book != null) EditButton(onEdit) },
    ) {
        if (book == null) {
            Text(
                text = "This book is no longer in your library.",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.body,
            )
            return@YomuScreenScaffold
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailCover(book, modifier = Modifier.width(116.dp))
            Column(
                modifier = Modifier.weight(1f).padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = book.title,
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.title,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    color = YomuTheme.colors.textSecondary,
                    style = YomuTheme.type.body,
                )
                book.series?.let { SeriesTag(it) }
                Text(
                    text = book.readingState.statusLabel(),
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            }
        }

        if (book.progress > 0f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailProgress(book.progress)
                Text(
                    text = "${(book.progress * 100).toInt()}% · ${book.remaining}",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.mono,
                )
            }
        }

        book.description?.takeIf { it.isNotBlank() }?.let { description ->
            YomuSettingGroup(title = "About") {
                Text(
                    text = description,
                    color = YomuTheme.colors.textSecondary,
                    style = YomuTheme.type.body,
                )
            }
        }

        YomuButton(
            text = if (book.readingState == ReadingState.Reading) "Resume" else "Read",
            onClick = onRead,
            modifier = Modifier.fillMaxWidth(),
            emphasis = YomuButtonEmphasis.Primary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (book.readingState != ReadingState.Finished) {
                YomuButton(
                    text = "Mark read",
                    onClick = onMarkRead,
                    emphasis = YomuButtonEmphasis.Secondary,
                )
            }
            if (book.readingState != ReadingState.Unread) {
                YomuButton(
                    text = "Mark unread",
                    onClick = onMarkUnread,
                    emphasis = YomuButtonEmphasis.Secondary,
                )
            }
            YomuButton(
                text = "Remove",
                onClick = onRemove,
                emphasis = YomuButtonEmphasis.Ghost,
            )
        }
    }
}

@Composable
private fun EditButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(YomuTheme.colors.surfaceRaised)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Edit details",
            tint = YomuTheme.colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun ReadingState.statusLabel(): String = when (this) {
    ReadingState.Unread -> "Not started"
    ReadingState.Reading -> "In progress"
    ReadingState.Finished -> "Finished"
}

@Composable
private fun SeriesTag(series: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surfaceRaised)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = series, color = YomuTheme.colors.textSecondary, style = YomuTheme.type.caption)
    }
}

@Composable
private fun DetailProgress(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(YomuTheme.colors.accent),
        )
    }
}

@Composable
private fun DetailCover(book: BookDetailsUi, modifier: Modifier = Modifier) {
    val coverModifier = modifier
        .aspectRatio(1f / 1.6f)
        .clip(RoundedCornerShape(YomuTheme.radius.md))
        .background(Brush.verticalGradient(book.coverColors))

    if (book.coverImagePath != null) {
        AsyncImage(
            model = File(book.coverImagePath),
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier = coverModifier,
        )
        return
    }

    Box(modifier = coverModifier.padding(12.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.42f)
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.72f)),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = book.title,
                color = Color.White,
                style = YomuTheme.type.caption,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
