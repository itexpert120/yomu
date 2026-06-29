package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuPressable
import java.io.File

@Composable
fun ContinueReadingCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Flush hero (no card chrome): a large cover anchors the most-recent book, with the title,
    // progress and a clear Resume action beside it. Capped so it doesn't stretch on a wide tablet.
    // Tapping the card opens details; the Resume pill jumps straight back into the reader.
    val colors = YomuTheme.colors
    Row(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .yomuPressable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCoverImage(book = book, modifier = Modifier.width(96.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = book.title,
                color = colors.textPrimary,
                style = YomuTheme.type.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author,
                color = colors.textSecondary,
                style = YomuTheme.type.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            ProgressLine(progress = book.progress)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // remaining already reads as "5% read" / "Finished" — don't restate the percent.
                    text = book.remaining,
                    color = colors.textMuted,
                    style = YomuTheme.type.mono,
                    modifier = Modifier.weight(1f),
                )
                ResumePill(onClick = onResume)
            }
        }
    }
}

/** The hero's primary call-to-action: a filled pill that resumes the book. */
@Composable
private fun ResumePill(onClick: () -> Unit) {
    val colors = YomuTheme.colors
    Row(
        modifier = Modifier
            .yomuPressable(onClick = onClick)
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(colors.textPrimary)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = colors.appBackground,
            modifier = Modifier.size(16.dp),
        )
        Text(text = "Resume", color = colors.appBackground, style = YomuTheme.type.control, maxLines = 1)
    }
}

@Composable
fun GridBookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Column(
        modifier = modifier
            .yomuPressable(onClick = onClick, onLongClick = onLongPress),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Progress reads as a thin bar along the cover's bottom edge instead of a
            // separate line under the title.
            BookCoverImage(book = book, modifier = Modifier.fillMaxWidth(), showProgress = true)
            SelectionMarker(selected)
        }
        // Reserve two lines so one- and two-line titles keep card baselines aligned across a row.
        Text(
            text = book.title,
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.caption,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun BookListRow(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(if (selected) YomuTheme.colors.accentSoft else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCoverImage(book = book, modifier = Modifier.width(54.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = book.title,
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author,
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.progress > 0f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ProgressLine(progress = book.progress) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${(book.progress * 100).toInt()}%",
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.mono,
                    )
                }
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(YomuTheme.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = YomuTheme.colors.appBackground,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.SelectionMarker(selected: Boolean) {
    if (!selected) return
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(shape)
            .background(YomuTheme.colors.accent.copy(alpha = 0.22f))
            .border(2.dp, YomuTheme.colors.accent, shape),
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(YomuTheme.colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = YomuTheme.colors.appBackground,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun ImportEmptyCard(onImport: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .yomuPressable(onClick = onImport)
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(YomuTheme.colors.surface)
            .border(1.5.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.md)),
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
fun GroupSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = YomuTheme.colors.textMuted,
        style = YomuTheme.type.section,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
private fun BookCoverImage(
    book: LibraryBook,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f / 1.6f)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(book.coverColors)),
    ) {
        // Real extracted cover when available; otherwise the generated gradient + title placeholder.
        if (book.coverImagePath != null) {
            AsyncImage(
                model = File(book.coverImagePath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.42f)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.72f)),
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
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

        if (showProgress && book.progress > 0f) {
            // A solid badge reads clearly over any cover, unlike a thin bar that blended in.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(YomuTheme.radius.pill))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "${(book.progress * 100).toInt()}%",
                    color = Color.White,
                    style = YomuTheme.type.mono,
                )
            }
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
            .background(YomuTheme.colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(YomuTheme.colors.textPrimary),
        )
    }
}
