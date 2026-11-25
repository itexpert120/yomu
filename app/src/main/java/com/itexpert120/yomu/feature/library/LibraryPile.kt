package com.itexpert120.yomu.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.stackCoverShadow
import com.itexpert120.yomu.core.designsystem.yomuPressable

/** Where the pile's reading info sits relative to the covers. */
internal enum class PileLayout {
    /** Phone portrait: covers on the left, the info at the right edge. */
    Phone,

    /** Tablet: the info right beside the covers, the rest of the desk left open. */
    Beside,

    /** Landscape phone: a 120dp-wide pile with the info underneath, in a column beside the shelf. */
    Column,
}

private const val SLOT_ROTATION = 3.5f
private val FlickDistance = 48.dp

/** Cover size and the per-slot offsets, scaled together so the pile keeps its shape at any size. */
private class PileMetrics(val coverWidth: Dp) {
    val coverHeight = coverWidth * 1.5f
    val slotX = coverWidth * (31f / 150f)
    val slotY = coverWidth * (5f / 150f)
    val boxWidth = coverWidth + slotX * 2 + coverWidth * 0.12f
    val boxHeight = coverHeight + coverWidth * (25f / 150f)
}

private val PhoneMetrics = PileMetrics(150.dp)
private val ColumnMetrics = PileMetrics(120.dp)

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
    layout: PileLayout = PileLayout.Phone,
    gutter: Dp = 24.dp,
) {
    if (books.isEmpty()) return
    var front by rememberSaveable(books.map { it.id }) { mutableIntStateOf(0) }
    val frontIndex = front.coerceIn(0, books.lastIndex)
    val frontBook = books[frontIndex]
    val metrics = if (layout == PileLayout.Column) ColumnMetrics else PhoneMetrics
    val covers: @Composable () -> Unit = {
        PileCovers(
            books = books,
            frontIndex = frontIndex,
            metrics = metrics,
            onFlick = { step -> front = (frontIndex + step) % books.size },
            onOpenDetails = onOpenDetails,
        )
    }
    val count = if (books.size > 1) "${frontIndex + 1} of ${books.size}" else " "

    when (layout) {
        PileLayout.Column -> Column(modifier = modifier.padding(horizontal = gutter)) {
            covers()
            Row(
                modifier = Modifier
                    .width(metrics.boxWidth)
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = frontBook.statusLabel,
                        style = StackType.Figure.copy(fontSize = 22.sp, lineHeight = 24.sp, letterSpacing = (-0.6).sp),
                        color = YomuTheme.stack.ink,
                        maxLines = 1,
                    )
                    Text(
                        text = frontBook.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = YomuTheme.stack.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                ResumeButton(title = frontBook.title, size = 52.dp, onClick = { onResume(frontBook.id) })
            }
        }

        PileLayout.Phone, PileLayout.Beside -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = gutter),
            verticalAlignment = Alignment.Top,
        ) {
            covers()
            if (layout == PileLayout.Phone) Spacer(Modifier.weight(1f)) else Spacer(Modifier.width(28.dp))
            PileInfo(
                count = count,
                book = frontBook,
                alignEnd = layout == PileLayout.Phone,
                onResume = { onResume(frontBook.id) },
            )
        }
    }
}

@Composable
private fun PileCovers(
    books: List<LibraryBook>,
    frontIndex: Int,
    metrics: PileMetrics,
    onFlick: (step: Int) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val flick = with(LocalDensity.current) { FlickDistance.toPx() }
    Box(
        modifier = Modifier
            .size(width = metrics.boxWidth, height = metrics.boxHeight)
            .semantics { contentDescription = "Books in progress" }
            .pointerInput(books.size) {
                if (books.size < 2) return@pointerInput
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onHorizontalDrag = { _, amount -> travelled += amount },
                    onDragEnd = {
                        if (kotlin.math.abs(travelled) >= flick) {
                            onFlick(if (travelled < 0) 1 else books.size - 1)
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
                    metrics = metrics,
                    onClick = { onOpenDetails(book.id) },
                )
            }
        }
    }
}

@Composable
private fun PileInfo(count: String, book: LibraryBook, alignEnd: Boolean, onResume: () -> Unit) {
    Column(
        modifier = Modifier
            .width(if (alignEnd) 118.dp else 200.dp)
            .padding(top = 12.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.bodySmall,
            color = YomuTheme.stack.muted,
        )
        Spacer(Modifier.height(48.dp))
        Text(
            text = book.statusLabel,
            style = StackType.Figure,
            color = YomuTheme.stack.ink,
            maxLines = 1,
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
            color = YomuTheme.stack.body,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(22.dp))
        ResumeButton(title = book.title, size = 64.dp, onClick = onResume)
    }
}

@Composable
private fun PileCover(book: LibraryBook, depth: Int, metrics: PileMetrics, onClick: () -> Unit) {
    // Each cover glides to its slot when the pile turns over; read in the layer, never recomposed.
    val slot by animateFloatAsState(
        targetValue = depth.toFloat(),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "pileSlot",
    )
    val density = LocalDensity.current
    val dx = with(density) { metrics.slotX.toPx() }
    val dy = with(density) { metrics.slotY.toPx() }
    Box(
        modifier = Modifier
            .size(metrics.coverWidth, metrics.coverHeight)
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
private fun ResumeButton(title: String, size: Dp, onClick: () -> Unit) {
    val stack = YomuTheme.stack
    Box(
        modifier = Modifier
            .size(size)
            .yomuPressable(onClick = onClick)
            .clip(CircleShape)
            .background(stack.ink)
            .semantics { contentDescription = "Resume $title" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(StackIcons.PlayArrowFilled, contentDescription = null, tint = stack.onInk, modifier = Modifier.size(size * 0.47f))
    }
}

/** The pile while the library loads: the same shapes, as quiet blocks. */
@Composable
internal fun LibraryPileSkeleton(modifier: Modifier = Modifier, gutter: Dp = 24.dp) {
    val block = YomuTheme.stack.track
    val metrics = PhoneMetrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = gutter),
    ) {
        Box(Modifier.size(width = metrics.boxWidth, height = metrics.boxHeight)) {
            for (depth in 2 downTo 0) {
                Box(
                    Modifier
                        .size(metrics.coverWidth, metrics.coverHeight)
                        .graphicsLayer {
                            translationX = metrics.slotX.toPx() * depth
                            translationY = metrics.slotY.toPx() * depth
                            rotationZ = SLOT_ROTATION * depth
                            transformOrigin = TransformOrigin(0f, 1f)
                            alpha = 1f - 0.2f * depth
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(block),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .width(110.dp)
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            SkeletonBlock(block, width = 36.dp, height = 12.dp)
            SkeletonBlock(block, width = 86.dp, height = 40.dp, modifier = Modifier.padding(top = 58.dp))
            SkeletonBlock(block, width = 70.dp, height = 12.dp, modifier = Modifier.padding(top = 10.dp))
            Box(
                Modifier
                    .padding(top = 22.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(block),
            )
        }
    }
}

@Composable
internal fun SkeletonBlock(color: Color, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
    )
}

/** Stack's library title row: the screen title and its actions (48dp icon buttons). */
@Composable
internal fun LibraryTitleRow(
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onArrange: () -> Unit,
    modifier: Modifier = Modifier,
    gutter: Dp = 24.dp,
    showActions: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = gutter, end = YomuTheme.space.sm, top = 20.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Library",
            style = StackType.Display,
            color = YomuTheme.stack.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (showActions) LibraryActions(onSearch = onSearch, onImport = onImport, onArrange = onArrange)
    }
}

/** Search, import and arrange: on the title row, and on the shelf's tab row once it has pinned. */
@Composable
internal fun RowScope.LibraryActions(
    onSearch: () -> Unit,
    onImport: (() -> Unit)?,
    onArrange: (() -> Unit)?,
) {
    TitleAction(StackIcons.Search, "Search", onSearch)
    if (onImport != null) TitleAction(StackIcons.Add, "Import book", onImport)
    if (onArrange != null) TitleAction(StackIcons.Tune, "Arrange library", onArrange)
}

@Composable
private fun TitleAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(icon, contentDescription = label, tint = YomuTheme.stack.ink)
    }
}
