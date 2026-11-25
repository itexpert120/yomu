package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.LayoutDirection
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.feature.bookdetails.BookSheetResume
import com.itexpert120.yomu.feature.bookdetails.bookPaneBesideLibrary
import com.itexpert120.yomu.feature.bookdetails.bookPaneWidth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Behind an open book the library dims to 80% (spec: black at 20%, never blur). */
internal const val SheetDim = 0.2f

/** How much of its travel the sheet follows a back gesture before the back commits. */
private const val SheetDragShare = 0.35f

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
 * never moves or scales. Closing reverses it: a back tap plays it from rest; a back gesture pulls
 * the sheet along with the finger, then commits from wherever it was (or retraces on cancel).
 *
 * The library page underneath is live during these moves; at rest, once navigation has removed
 * it, the frame draws [LibraryUnderlay]'s picture of it (or the plain desk when there is none).
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
    val enterSpring = rememberSheetSpring()
    val dimSpring = remember(scheme) { scheme.defaultEffectsSpec<Float>() }
    // 1 = off screen / not dimmed, 0 = at rest. Entering springs; leaving runs linearly so a back
    // gesture can scrub it (the pose below eases it).
    val hidden = transition.animateFloat(
        transitionSpec = {
            if (initialState == EnterExitState.PreEnter) enterSpring else tween(YomuMotion.PredictiveBackMillis, easing = LinearEasing)
        },
        label = "bookSheet",
    ) { state -> if (motionEnabled && state != EnterExitState.Visible) 1f else 0f }
    val undimmed = transition.animateFloat(
        transitionSpec = {
            if (initialState == EnterExitState.PreEnter) dimSpring else tween(YomuMotion.PredictiveBackMillis, easing = LinearEasing)
        },
        label = "bookSheetDim",
    ) { state -> if (motionEnabled && state != EnterExitState.Visible) 1f else 0f }
    val leaving = transition.targetState == EnterExitState.PostExit
    // Decided in composition, unlike the back flags: a pop must not gain these springs, or a back
    // gesture would seek a longer transition and distort its own pose.
    val resumingNow = leaving && motionEnabled && resuming()
    val fadeSpring = remember(scheme) { scheme.fastEffectsSpec<Float>() }
    val rise = transition.animateFloat(
        transitionSpec = { if (resumingNow) enterSpring else snap() },
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
    val releaseAt = remember { floatArrayOf(0f) }
    val desk = YomuTheme.stack.desk
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val image = underlay.image
        val besideLibrary = bookPaneBesideLibrary(maxWidth) && image != null
        val paneWidthPx = with(density) { bookPaneWidth(maxWidth).toPx() }

        // Which move is running is decided by navigation in the same pass, so it is read while
        // drawing. [BackMotion.sheet]: closing onto the library (also while a cancelled back
        // gesture retraces); [BackMotion.sheetEnter]: opening over it.
        fun closing(): Boolean = motion.sheet
        fun opening(): Boolean = !motion.sheet && motion.sheetEnter && !leaving

        // 0..1 of the sheet's travel away from rest for this frame.
        fun travel(): Float {
            val t = hidden.value
            return when {
                closing() -> {
                    if (!motion.released) releaseAt[0] = t
                    val pose = PredictivePose.of(t, release = if (motion.tapPop) 0f else releaseAt[0])
                    val dragged = SheetDragShare * pose.drag
                    dragged + (1f - dragged) * pose.commit
                }
                opening() -> t
                else -> 0f
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val picture = image?.takeIf { it.width == size.width.toInt() && it.height == size.height.toInt() }
                    when {
                        // Closing onto the library: it is live underneath, so only the dim is drawn.
                        closing() && leaving -> Unit
                        picture != null -> drawImage(picture)
                        // Opening without a picture yet: the live library still shows through.
                        opening() -> Unit
                        else -> drawRect(desk)
                    }
                    if (!besideLibrary) {
                        val away = when {
                            closing() -> travel()
                            opening() -> undimmed.value
                            else -> 0f
                        }.coerceIn(0f, 1f)
                        drawRect(Color.Black, alpha = SheetDim * (1f - away))
                    }
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val away = travel()
                    if (besideLibrary) {
                        translationX = paneWidthPx * away * if (rtl) -1f else 1f
                    } else {
                        translationY = size.height * away
                    }
                },
        ) { content(besideLibrary, resume.takeUnless { besideLibrary }) }
    }
}

/**
 * The spatial default spring the book sheet moves on, landing within a pixel's worth of its 0..1
 * travel so it settles smoothly. The reader's page arriving in a rising sheet uses the same one.
 */
@Composable
internal fun rememberSheetSpring(): FiniteAnimationSpec<Float> {
    val scheme = MaterialTheme.motionScheme
    return remember(scheme) {
        (scheme.defaultSpatialSpec<Float>() as? SpringSpec<Float>)?.let { spring(it.dampingRatio, it.stiffness, visibilityThreshold = 0.0005f) }
            ?: scheme.defaultSpatialSpec()
    }
}
