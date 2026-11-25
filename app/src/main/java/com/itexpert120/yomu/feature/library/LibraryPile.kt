package com.itexpert120.yomu.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.stackCoverShadow
import com.itexpert120.yomu.core.designsystem.yomuPressable

private val CoverWidth = 150.dp
private val CoverHeight = 225.dp
private val SlotOffsetX = 31.dp
private val SlotOffsetY = 5.dp
private const val SLOT_ROTATION = 3.5f
private val FlickDistance = 48.dp

/**
 * Stack's library hero: the books in progress as a tilted pile on the desk (up to three, the front
 * one upright, each one behind offset, turned and a little darker), with where you are in the
 * front book and a play button beside it. A horizontal flick brings the next book to the front;
 * tapping the front cover opens its details, play resumes it.
 */
@Composable
internal fun LibraryPile(
    books: List<LibraryBook>,
    onOpenDetails: (String) -> Unit,
    onResume: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (books.isEmpty()) return
    var front by rememberSaveable(books.map { it.id }) { mutableIntStateOf(0) }
    val frontIndex = front.coerceIn(0, books.lastIndex)
    val frontBook = books[frontIndex]
    val haptics = LocalHapticFeedback.current
    val flick = with(LocalDensity.current) { FlickDistance.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = YomuTheme.space.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(width = CoverWidth + SlotOffsetX * 2 + 18.dp, height = CoverHeight + 25.dp)
                .semantics { contentDescription = "Books in progress" }
                .pointerInput(books.size) {
                    if (books.size < 2) return@pointerInput
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { travelled = 0f },
                        onHorizontalDrag = { _, amount -> travelled += amount },
                        onDragEnd = {
                            if (kotlin.math.abs(travelled) >= flick) {
                                val step = if (travelled < 0) 1 else books.size - 1
                                front = (frontIndex + step) % books.size
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        },
                    )
                },
        ) {
            // Back to front, so the front cover draws last.
            for (depth in books.indices.reversed()) {
                val book = books[(frontIndex + depth) % books.size]
                key(book.id) {
                    PileCover(
                        book = book,
                        depth = depth,
                        onClick = { onOpenDetails(book.id) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .width(118.dp)
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = if (books.size > 1) "${frontIndex + 1} of ${books.size}" else " ",
                style = MaterialTheme.typography.bodySmall,
                color = YomuTheme.stack.muted,
            )
            Spacer(Modifier.height(48.dp))
            Text(
                text = frontBook.statusLabel,
                style = StackType.Figure,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = frontBook.title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = YomuTheme.stack.body,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(22.dp))
            ResumeButton(title = frontBook.title, onClick = { onResume(frontBook.id) })
        }
    }
}

@Composable
private fun PileCover(book: LibraryBook, depth: Int, onClick: () -> Unit) {
    // Each cover glides to its slot when the pile turns over; read in the layer, never recomposed.
    val slot by animateFloatAsState(
        targetValue = depth.toFloat(),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "pileSlot",
    )
    val density = LocalDensity.current
    val dx = with(density) { SlotOffsetX.toPx() }
    val dy = with(density) { SlotOffsetY.toPx() }
    Box(
        modifier = Modifier
            .size(CoverWidth, CoverHeight)
            .graphicsLayer {
                translationX = dx * slot
                translationY = dy * slot
                rotationZ = SLOT_ROTATION * slot
                transformOrigin = TransformOrigin(0f, 1f)
            }
            .stackCoverShadow()
            .then(if (depth == 0) Modifier.yomuPressable(onClick = onClick) else Modifier),
    ) {
        BookCoverImage(book = book, modifier = Modifier.fillMaxSize())
        // Covers further back sit a little darker (brightness .95, .9).
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent { drawRect(Color.Black.copy(alpha = (0.05f * slot).coerceIn(0f, 0.12f))) },
        )
    }
}

@Composable
private fun ResumeButton(title: String, onClick: () -> Unit) {
    val stack = YomuTheme.stack
    Box(
        modifier = Modifier
            .size(64.dp)
            .yomuPressable(onClick = onClick)
            .clip(CircleShape)
            .background(stack.ink)
            .semantics { contentDescription = "Resume $title" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = stack.onInk, modifier = Modifier.size(30.dp))
    }
}

/** Stack's library title row: the screen title and its three actions (48dp icon buttons). */
@Composable
internal fun LibraryTitleRow(
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onArrange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = YomuTheme.space.lg, end = YomuTheme.space.sm, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Library",
            style = StackType.Display,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TitleAction(Icons.Rounded.Search, "Search", onSearch)
        TitleAction(Icons.Rounded.Add, "Import book", onImport)
        TitleAction(Icons.Rounded.Tune, "Arrange library", onArrange)
    }
}

@Composable
private fun TitleAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}
