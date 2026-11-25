package com.itexpert120.yomu.reader.ui

import android.graphics.Typeface
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.reader.epub.EpubBook
import com.itexpert120.yomu.reader.layout.ChapterLayout
import com.itexpert120.yomu.reader.layout.PageSpec
import com.itexpert120.yomu.reader.text.ChapterText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sign

/** A place in the book: chapter (reading-order index) and page within it. */
data class PagePosition(val chapter: Int, val page: Int)

/**
 * Reads [book] one page at a time with native layout. Pages are drawn straight from the chapter's
 * text layout; a turn slides the leaving page off over the next one (or the previous page back on
 * top) under the finger, finished or cancelled by a spring that keeps the finger's speed.
 */
@Composable
fun PagedReader(
    book: EpubBook,
    startChapter: Int,
    typeface: Typeface,
    italicTypeface: Typeface?,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onFirstPage: () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize().background(background).clipToBounds()) {
        val density = LocalDensity.current
        val spec = remember(constraints.maxWidth, constraints.maxHeight, typeface, italicTypeface, textColor) {
            with(density) {
                PageSpec(
                    width = constraints.maxWidth,
                    height = constraints.maxHeight,
                    // Readium's defaults today: 16dp text, CSS line-height 1.5, 1em paragraph
                    // spacing, 20dp side margins, left-aligned without hyphenation.
                    marginHorizontal = 20.dp.roundToPx(),
                    marginTop = 20.dp.roundToPx(),
                    marginBottom = 36.dp.roundToPx(),
                    textSizePx = 16.sp.toPx(),
                    lineHeightEm = 1.5f,
                    paragraphGapEm = 1f,
                    textColor = textColor.toArgb(),
                    typeface = typeface,
                    italicTypeface = italicTypeface,
                )
            }
        }
        val chapters = remember(spec) { mutableStateMapOf<Int, ChapterLayout>() }
        var position by remember { mutableStateOf(PagePosition(startChapter.coerceIn(book.spine.indices), 0)) }

        // Lay out the current chapter first, then its neighbours, so turns across a chapter
        // boundary never wait.
        LaunchedEffect(spec, position.chapter) {
            for (index in listOf(position.chapter, position.chapter + 1, position.chapter - 1)) {
                if (index !in book.spine.indices || chapters.containsKey(index)) continue
                chapters[index] = withContext(Dispatchers.Default) { layoutChapter(book, index, spec) }
            }
        }

        fun next(of: PagePosition): PagePosition? {
            val layout = chapters[of.chapter] ?: return null
            if (of.page + 1 < layout.pages.size) return of.copy(page = of.page + 1)
            return if (chapters.containsKey(of.chapter + 1)) PagePosition(of.chapter + 1, 0) else null
        }

        fun previous(of: PagePosition): PagePosition? {
            if (of.page > 0) return of.copy(page = of.page - 1)
            val previousLayout = chapters[of.chapter - 1] ?: return null
            return PagePosition(of.chapter - 1, previousLayout.pages.lastIndex)
        }

        val width = constraints.maxWidth.toFloat()
        // Negative: the current page is leaving to the left (forward). Positive: the previous page
        // is arriving from the left (backward).
        val offset = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val turnSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)

        suspend fun settle(velocity: Float) {
            val value = offset.value
            val target = when {
                value < 0 && next(position) != null && (value < -width * 0.25f || velocity < -FLING_PX_PER_S) -> -width
                value > 0 && previous(position) != null && (value > width * 0.25f || velocity > FLING_PX_PER_S) -> width
                else -> 0f
            }
            offset.animateTo(target, turnSpring, initialVelocity = velocity)
            if (target != 0f) {
                position = if (target < 0) next(position)!! else previous(position)!!
                offset.snapTo(0f)
            }
        }

        // Composition only needs the turn's direction; the offset itself is read while drawing.
        val direction by remember { derivedStateOf { sign(offset.value) } }
        val current = position
        val shade = Color.Black.copy(alpha = 0.10f)
        val moving = Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = if (offset.value > 0) offset.value - size.width else offset.value }
            .drawBehind {
                // A soft edge on the moving page, so it reads as a sheet over the other one.
                val edge = 14.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(listOf(shade, Color.Transparent), startX = size.width, endX = size.width + edge),
                    topLeft = Offset(size.width, 0f),
                    size = Size(edge, size.height),
                )
            }
            .background(background)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        if (offset.isRunning) return@detectTapGestures
                        scope.launch {
                            when {
                                tap.x > size.width * 2 / 3 && next(position) != null -> {
                                    offset.snapTo(-1f)
                                    settle(-FLING_PX_PER_S * 2)
                                }
                                tap.x < size.width / 3 && previous(position) != null -> {
                                    offset.snapTo(1f)
                                    settle(FLING_PX_PER_S * 2)
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        // Only a drag past the touch slop turns pages; a tap is left to the tap zones.
                        var overSlop = 0f
                        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            overSlop = over
                        } ?: return@awaitEachGesture
                        tracker.addPosition(drag.uptimeMillis, drag.position)
                        fun follow(dx: Float) {
                            val proposed = offset.value + dx
                            // A little resistance where there's no page to turn to.
                            val allowed = when {
                                proposed < 0 && next(position) == null -> offset.value + dx * 0.2f
                                proposed > 0 && previous(position) == null -> offset.value + dx * 0.2f
                                else -> proposed
                            }
                            scope.launch { offset.snapTo(allowed.coerceIn(-width, width)) }
                        }
                        follow(overSlop)
                        horizontalDrag(drag.id) { change ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            follow(change.positionChange().x)
                            change.consume()
                        }
                        val velocity = tracker.calculateVelocity().x
                        scope.launch { settle(velocity) }
                    }
                },
        ) {
            when {
                // Forward: the next page waits underneath while the current one leaves.
                direction < 0 -> {
                    next(current)?.let { key(it) { Page(chapters, it, Modifier.fillMaxSize()) } }
                    key(current) { Page(chapters, current, moving) }
                }
                // Backward: the previous page comes back on top of the current one.
                direction > 0 -> {
                    key(current) { Page(chapters, current, Modifier.fillMaxSize()) }
                    previous(current)?.let { key(it) { Page(chapters, it, moving) } }
                }
                else -> key(current) { Page(chapters, current, Modifier.fillMaxSize(), onDrawn = onFirstPage) }
            }
        }
    }
}

@Composable
private fun Page(
    chapters: Map<Int, ChapterLayout>,
    position: PagePosition,
    modifier: Modifier,
    onDrawn: () -> Unit = {},
) {
    val layout = chapters[position.chapter]
    var reported by remember { mutableStateOf(false) }
    Spacer(
        modifier.drawWithContent {
            drawIntoCanvas { canvas -> layout?.drawPage(canvas.nativeCanvas, position.page) }
            if (layout != null && !reported) {
                reported = true
                onDrawn()
            }
        },
    )
}

private const val FLING_PX_PER_S = 1200f

private fun layoutChapter(book: EpubBook, index: Int, spec: PageSpec): ChapterLayout {
    val t0 = System.nanoTime()
    val text = ChapterText.parse(book.readText(book.spine[index].path))
    val t1 = System.nanoTime()
    val layout = ChapterLayout(text, spec)
    val t2 = System.nanoTime()
    Log.i(
        "NativeReader",
        "chapter $index: parse ${(t1 - t0) / 1_000_000} ms, layout ${(t2 - t1) / 1_000_000} ms, " +
            "${text.length} chars, ${layout.pages.size} pages",
    )
    return layout
}
