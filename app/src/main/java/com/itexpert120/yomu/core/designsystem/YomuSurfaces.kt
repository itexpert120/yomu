package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun YomuAppSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YomuTheme.colors.appBackground),
        content = content,
    )
}

@Composable
fun YomuPanel(
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    contentPadding: Dp = YomuTheme.space.md,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YomuTheme.colors
    val radius = YomuTheme.radius
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.panel))
            .background(if (tonal) colors.panelStrong else colors.panel)
            .border(1.dp, colors.border, RoundedCornerShape(radius.panel))
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun YomuFloatingPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YomuTheme.colors
    val radius = YomuTheme.radius
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.lg))
            .background(colors.panel)
            .border(1.dp, colors.border, RoundedCornerShape(radius.lg))
            .padding(YomuTheme.space.sm),
        content = content,
    )
}

@Composable
fun ReaderPageSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = YomuTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(YomuTheme.radius.lg))
            .background(colors.readerPaper)
            .border(1.dp, colors.border, RoundedCornerShape(YomuTheme.radius.lg))
            .padding(YomuTheme.space.lg),
        content = content,
    )
}
