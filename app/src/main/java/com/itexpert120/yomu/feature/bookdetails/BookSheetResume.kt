package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme

/** How far below the status bar the phone book sheet starts: the library shows above it. */
val BookSheetTopGap = 50.dp

/** The book sheet's top corners. */
val BookSheetCorner = 32.dp

/**
 * How far the book info scrolls up off the sheet as Resume turns the sheet into the page, and how
 * far the chapter rises into place: one distance, so the two read as a single scroll (Stack spec).
 */
val BookSheetResumeScroll = 340.dp

/** Where the phone book sheet's top edge rests, [statusBarTop] being the status bar's height. */
fun bookSheetTop(statusBarTop: Dp): Dp = statusBarTop + BookSheetTopGap

/**
 * Where the phone book sheet stands in the Resume motion into the reader. Navigation drives the
 * values; the sheet reads them only while drawing, so the motion never recomposes it.
 *
 * [rise]: 0 at rest, 1 with the sheet's top at the window's top (it may overshoot a little; the
 * sheet clamps it). [square]: 0..1 as its 32dp top corners square off. [away]: 0..1 as the book
 * info fades out; it scrolls [BookSheetResumeScroll] up with [rise].
 */
@Stable
class BookSheetResume(
    private val riseState: State<Float>,
    private val squareState: State<Float>,
    private val awayState: State<Float>,
) {
    val rise: Float get() = riseState.value
    val square: Float get() = squareState.value
    val away: Float get() = awayState.value
}

/**
 * Where a drag let go of the phone book sheet when it closed it: navigation's close motion
 * continues from [offsetPx] (px below rest) instead of the sheet animating away on its own, so a
 * drag-dismissal is one continuous move and the sheet never comes back. Navigation provides it
 * through [LocalBookSheetHold]; the sheet writes it once, as it hands itself over.
 */
@Stable
class BookSheetHold {
    var offsetPx by mutableFloatStateOf(0f)
}

val LocalBookSheetHold = staticCompositionLocalOf<BookSheetHold?> { null }

/**
 * The book pane's leading edge (the tablet board's hairline): a 1dp rule down its start side and
 * round its [BookSheetCorner] top-start corner, where it meets the library beside it.
 */
@Composable
fun Modifier.bookPaneEdge(): Modifier {
    val rule = YomuTheme.stack.rule
    return drawBehind {
        val w = 1.dp.toPx()
        val r = BookSheetCorner.toPx()
        val rtl = layoutDirection == LayoutDirection.Rtl
        val path = Path().apply {
            if (!rtl) {
                moveTo(w / 2f - w, size.height)
                lineTo(w / 2f - w, r)
                arcTo(Rect(w / 2f - w, w / 2f - w, w / 2f - w + r * 2, w / 2f - w + r * 2), 180f, 90f, false)
                lineTo(r * 2, w / 2f - w)
            } else {
                moveTo(size.width - w / 2f + w, size.height)
                lineTo(size.width - w / 2f + w, r)
                arcTo(Rect(size.width - w / 2f + w - r * 2, w / 2f - w, size.width - w / 2f + w, w / 2f - w + r * 2), 0f, -90f, false)
                lineTo(size.width - r * 2, w / 2f - w)
            }
        }
        drawPath(path, rule, style = Stroke(w))
    }
}
