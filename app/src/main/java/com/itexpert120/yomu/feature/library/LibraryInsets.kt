package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

@Composable
internal fun libraryContentPadding(
    horizontal: Dp,
    top: Dp,
    bottom: Dp,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    return PaddingValues(
        start = safeDrawing.calculateStartPadding(layoutDirection) + horizontal,
        top = safeDrawing.calculateTopPadding() + top,
        end = safeDrawing.calculateEndPadding(layoutDirection) + horizontal,
        bottom = safeDrawing.calculateBottomPadding() + bottom,
    )
}
