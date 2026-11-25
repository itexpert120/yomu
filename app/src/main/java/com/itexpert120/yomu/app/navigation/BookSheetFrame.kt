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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.rememberSettlingSpatialSpring
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.feature.bookdetails.BookSheetHold
import com.itexpert120.yomu.feature.bookdetails.BookSheetResume
import com.itexpert120.yomu.feature.bookdetails.LocalBookSheetHold
import com.itexpert120.yomu.feature.bookdetails.bookPaneBesideLibrary
import com.itexpert120.yomu.feature.bookdetails.bookPaneWidth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Behind an open book the library dims to 80% (spec: black at 20%, never blur). */
internal const val SheetDim = 0.2f

/**
 * The library as it looked when a book opened over it. Navigation removes the library page once
 * the book's page has arrived, so the book sheet keeps this picture of it to stand on. It lives
 * as long as the host composition (a configuration change drops it; the sheet then stands on
 * the plain desk).
 */
@Stable
internal class LibraryUnderlay {
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Takes the picture now, from the layer [recordLibraryPage] keeps current. */
    fun capture(layer: GraphicsLayer, scope: CoroutineScope) {
        if (layer.size.width <= 0 || layer.size.height <= 0) return
        // Undispatched: on API 28+ this renders straight into a hardware bitmap before the tap
        // returns, so the picture is ready for the sheet's first frame.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            image = runCatching { layer.toImageBitmap() }.getOrNull()
        }
    }

    fun clear() {
        image = null
    }
}

/**
 * Wraps the library (Home) page: records it into a layer each frame, so a book can take its
 * picture on open, and lets the picture go once the library is the resting page again.
 */
@Composable
internal fun AnimatedContentScope.LibraryPageRecorder(
    underlay: LibraryUnderlay,
    content: @Composable (GraphicsLayer) -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
    LaunchedEffect(settled) { if (settled) underlay.clear() }
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
    ) { content(layer) }
}

/**
 * Stack's open-book motion around the book details page. Opening, the book sheet rises from the
 * bottom (phones) or slides in from the end edge (a pane beside the library, medium and up) on
 * the spatial default spring, while the library behind dims to 80% on the effects spring and
 * never moves or scales. Closing is Stack's one back motion ([BackSlide]): the sheet slides back
 * down (the pane back to its edge) and the dim lifts with it — following the finger during a back
 * gesture, from rest on a back tap, a tap on the library beside a pane or the close button, and
 * from wherever a drag let go of the phone sheet ([BookSheetHold], handed over by the sheet).
 *
 * Under the sheet the frame draws [LibraryUnderlay]'s picture of the library, at rest and while the
 * sheet moves, over the live library page underneath; while closing, the picture fades out as the
 * sheet leaves, uncovering the live library. So the library on screen never swaps for a frame
 * (the live page's first frame after being recreated is often not settled yet). With no picture
 * (after a configuration change) it stands on the live library, or on the plain desk at rest.
 * Moving on to the editor, or coming back from it or from the reader, leaves the sheet where it is.
 *
 * Resume ([resuming]: the page is leaving for the reader, as a phone sheet) keeps the sheet moving
 * up: it rises to the window's top and squares its corners while the book info scrolls up off it
 * and fades, on the same spring as the reader's page arriving in it ([ReaderEntranceFrame]). The
 * library under it stays dimmed.
 */
@Composable
internal fun AnimatedContentScope.BookSheetFrame(
    motion: BackMotion,
    underlay: LibraryUnderlay,
    resuming: () -> Boolean,
    content: @Composable (libraryBeside: Boolean, resume: BookSheetResume?) -> Unit,
) {
    val motionEnabled = yomuAnimationsEnabled()
    val scheme = MaterialTheme.motionScheme
    val sheetSpring = rememberSheetSpring()
    // A pane beside the library slides in on the slow spatial spring (Stack: panes), a sheet on the default one.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val slowSpring = remember(scheme) { scheme.slowSpatialSpec<Float>() }
    val enterSpring = if (bookPaneBesideLibrary(windowWidth) && underlay.image != null) slowSpring else sheetSpring
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
    val desk = YomuTheme.stack.desk
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val image = underlay.image
        val besideLibrary = bookPaneBesideLibrary(maxWidth) && image != null
        val paneWidthPx = with(density) { bookPaneWidth(maxWidth).toPx() }

        // Which move is running is decided by navigation in the same pass, so it is read while
        // drawing: closing onto the library (also while a cancelled back gesture retraces), or
        // opening over it.
        fun closing(): Boolean = leaving && motion.popping
        fun opening(): Boolean = !leaving && motion.sheetEnter

        // 0..1 of the sheet's travel away from rest for this frame, over [extent] px; a drag's
        // hand-off starts it [hold] px down.
        fun travel(extent: Float): Float {
            val held = hold.offsetPx.coerceIn(0f, extent)
            val moved = when {
                closing() -> slide.position(hidden.value, dragging = !motion.released, tap = motion.tapPop)
                opening() -> hidden.value
                else -> 0f
            }
            return if (extent <= 0f) moved else (held + (extent - held) * moved) / extent
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val gone = travel(if (besideLibrary) paneWidthPx else size.height).coerceIn(0f, 1f)
                    val picture = image?.takeIf { it.width == size.width.toInt() && it.height == size.height.toInt() }
                    when {
                        picture != null -> drawImage(picture, alpha = if (closing()) 1f - gone else 1f)
                        // Moving over the live library without a picture: it shows through.
                        closing() || opening() -> Unit
                        else -> drawRect(desk)
                    }
                    // A pane beside the library leaves it undimmed (Stack's tablet board).
                    if (!besideLibrary) {
                        val dimLifted = if (opening()) undimmed.value else gone
                        drawRect(Color.Black, alpha = SheetDim * (1f - dimLifted).coerceIn(0f, 1f))
                    }
                },
        )
        CompositionLocalProvider(LocalBookSheetHold provides hold) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (besideLibrary) {
                            translationX = paneWidthPx * travel(paneWidthPx) * if (rtl) -1f else 1f
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
