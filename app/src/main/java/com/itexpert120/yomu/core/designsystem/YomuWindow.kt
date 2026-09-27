package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width-driven layout buckets matching the Material 3 breakpoints (formerly window size classes),
 * driven by a measured width via `BoxWithConstraints`:
 *  - [Compact]  (< 600dp)      phone portrait → one pane, navigation bar, 16dp margins.
 *  - [Medium]   (600–839dp)    tablet/foldable portrait → one pane (two only for light content),
 *                              navigation rail, 24dp margins.
 *  - [Expanded] (840–1199dp)   tablet landscape → two panes, fixed pane 360dp.
 *  - [Large]    (1200dp+)      large tablet/desktop → two panes, fixed pane 412dp.
 */
enum class YomuWidthClass {
    Compact,
    Medium,
    Expanded,
    Large,
    ;

    val isExpanded: Boolean get() = this == Expanded || this == Large

    /** Wide enough for a navigation rail and roomier layouts (M3 medium and up). */
    val isWide: Boolean get() = this != Compact

    /** Leading/trailing window margin for this breakpoint. */
    val margin: Dp get() = if (this == Compact) 16.dp else YomuPaneSpacer

    companion object {
        const val MEDIUM_MIN_DP = 600
        const val EXPANDED_MIN_DP = 840
        const val LARGE_MIN_DP = 1200

        fun fromWidth(width: Dp): YomuWidthClass = when {
            width >= LARGE_MIN_DP.dp -> Large
            width >= EXPANDED_MIN_DP.dp -> Expanded
            width >= MEDIUM_MIN_DP.dp -> Medium
            else -> Compact
        }
    }
}

/**
 * Comfortable maximum width for wide, visual surfaces (grids) so they don't stretch edge-to-edge on
 * desktops. Content is centred within this bound.
 */
val YomuContentMaxWidth: Dp = 1040.dp

/**
 * Maximum width for reading-oriented single panes (forms, prose, settings). Keeps body text near
 * Material's 40–60 characters per line instead of spanning a landscape tablet.
 */
val YomuReadableMaxWidth: Dp = 720.dp

/** Space between two panes (Material 3: 24dp from medium up). */
val YomuPaneSpacer: Dp = 24.dp

/** Minimum available width at which a side-by-side layout of dense content stays comfortable. */
val YomuTwoPaneMinWidth: Dp = 720.dp

fun Dp.supportsYomuTwoPane(): Boolean = this >= YomuTwoPaneMinWidth

/**
 * Leading-pane width per Material 3: panes split 50/50 at medium; from expanded the leading pane is
 * a fixed 360dp (412dp at large) and the trailing pane flexes.
 */
fun yomuLeadingPaneWidth(availableWidth: Dp): Dp {
    val inner = availableWidth - YomuPaneSpacer * 3
    return when (YomuWidthClass.fromWidth(availableWidth)) {
        YomuWidthClass.Compact, YomuWidthClass.Medium -> inner / 2
        YomuWidthClass.Expanded -> 360.dp.coerceAtMost(inner / 2)
        YomuWidthClass.Large -> 412.dp
    }
}

/**
 * The shared adaptive two-pane primitive (Material 3 fixed-and-flexible / split-pane layout). Panes
 * sit inside the 24dp window margins with a 24dp spacer between them; each slot owns its own scroll
 * state so long metadata and long lists stay independent.
 */
@Composable
fun YomuTwoPane(
    modifier: Modifier = Modifier,
    startModifier: Modifier = Modifier,
    endModifier: Modifier = Modifier,
    startContent: @Composable BoxScope.() -> Unit,
    endContent: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val startWidth = yomuLeadingPaneWidth(maxWidth)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = YomuPaneSpacer),
            horizontalArrangement = Arrangement.spacedBy(YomuPaneSpacer),
        ) {
            Box(
                modifier = startModifier
                    .width(startWidth)
                    .fillMaxHeight(),
                content = startContent,
            )
            Box(
                modifier = endModifier
                    .weight(1f)
                    .fillMaxHeight(),
                content = endContent,
            )
        }
    }
}
