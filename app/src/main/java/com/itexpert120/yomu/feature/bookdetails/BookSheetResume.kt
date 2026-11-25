package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
