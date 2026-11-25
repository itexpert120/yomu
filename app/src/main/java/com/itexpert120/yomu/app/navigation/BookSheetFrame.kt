package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Behind an open book the library dims to 80% (spec: black at 20%, never blur). */
internal const val SheetDim = 0.2f

/**
 * How far the book pane beside the library (medium widths and up) is open: 0 closed, 1 fully in.
 * The book's frame publishes it while it is composed; the library reads it while laying out and
 * narrows its column by that share of the pane's width, so the two move as one (list-detail).
 */
@Stable
internal class BookPaneSpace {
    var openness: (() -> Float)? by mutableStateOf(null)
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
    // 1 = off screen, 0 = at rest. Opening springs; any other move is the linear back clock.
    val hidden = transition.animateFloat(
        transitionSpec = {
            if (initialState == EnterExitState.PreEnter) enterSpring else tween(YomuMotion.BackSlideMillis, easing = LinearEasing)
        },
        label = "bookSheet",
    ) { state -> if (motionEnabled && state != EnterExitState.Visible) 1f else 0f }
    val undimmed = transition.animateFloat(
        transitionSpec = {
            if (initialState == EnterExitState.PreEnter) dimSpring else tween(YomuMotion.BackSlideMillis, easing = LinearEasing)
        },
        label = "bookSheetDim",
    ) { state -> if (motionEnabled && state != EnterExitState.Visible) 1f else 0f }
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
    // (Live reads, not the composition's `leaving`: the library calls these through [openness].)
    fun leavingNow(): Boolean = transition.targetState == EnterExitState.PostExit
    fun closing(): Boolean = leavingNow() && motion.popping
    fun opening(): Boolean = !leavingNow() && motion.sheetEnter

    // 0..1 of the sheet's travel away from rest, over [extent] px; a drag's hand-off starts it
    // [BookSheetHold.offsetPx] down.
    fun travel(extent: Float): Float {
        val held = hold.offsetPx.coerceIn(0f, extent.coerceAtLeast(0f))
        val moved = when {
            closing() -> slide.position(hidden.value, dragging = !motion.released, tap = motion.tapPop)
            opening() -> hidden.value
            else -> 0f
        }
        return if (extent <= 0f) moved else (held + (extent - held) * moved) / extent
    }

    // The library beside a pane narrows with it. Only this page's own value is ever cleared, so a
    // book swapped in place (two of these briefly composed) never leaves the library full width.
    val openness = remember { { (1f - travel(1f)).coerceIn(0f, 1f) } }
    DisposableEffect(paneMode) {
        if (paneMode) pane.openness = openness
        onDispose { if (pane.openness === openness) pane.openness = null }
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
                        val gone = if (opening()) undimmed.value else travel(size.height)
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
