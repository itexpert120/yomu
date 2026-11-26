package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.rememberSettlingSpatialSpring
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.feature.bookdetails.BookSheetHold
import com.itexpert120.yomu.feature.bookdetails.BookSheetResume
import com.itexpert120.yomu.feature.bookdetails.LocalBookSheetHold
import com.itexpert120.yomu.feature.bookdetails.bookPaneBesideLibrary
import com.itexpert120.yomu.feature.bookdetails.bookPaneWidth
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * How far (px, from its start edge) the library beside a book pane is visible: its full width
 * [fullPx] less [roomPx] (the pane's width and the desk gap before it) by how far the pane is
 * open ([openness], 0..1). Both in px of the same window.
 */
internal fun libraryVisibleEdge(fullPx: Int, roomPx: Float, openness: Float): Int = (fullPx - (roomPx * openness.coerceIn(0f, 1f)).roundToInt()).coerceIn(0, fullPx)

/** Behind an open book the library dims to 80% (spec: black at 20%, never blur). */
internal const val SheetDim = 0.2f

/**
 * How far a book pane beside the library (medium widths and up) is open, [openness] 0..1, read
 * every frame while the library lays out, so the library's edge follows the pane's edge exactly —
 * opening, closing and through a back gesture 1:1 — and whether the pane is [moving] (its move or
 * a gesture is running), which holds the library grid's column count still until the move ends.
 * The book's frame publishes both; only the page that set them can clear them, so a book swapped
 * into the pane (two book pages briefly composed) never leaves the library full width.
 */
@Stable
internal class BookPaneSpace {
    var openness: (() -> Float)? by mutableStateOf(null)
        private set
    var moving: () -> Boolean = { false }
        private set
    private var owner: Any? = null

    fun publish(by: Any, openness: () -> Float, moving: () -> Boolean) {
        owner = by
        this.moving = moving
        this.openness = openness
    }

    fun release(by: Any) {
        if (owner === by) {
            owner = null
            moving = { false }
            openness = null
        }
    }
}

/**
 * Stack's open-book motion around the book details page. The library is live underneath (it is
 * never removed while a book is open — see [YomuNavHost]), so nothing stands in for it.
 *
 * On a phone the book sheet rises from the bottom on the spatial default spring over the library,
 * which dims to 80% on the effects spring and never moves or scales; the dimmed library takes no
 * touches. Closing is Stack's one back motion ([BackSlide]): the sheet slides back down and the dim
 * lifts with it — following the finger during a back gesture, from rest on a back tap or the close
 * button, and from wherever a drag let go of it ([BookSheetHold], handed over by the sheet).
 *
 * From medium widths it is list-detail: the book pane slides in at the end edge on the slow
 * spatial spring (Stack: panes) while the library column narrows beside it ([BookPaneSpace]);
 * nothing overlaps or dims, and the library stays in use (another book swaps the pane in place).
 * Closing reverses both, a back gesture scrubbing them.
 *
 * Moving on to the editor, or coming back from it or from the reader, leaves the sheet where it is.
 * Resume ([resuming]: the page is leaving for the reader, as a phone sheet) keeps the sheet moving
 * up: it rises to the window's top and squares its corners while the book info scrolls up off it
 * and fades, on the same spring as the reader's page arriving in it ([ReaderEntranceFrame]). The
 * library under it stays dimmed.
 */
@Composable
internal fun AnimatedContentScope.BookSheetFrame(
    motion: BackMotion,
    pane: BookPaneSpace,
    resuming: () -> Boolean,
    content: @Composable (libraryBeside: Boolean, resume: BookSheetResume?) -> Unit,
) {
    val motionEnabled = yomuAnimationsEnabled()
    val scheme = MaterialTheme.motionScheme
    val sheetSpring = rememberSheetSpring()
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val paneMode = bookPaneBesideLibrary(windowWidth)
    val slowSpring = remember(scheme) { scheme.slowSpatialSpec<Float>() }
    val enterSpring = if (paneMode) slowSpring else sheetSpring
    val dimSpring = remember(scheme) { scheme.defaultEffectsSpec<Float>() }
    // 1 = off screen, 0 = at rest: the back clock (and the transition's length while opening).
    val hidden = transition.animateFloat(
        transitionSpec = {
            if (initialState == EnterExitState.PreEnter) enterSpring else tween(YomuMotion.BackSlideMillis, easing = LinearEasing)
        },
        label = "bookSheet",
    ) { state -> if (motionEnabled && state != EnterExitState.Visible) 1f else 0f }
    // Opening runs on its own clock, started only once the page's first frame — its heaviest, where
    // the whole page composes — has been drawn: the sheet waits off screen through that frame and
    // then rises from its very start, instead of the spring's first steps being spent inside one
    // long frame (a jump). The dim follows the same start.
    val entering = remember { motionEnabled && transition.currentState == EnterExitState.PreEnter }
    val rising = remember { Animatable(if (entering) 1f else 0f) }
    val undimming = remember { Animatable(if (entering) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!entering) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        launch { undimming.animateTo(0f, dimSpring) }
        rising.animateTo(0f, enterSpring)
    }
    val leaving = transition.targetState == EnterExitState.PostExit
    // Decided in composition, unlike the back flags: a pop must not gain these springs, or a back
    // gesture would seek a longer transition and distort its own pose.
    val resumingNow = leaving && motionEnabled && resuming()
    val fadeSpring = remember(scheme) { scheme.fastEffectsSpec<Float>() }
    val rise = transition.animateFloat(
        transitionSpec = { if (resumingNow) sheetSpring else snap() },
        label = "bookSheetResume",
    ) { state -> if (resumingNow && state == EnterExitState.PostExit) 1f else 0f }
    val square = transition.animateFloat(
        transitionSpec = { if (resumingNow) dimSpring else snap() },
        label = "bookSheetResumeCorners",
    ) { state -> if (resumingNow && state == EnterExitState.PostExit) 1f else 0f }
    val away = transition.animateFloat(
        transitionSpec = { if (resumingNow) fadeSpring else snap() },
        label = "bookSheetResumeInfo",
    ) { state -> if (resumingNow && state == EnterExitState.PostExit) 1f else 0f }
    val resume = remember(rise, square, away) { BookSheetResume(rise, square, away) }
    val slide = remember { BackSlide() }
    val hold = remember { BookSheetHold() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Which move is running is decided by navigation in the same pass, so it is read while
    // drawing: closing onto the library (also while a cancelled back gesture retraces), or
    // opening over it.
    fun leavingNow(): Boolean = transition.targetState == EnterExitState.PostExit
    fun closing(): Boolean = leavingNow() && motion.popping
    fun opening(): Boolean = !leavingNow() && motion.sheetEnter

    // 0..1 of the sheet's travel away from rest, over [extent] px; a drag's hand-off starts it
    // [BookSheetHold.offsetPx] down.
    fun travel(extent: Float): Float {
        val held = hold.offsetPx.coerceIn(0f, extent.coerceAtLeast(0f))
        val moved = when {
            closing() -> slide.position(hidden.value, dragging = !motion.released, tap = motion.tapPop)
            opening() -> rising.value
            else -> 0f
        }
        return if (extent <= 0f) moved else (held + (extent - held) * moved) / extent
    }

    // The library beside a pane follows its edge every frame (one push), and holds its grid's
    // columns still while the pane moves.
    val token = remember { Any() }
    val openness = remember { { (1f - travel(1f)).coerceIn(0f, 1f) } }
    val moving = remember { { transition.currentState != transition.targetState || rising.isRunning } }
    DisposableEffect(paneMode) {
        if (paneMode) pane.publish(token, openness, moving) else pane.release(token)
        onDispose { pane.release(token) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val besideLibrary = paneMode && bookPaneBesideLibrary(maxWidth)
        val paneWidthPx = with(LocalDensity.current) { bookPaneWidth(maxWidth).toPx() }
        if (!besideLibrary) {
            Box(
                Modifier
                    .fillMaxSize()
                    // The dimmed library under a phone sheet takes no touches.
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                    .drawBehind {
                        val gone = if (opening()) undimming.value else travel(size.height)
                        drawRect(Color.Black, alpha = SheetDim * (1f - gone).coerceIn(0f, 1f))
                    },
            )
        }
        CompositionLocalProvider(LocalBookSheetHold provides hold) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (besideLibrary) {
                            translationX = paneWidthPx * travel(1f) * if (rtl) -1f else 1f
                        } else {
                            translationY = size.height * travel(size.height)
                        }
                    },
            ) { content(besideLibrary, resume.takeUnless { besideLibrary }) }
        }
    }
}

/**
 * The spatial default spring the book sheet moves on, landing within a pixel's worth of its 0..1
 * travel so it settles smoothly. The reader's page arriving in a rising sheet uses the same one.
 */
@Composable
internal fun rememberSheetSpring(): FiniteAnimationSpec<Float> = rememberSettlingSpatialSpring()
