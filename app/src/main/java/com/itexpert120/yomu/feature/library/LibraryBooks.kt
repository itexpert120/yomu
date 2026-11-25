package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.InstrumentSansCondensed
import com.itexpert120.yomu.core.designsystem.YomuBookCoverAspectRatio
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.stackCoverShadow
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuPressable
import java.io.File

/*
 * One selection language for every library view: while selecting, each cover carries a round mark
 * at its corner; a selected cover settles a little smaller and its mark fills with ink.
 */

/** Stack's grid cell: the cover as an object (one static shadow), title, and a one-word status. */
@Composable
fun GridBookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selecting: Boolean = false,
) {
    Column(
        modifier = modifier
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .semantics { this.selected = selected },
    ) {
        SelectableCover(book = book, selected = selected, selecting = selecting, modifier = Modifier.fillMaxWidth())
        Text(
            text = book.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = YomuTheme.space.xs),
        )
        Text(
            text = book.statusLabel,
            color = YomuTheme.stack.muted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/** One word for where a book stands: Finished, New, or how far in. */
internal val LibraryBook.statusLabel: String
    get() = when {
        progress >= 0.995f -> "Finished"
        progress <= 0f -> "New"
        else -> "${(progress * 100).toInt().coerceIn(1, 99)}%"
    }

private val LibraryBook.inProgress: Boolean get() = progress > 0f && progress < 0.995f

/** Compact: more, smaller covers, each with just its progress underneath. */
@Composable
fun CompactGridBookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selecting: Boolean = false,
) {
    Column(
        modifier = modifier
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .semantics(mergeDescendants = true) {
                contentDescription = book.title
                this.selected = selected
            },
    ) {
        SelectableCover(book = book, selected = selected, selecting = selecting, modifier = Modifier.fillMaxWidth())
        Text(
            text = book.statusLabel,
            color = YomuTheme.stack.muted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** Covers only: nothing but the books; the title and status stay available to accessibility. */
@Composable
fun CoverOnlyGridBookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selecting: Boolean = false,
) {
    SelectableCover(
        book = book,
        selected = selected,
        selecting = selecting,
        modifier = modifier
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .clearAndSetSemantics {
                contentDescription = "${book.title}, ${book.statusLabel.lowercase()}"
                this.selected = selected
            },
    )
}

/**
 * The list row: a 44×66 thumbnail, title, caption, and how far in on the right. [title] may carry
 * emphasis (search matches); [caption] defaults to the author.
 */
@Composable
fun BookListRow(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selecting: Boolean = false,
    title: AnnotatedString = AnnotatedString(book.title),
    titleWeight: FontWeight = FontWeight.SemiBold,
    caption: String = book.author,
    showProgress: Boolean = true,
    divider: Boolean = true,
) {
    val stack = YomuTheme.stack
    val container = rememberRowContainer(selected)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .semantics { this.selected = selected }
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                drawRect(container.value)
                if (divider) {
                    val rule = 1.dp.toPx()
                    drawRect(stack.rule, topLeft = Offset(0f, size.height - rule), size = size.copy(height = rule))
                }
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectableCover(
            book = book,
            selected = selected,
            selecting = selecting,
            thumbnail = true,
            modifier = Modifier.size(width = 44.dp, height = 66.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = stack.ink,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = titleWeight),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    color = stack.muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (showProgress) {
            if (book.inProgress) {
                Text(
                    text = book.statusLabel,
                    color = stack.ink,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = InstrumentSansCondensed),
                )
            } else {
                Text(text = book.statusLabel, color = stack.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * A cover that carries the selection treatment: while [selecting], a round mark sits on its corner;
 * once [selected], the cover settles smaller and the mark fills with ink.
 */
@Composable
private fun SelectableCover(
    book: LibraryBook,
    selected: Boolean,
    selecting: Boolean,
    modifier: Modifier = Modifier,
    thumbnail: Boolean = false,
) {
    val scale = rememberCoverScale(selected)
    val shape = if (thumbnail) RoundedCornerShape(3.dp) else YomuShapeTokens.cover
    Box(modifier = modifier) {
        BookCoverImage(
            book = book,
            shape = shape,
            showTitle = !thumbnail,
            modifier = Modifier
                .fillMaxWidth()
                // Read in the draw phase, so settling the cover never recomposes the card.
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .stackCoverShadow(shape),
        )
        SelectionMark(selecting = selecting, selected = selected, small = thumbnail)
    }
}

@Composable
private fun BoxScope.SelectionMark(selecting: Boolean, selected: Boolean, small: Boolean) {
    val animate = yomuAnimationsEnabled()
    val stack = YomuTheme.stack
    val fill by animateColorAsState(
        targetValue = if (selected) stack.ink else Color.Black.copy(alpha = 0.25f),
        animationSpec = if (animate) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
        label = "selectionMarkFill",
    )
    AnimatedVisibility(
        visible = selecting,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 4.dp, y = (-4).dp),
        enter = if (animate) fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) else EnterTransition.None,
        exit = if (animate) fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) else ExitTransition.None,
    ) {
        val size = if (small) 20.dp else 26.dp
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .drawBehind { drawRect(fill) }
                .border(1.5.dp, if (selected) stack.sheet else Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = StackIcons.Check,
                    contentDescription = null,
                    tint = stack.onInk,
                    modifier = Modifier.size(if (small) 14.dp else 16.dp),
                )
            }
        }
    }
}

/**
 * A book's cover at 2:3. Without an extracted cover it is a plain block in the book's own colour
 * with its title and author set on it (no drawn ornament).
 */
@Composable
internal fun BookCoverImage(
    book: LibraryBook,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = YomuShapeTokens.cover,
    // Thumbnails are too small to set a title on; they show the colour alone.
    showTitle: Boolean = true,
) {
    Box(
        modifier = modifier
            .aspectRatio(YomuBookCoverAspectRatio)
            .clip(shape)
            .background(book.coverColors.firstOrNull() ?: YomuTheme.stack.raised),
    ) {
        if (book.coverImagePath != null) {
            AsyncImage(
                model = File(book.coverImagePath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showTitle) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(YomuTheme.space.xs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = book.shortTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.authorLastName,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Selected covers settle smaller on the fast spatial spring. */
@Composable
private fun rememberCoverScale(selected: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (selected) 0.88f else 1f,
    animationSpec = if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
    label = "coverScale",
)

/** A selected list row sits on the raised surface. */
@Composable
private fun rememberRowContainer(selected: Boolean): State<Color> = animateColorAsState(
    targetValue = if (selected) YomuTheme.stack.raised else Color.Transparent,
    animationSpec = if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
    label = "rowContainer",
)
