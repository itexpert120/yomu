package com.itexpert120.yomu.app.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.feature.bookdetails.BookSheetCorner
import com.itexpert120.yomu.feature.bookdetails.BookSheetResumeScroll
import com.itexpert120.yomu.feature.bookdetails.bookSheetTop
import com.itexpert120.yomu.feature.reader.LocalReaderOpeningRise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where the reader is opened from, when it opens with Stack's Resume motion. */
internal enum class ReaderEntrance {
    /** The book's sheet (a phone sheet) keeps rising and becomes the page. */
    BookSheet,

    /** The library pile's play button: a sheet rises from the bottom over the library into the page. */
    Library,
}

/**
 * Which Resume motion opening the reader plays, if any: from the library ([fromLibrary]) a fresh
 * sheet rises; from a book shown as a phone sheet ([fromBook], [bookIsPhoneSheet]) that sheet
 * carries on up. A book shown as a pane, and anything else, keeps the push.
 */
internal fun readerEntrance(
    toReader: Boolean,
    fromLibrary: Boolean,
    fromBook: Boolean,
    bookIsPhoneSheet: Boolean,
): ReaderEntrance? = when {
    !toReader -> null
    fromLibrary -> ReaderEntrance.Library
    fromBook && bookIsPhoneSheet -> ReaderEntrance.BookSheet
    else -> null
}

/**
 * Waits until the reader's first page is laid out ([pageReady]), but no longer than [graceMillis].
 * Returns whether the page is ready.
 */
internal suspend fun awaitFirstPage(pageReady: Flow<Boolean>, graceMillis: Long): Boolean = withTimeoutOrNull(graceMillis) { pageReady.first { it } } ?: false

/**
 * How long a rising sheet waits for the book's first page before showing the reader as it is
 * (its "Opening…" state), so a slow open never leaves a blank sheet on screen.
 */
private const val OpeningGraceMillis = 650L

/** How far the reader rises as it fades in, opened from the library's play button. */
private val LibraryEntranceRise = 48.dp

/**
 * The reader's side of Stack's Resume motion. The reader page arrives inside a sheet that rises
 * into it: from the book sheet ([ReaderEntrance.BookSheet]; [BookSheetFrame] moves that sheet up on
 * the same spring, so the page is clipped exactly to it) or, from the library, a fresh sheet
 * rising from the bottom edge while the library dims to 80% ([ReaderEntrance.Library]). The
 * sheet's top meets the window's top and its corners square off.
 *
 * The page itself only shows once the reader has laid out its first page (or failed, or
 * [OpeningGraceMillis] passed): it fades in on the effects spring over the risen sheet while the
 * chapter rises [BookSheetResumeScroll] into place (the reader's own first reveal, through
 * [LocalReaderOpeningRise]). The reader's opening is never held up; only what is drawn waits.
 *
 * Nothing moves when [entrance] is null, animations are off (navigation crossfades instead), or
 * the page is not opening (a restored reader, a pop): the frame then just draws the reader. Every
 * animated value is read while drawing.
 */
@Composable
internal fun AnimatedContentScope.ReaderEntranceFrame(
    entrance: ReaderEntrance?,
    pageReady: Flow<Boolean>,
    content: @Composable () -> Unit,
) {
    val motionEnabled = yomuAnimationsEnabled()
    // Settled at the page's first frame: only a page that is opening runs the motion.
    val active = remember { entrance != null && motionEnabled && transition.currentState == EnterExitState.PreEnter }
    val fromLibrary = entrance == ReaderEntrance.Library
    val scheme = MaterialTheme.motionScheme
    val sheetSpring = rememberSheetSpring()
    val effects = remember(scheme) { scheme.defaultEffectsSpec<Float>() }
    // 0 → 1 as the sheet reaches the window's top. Every other state holds 1, so a pop gains no
    // animation here (a back gesture seeks the pop's own length).
    val rise = transition.animateFloat(transitionSpec = { sheetSpring }, label = "readerSheetRise") { state ->
        if (active && state == EnterExitState.PreEnter) 0f else 1f
    }
    val square = transition.animateFloat(transitionSpec = { effects }, label = "readerSheetCorners") { state ->
        if (active && state == EnterExitState.PreEnter) 0f else 1f
    }
    val pageIn = remember { Animatable(if (active) 0f else 1f) }
    var rising by remember { mutableStateOf(active) }
    LaunchedEffect(Unit) {
        if (!active) return@LaunchedEffect
        launch {
            awaitFirstPage(pageReady, OpeningGraceMillis)
            pageIn.animateTo(1f, if (fromLibrary) scheme.slowEffectsSpec() else effects)
        }
        // The chapter's rise belongs to this first open only, not to a later reopen of the page.
        pageReady.first { it }
        rising = false
    }

    val density = LocalDensity.current
    val sheetTopPx = with(density) {
        bookSheetTop(WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()).toPx()
    }
    val cornerPx = with(density) { BookSheetCorner.toPx() }
    val libraryRisePx = with(density) { LibraryEntranceRise.toPx() }
    val sheetColor = MaterialTheme.colorScheme.surface

    // Navigation keeps the page underneath until this page's move has settled.
    fun settled(): Boolean = transition.currentState == EnterExitState.Visible

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // From the library: it dims under the rising sheet as it does under an open book.
                if (active && fromLibrary && !settled()) drawRect(Color.Black, alpha = SheetDim * rise.value.coerceIn(0f, 1f))
            }
            .graphicsLayer {
                val r = rise.value.coerceIn(0f, 1f)
                val s = square.value.coerceIn(0f, 1f)
                if (!active || (r >= 1f && s >= 1f)) {
                    clip = false
                    translationY = 0f
                    alpha = 1f
                    return@graphicsLayer
                }
                if (fromLibrary) {
                    // From the library: no sheet travelling the whole screen up from the bottom; the
                    // page itself rises a little as it fades in, once its first page is laid out.
                    clip = false
                    translationY = libraryRisePx * (1f - pageIn.value.coerceIn(0f, 1f))
                    return@graphicsLayer
                }
                shape = RisingSheetShape(top = sheetTopPx * (1f - r), corner = cornerPx * (1f - s))
                clip = true
            }
            .drawBehind {
                // The sheet itself, under the page until the page has faded in. From the book it is
                // drawn only once that sheet has gone (it is the same blank sheet by then), so the
                // book info can be seen leaving (it has faded long before the sheet tops out).
                if (active && !fromLibrary && pageIn.value < 1f && (settled() || rise.value >= 0.999f)) drawRect(sheetColor)
            },
    ) {
        // The chapter's own first rise belongs to the book sheet's scroll; from the library the whole
        // page makes the short rise.
        val chapterRise = if (fromLibrary) 0.dp else BookSheetResumeScroll
        CompositionLocalProvider(LocalReaderOpeningRise provides if (active && rising) chapterRise else 0.dp) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = pageIn.value }) { content() }
        }
    }
}

/** A sheet whose top edge stands [top] down the page, with its top corners rounded by [corner]. */
private class RisingSheetShape(private val top: Float, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Rounded(
        RoundRect(
            left = 0f,
            top = top.coerceAtMost(size.height),
            right = size.width,
            bottom = size.height,
            topLeftCornerRadius = CornerRadius(corner),
            topRightCornerRadius = CornerRadius(corner),
            bottomRightCornerRadius = CornerRadius.Zero,
            bottomLeftCornerRadius = CornerRadius.Zero,
        ),
    )
}
