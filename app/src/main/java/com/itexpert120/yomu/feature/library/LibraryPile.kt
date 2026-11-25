package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackTitleRow
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.stackCoverShadow
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuPressable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Where the pile's reading info sits relative to the covers, and the gaps around it (from the
 * boards): [topGap] under the title row, [sheetGap] from the pile's box to the shelf sheet.
 */
internal enum class PileLayout(val topGap: Dp, val sheetGap: Dp) {
    /** Phone portrait: covers on the left, the info at the right edge. */
    Phone(topGap = 24.dp, sheetGap = 46.dp),

    /** Portrait tablet: as the phone, larger (176dp covers, a 52 figure). */
    Tall(topGap = 26.dp, sheetGap = 40.dp),

    /** Landscape tablet column: the info right beside the covers, the rest of the desk left open. */
    Beside(topGap = 20.dp, sheetGap = 38.dp),

    /** Landscape phone: a 120dp-wide pile with the info underneath, in a column beside the shelf. */
    Column(topGap = 0.dp, sheetGap = 0.dp),
}

private const val SLOT_ROTATION = 3.5f

/** The pile always shows this many slots; books fill them from the front. */
private const val PileSlots = 3

/** Cover size and the per-slot offsets, scaled together so the pile keeps its shape at any size. */
private class PileMetrics(val coverWidth: Dp) {
    val coverHeight = coverWidth * 1.5f
    val slotX = coverWidth * (31f / 150f)
    val slotY = coverWidth * (5f / 150f)
    val boxWidth = coverWidth + slotX * 2 + coverWidth * 0.12f
    val boxHeight = coverHeight + coverWidth * (25f / 150f)
}

private val PhoneMetrics = PileMetrics(150.dp)
private val TallMetrics = PileMetrics(176.dp)
private val ColumnMetrics = PileMetrics(120.dp)

/**
 * Stack's library hero: the books in progress as a tilted pile on the desk (up to three, the front
 * one upright, each one behind offset, turned and a little darker), with where you are in the
 * front book and a play button beside it. Tapping the front cover opens its details, play resumes
 * it. A horizontal swipe turns the pile over: the front cover follows the finger off to the start
 * while the covers behind move up a slot with it (dragging toward the end brings the previous book
 * back from the start). The pile is a loop, so it turns the same way at every book.
 */
@Composable
internal fun LibraryPile(
    books: List<LibraryBook>,
    onOpenDetails: (String) -> Unit,
    onResume: (String) -> Unit,
    modifier: Modifier = Modifier,
    layout: PileLayout = PileLayout.Phone,
    gutter: Dp = 24.dp,
    // Where each book is ("Chapter VII"), by id; the title stands in for a book not indexed yet.
    chapters: Map<String, String> = emptyMap(),
) {
    if (books.isEmpty()) return
    val ids = books.map { it.id }
    // The front book survives a configuration change, and the pile's books changing around it.
    var savedFront by rememberSaveable { mutableStateOf<String?>(null) }
    val pile = remember(ids) { PileState(start = ids.indexOf(savedFront).coerceAtLeast(0), count = books.size) }
    val frontIndex by remember(pile) { derivedStateOf { pile.nearest } }
    val frontBook = books[frontIndex.coerceIn(0, books.lastIndex)]
    val metrics = when (layout) {
        PileLayout.Column -> ColumnMetrics
        PileLayout.Tall -> TallMetrics
        else -> PhoneMetrics
    }
    val where: (LibraryBook) -> String = { book -> chapters[book.id] ?: book.title }
    val covers: @Composable () -> Unit = {
        PileCovers(
            books = books,
            pile = pile,
            metrics = metrics,
            onSettled = { index -> savedFront = books.getOrNull(index)?.id },
            onOpenDetails = onOpenDetails,
        )
    }

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
                PileText(book = frontBook, modifier = Modifier.weight(1f)) { book ->
                    Column {
                        Text(
                            text = book.statusLabel,
                            style = StackType.Figure.copy(fontSize = 22.sp, lineHeight = 24.sp, letterSpacing = (-0.6).sp),
                            color = YomuTheme.stack.ink,
                            maxLines = 1,
                        )
                        Text(
                            text = where(book),
                            style = MaterialTheme.typography.bodySmall,
                            color = YomuTheme.stack.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                ResumeButton(title = frontBook.title, size = 52.dp, onClick = { onResume(frontBook.id) })
            }
        }

        PileLayout.Phone, PileLayout.Tall, PileLayout.Beside -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = gutter),
            verticalAlignment = Alignment.Top,
        ) {
            covers()
            if (layout == PileLayout.Beside) Spacer(Modifier.width(PileInfoGap)) else Spacer(Modifier.weight(1f))
            PileInfo(
                index = frontIndex,
                count = books.size,
                book = frontBook,
                where = where,
                layout = layout,
                onResume = { onResume(frontBook.id) },
            )
        }
    }
}

/** Gap between the pile's box and its info column beside it (landscape tablets): 268dp from the pile's start. */
private val PileInfoGap = 38.dp

/**
 * Where the pile is turned to: [position] counts books brought to the front (fractional while a
 * swipe or its settle is under way; it can run past the book count or below zero, as the pile is a
 * loop). Read in layers only, so turning the pile never recomposes it except when the front
 * changes.
 */
@Stable
private class PileState(start: Int, val count: Int) {
    var position by mutableFloatStateOf(start.toFloat())

    /** Where the last release is heading; a swipe that starts mid-settle continues from there. */
    var target = start

    var settling by mutableStateOf(false)
    var settle: Job? = null

    /** The book (0 until [count]) closest to the front right now. */
    val nearest: Int get() = Math.floorMod(position.roundToInt(), count)
}

/**
 * Where a released swipe settles, in books brought to the front: one step on from [start] toward
 * the side it was flung ([velocity], books per second) once it is fast enough, else toward the side
 * it was dragged ([position] - [start]) once that's far enough, else back to [start]. Never more
 * than one step from [start], so a single swipe turns one book.
 */
internal fun pileSettleTarget(
    start: Int,
    position: Float,
    velocity: Float,
    distanceThreshold: Float = PileCommitDistance,
    velocityThreshold: Float = PileCommitVelocity,
): Int {
    val dragged = position - start
    val step = when {
        abs(velocity) >= velocityThreshold -> sign(velocity).toInt()
        abs(dragged) >= distanceThreshold -> sign(dragged).toInt()
        else -> 0
    }
    return start + step
}

/** A swipe commits past this share of a cover's width… */
internal const val PileCommitDistance = 0.3f

/** …or when flung faster than this many cover widths a second (about 400dp/s on a phone). */
internal const val PileCommitVelocity = 2.6f

@Composable
private fun PileCovers(
    books: List<LibraryBook>,
    pile: PileState,
    metrics: PileMetrics,
    onSettled: (index: Int) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val count = books.size
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animate = yomuAnimationsEnabled()
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // One cover width (and a little) of finger travel turns the pile by one book, so the front
    // cover follows the finger 1:1.
    val travel = with(LocalDensity.current) { metrics.coverWidth.toPx() * TravelFactor }
    val currentOnSettled by rememberUpdatedState(onSettled)
    // Only the whole part is read in composition: the covers' order changes once per book.
    val base by remember(pile) { derivedStateOf { floor(pile.position).toInt() } }
    var dragStart by remember(pile) { mutableIntStateOf(pile.target) }

    fun settleTo(target: Int, velocity: Float) {
        pile.target = target
        pile.settle?.cancel()
        pile.settle = scope.launch {
            pile.settling = true
            try {
                if (animate) {
                    animate(pile.position, target.toFloat(), velocity, spring) { value, _ -> pile.position = value }
                } else {
                    pile.position = target.toFloat()
                }
                currentOnSettled(Math.floorMod(target, count))
            } finally {
                pile.settling = false
            }
        }
    }

    val dragState = rememberDraggableState { delta -> pile.position -= delta / travel }
    Box(
        modifier = Modifier
            .size(width = metrics.boxWidth, height = metrics.boxHeight)
            .semantics {
                contentDescription = "Books in progress"
                if (count > 1) {
                    customActions = listOf(
                        CustomAccessibilityAction("Next book") {
                            settleTo(pile.target + 1, 0f)
                            true
                        },
                        CustomAccessibilityAction("Previous book") {
                            settleTo(pile.target - 1, 0f)
                            true
                        },
                    )
                }
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = count > 1,
                // Catching a pile that is still settling takes it without waiting for touch slop.
                startDragImmediately = pile.settling,
                onDragStarted = {
                    // A swipe during a settle continues from where that settle was heading, so a
                    // quick double swipe turns two books.
                    dragStart = if (pile.settling) pile.target else pile.position.roundToInt()
                    pile.settle?.cancel()
                },
                onDragStopped = { velocity ->
                    val target = pileSettleTarget(dragStart, pile.position, -velocity / travel)
                    if (target != dragStart) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    settleTo(target, -velocity / travel)
                },
            ),
    ) {
        // A pile of one or two still reads as a pile: the empty slots behind hold quiet blanks.
        for (depth in PileSlots - 1 downTo count) {
            key("blank-$depth") { PileBlank(depth = depth, metrics = metrics) }
        }
        if (count > 1) {
            // The front book's second copy, arriving at the back as the front one leaves (and the
            // other way round). Drawn first, behind everything.
            val front = books[Math.floorMod(base, count)]
            key("back-${front.id}") {
                PileCover(book = front, slot = count, base = base, pile = pile, metrics = metrics, onClick = null)
            }
        }
        // Back to front, so the front cover draws last. Keys follow the book, so a cover keeps its
        // node (and loaded image) as it moves between slots.
        for (slot in count - 1 downTo 0) {
            val book = books[Math.floorMod(base + slot, count)]
            key(book.id) {
                PileCover(
                    book = book,
                    slot = slot,
                    base = base,
                    pile = pile,
                    metrics = metrics,
                    onClick = if (slot == 0) ({ onOpenDetails(book.id) }) else null,
                )
            }
        }
    }
}

/** How far (in cover widths) the front cover travels off the pile while it turns over. */
private const val TravelFactor = 1.06f

/** The front cover tilts this far (degrees) as it leaves toward the start. */
private const val LeaveRotation = -4f

/** "N of M", the figure and where you are in the book, as on the boards. */
@Composable
private fun PileInfo(
    index: Int,
    count: Int,
    book: LibraryBook,
    where: (LibraryBook) -> String,
    layout: PileLayout,
    onResume: () -> Unit,
) {
    val alignEnd = layout != PileLayout.Beside
    val tall = layout == PileLayout.Tall
    Column(
        modifier = Modifier
            .width(
                when (layout) {
                    PileLayout.Beside -> 200.dp
                    PileLayout.Tall -> 180.dp
                    else -> 110.dp
                },
            )
            .padding(top = 12.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = if (count > 1) "${index + 1} of $count" else " ",
            style = MaterialTheme.typography.bodySmall,
            color = YomuTheme.stack.muted,
        )
        Spacer(Modifier.height(if (tall) 84.dp else 56.dp))
        PileText(book = book, modifier = Modifier.fillMaxWidth()) { shown ->
            Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
                Text(
                    text = shown.statusLabel,
                    style = if (tall) TallFigure else StackType.Figure,
                    color = YomuTheme.stack.ink,
                    maxLines = 1,
                )
                Text(
                    text = where(shown),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = if (tall) 14.sp else 13.sp,
                        lineHeight = if (tall) 18.sp else 17.sp,
                    ),
                    color = YomuTheme.stack.body,
                    textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                    // One line, so a long chapter name never moves the play button.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        ResumeButton(title = book.title, size = 64.dp, onClick = onResume)
    }
}

/** The portrait tablet's larger figure (52/52). */
private val TallFigure = StackType.Figure.copy(fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-1.8).sp)

/** The front book's text, cross-faded in place (same size either way) when the front changes. */
@Composable
private fun PileText(
    book: LibraryBook,
    modifier: Modifier = Modifier,
    content: @Composable (LibraryBook) -> Unit,
) {
    val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = book,
        contentKey = { it.id },
        transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) using SizeTransform(clip = false) { _, _ -> snap() } },
        label = "pileText",
        modifier = modifier,
    ) { shown -> content(shown) }
}

/**
 * One cover of the pile at [slot] (0 = front) while the pile's whole turn count is [base]; its
 * depth is the slot less how far the pile has turned past [base], read in the layer. Below 0 the
 * cover is leaving the front toward the start (fading as it goes); past the last slot it is the
 * front book's copy arriving at the back (fading in).
 */
@Composable
private fun PileCover(
    book: LibraryBook,
    slot: Int,
    base: Int,
    pile: PileState,
    metrics: PileMetrics,
    onClick: (() -> Unit)?,
) {
    val density = LocalDensity.current
    val dx = with(density) { metrics.slotX.toPx() }
    val dy = with(density) { metrics.slotY.toPx() }
    val travel = with(density) { metrics.coverWidth.toPx() * TravelFactor }
    val last = (pile.count - 1).toFloat()
    val depth = { slot - (pile.position - base).coerceIn(0f, 1f) }
    Box(
        modifier = Modifier
            .size(metrics.coverWidth, metrics.coverHeight)
            .graphicsLayer {
                val d = depth()
                transformOrigin = TransformOrigin(0f, 1f)
                if (d < 0f) {
                    translationX = travel * d
                    rotationZ = LeaveRotation * -d
                    alpha = 1f + d
                } else {
                    translationX = dx * d
                    translationY = dy * d
                    rotationZ = SLOT_ROTATION * d
                    alpha = if (d > last) (1f - (d - last)).coerceIn(0f, 1f) else 1f
                }
            }
            .stackCoverShadow()
            .then(if (onClick != null) Modifier.yomuPressable(onClick = onClick, shape = YomuShapeTokens.cover) else Modifier),
    ) {
        BookCoverImage(book = book, modifier = Modifier.fillMaxSize())
        // Covers further back sit a little darker (brightness .95, .9).
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent { drawRect(Color.Black.copy(alpha = (0.05f * depth()).coerceIn(0f, 0.12f))) },
        )
    }
}

/**
 * An empty slot of the pile: a cover-shaped blank in the raised colour, turned and offset like a
 * cover at that depth, with a much softer shadow than a real cover so it stays in the background.
 */
@Composable
private fun PileBlank(depth: Int, metrics: PileMetrics) {
    val raised = YomuTheme.stack.raised
    val shadow = Color.Black.copy(alpha = if (YomuTheme.stack.isDark) 0.28f else 0.1f)
    Box(
        modifier = Modifier
            .size(metrics.coverWidth, metrics.coverHeight)
            .graphicsLayer {
                translationX = metrics.slotX.toPx() * depth
                translationY = metrics.slotY.toPx() * depth
                rotationZ = SLOT_ROTATION * depth
                transformOrigin = TransformOrigin(0f, 1f)
            }
            .dropShadow(YomuShapeTokens.cover, Shadow(radius = 16.dp, spread = (-8).dp, offset = DpOffset(0.dp, 8.dp), color = shadow))
            .clip(YomuShapeTokens.cover)
            .background(raised),
    )
}

@Composable
private fun ResumeButton(title: String, size: Dp, onClick: () -> Unit) {
    val stack = YomuTheme.stack
    Box(
        modifier = Modifier
            .size(size)
            .yomuPressable(onClick = onClick, shape = CircleShape)
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

/** The library's title row (the shared top-level title row) with its actions. */
@Composable
internal fun LibraryTitleRow(
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onArrange: () -> Unit,
    modifier: Modifier = Modifier,
    gutter: Dp = 24.dp,
    showActions: Boolean = true,
    collapse: (() -> Float)? = null,
) {
    StackTitleRow(
        title = "Library",
        modifier = modifier,
        gutter = gutter,
        collapse = collapse,
        actions = if (showActions) {
            { LibraryActions(onSearch = onSearch, onImport = onImport, onArrange = onArrange) }
        } else {
            null
        },
    )
}

/** Search, import and arrange: on the title row and the compact header (and the landscape tab row). */
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
