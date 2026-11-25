package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** The desk: the grey background every screen sits on, with ink as its content colour. */
@Composable
fun YomuAppSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), content = content)
    }
}

/**
 * A card on the desk: sheet-coloured, radius 24, no elevation. [tonal] = false is the same card
 * (Stack has no tonal step); [outlined] adds a rule-coloured hairline.
 */
@Composable
fun YomuPanel(
    modifier: Modifier = Modifier,
    tonal: Boolean = true,
    outlined: Boolean = false,
    contentPadding: Dp = YomuTheme.space.md,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = YomuShapeTokens.hero,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Box(Modifier.padding(contentPadding), content = content)
    }
}

/** A floating sheet-coloured panel (radius 24) with the soft static shadow floating things carry. */
@Composable
fun YomuFloatingPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = YomuShapeTokens.hero
    Surface(
        modifier = modifier.dropShadow(shape, FloatingShadow),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.padding(YomuTheme.space.sm), content = content)
    }
}

/** A reading-page sample surface (dev gallery, previews): the sheet colour, radius 24. */
@Composable
fun ReaderPageSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = YomuShapeTokens.hero,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.padding(YomuTheme.space.lg), content = content)
    }
}

private val FloatingShadow = Shadow(radius = 24.dp, spread = (-10).dp, offset = DpOffset(0.dp, 8.dp), color = Color(0xFF16191C).copy(alpha = 0.35f))
