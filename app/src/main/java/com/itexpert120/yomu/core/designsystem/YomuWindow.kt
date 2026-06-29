package com.itexpert120.yomu.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width-driven layout buckets for responsive, tablet-aware UI. Mirrors the spirit of Material's
 * WindowSizeClass but is dependency-free (driven by a measured width via `BoxWithConstraints`) so it
 * fits Yomu's CompositionLocal-based design system.
 *
 * Breakpoints follow the common Compact / Medium / Expanded split:
 *  - [Compact]  : phones in portrait (< 600dp)              → single-column layouts.
 *  - [Medium]   : large phones landscape / small tablets    → can start widening / two-pane.
 *  - [Expanded] : tablets, foldables open, desktop (>= 840dp) → full two-pane, wide grids.
 */
enum class YomuWidthClass {
    Compact,
    Medium,
    Expanded,
    ;

    val isExpanded: Boolean get() = this == Expanded

    /** Wide enough to justify a side-by-side (two-pane) layout. */
    val isWide: Boolean get() = this == Medium || this == Expanded

    companion object {
        const val MEDIUM_MIN_DP = 600
        const val EXPANDED_MIN_DP = 840

        fun fromWidth(width: Dp): YomuWidthClass = when {
            width >= EXPANDED_MIN_DP.dp -> Expanded
            width >= MEDIUM_MIN_DP.dp -> Medium
            else -> Compact
        }
    }
}

/**
 * Comfortable maximum content width so reading-oriented surfaces (library grid, details) don't
 * stretch edge-to-edge on very wide tablets/desktops. Content is centered within this bound.
 */
val YomuContentMaxWidth: Dp = 1040.dp
