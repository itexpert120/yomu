package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Stack's one scroll-edge rule: where content scrolls under something that stays put — a pinned
 * header, a pinned footer, a sheet's pinned title row — a 1dp hairline in the `rule` colour appears
 * on that edge, cross-fading in on the effects spring while content is actually hidden there, and
 * out again at rest. Nothing else: no fades, gradients or shadows, and an open edge (a screen's or a
 * sheet's bottom edge, where content simply runs under the gesture bar) gets no rule at all.
 *
 * Use [yomuPinnedHeaderRule] on a pinned header (the rule sits on its bottom edge),
 * [yomuPinnedFooterRule] on a pinned footer (top edge), or [yomuScrollEdgeRule] on a scroll
 * container whose own top/bottom edge is the seam. `YomuScreenScaffold`, `YomuScreenHeader`,
 * `YomuBottomSheet` and `YomuSheetHeader` already apply it. Every value is read while drawing.
 */

/** A pinned header's rule, on its bottom edge, while [scrolledUnder] says content is beneath it. */
fun Modifier.yomuPinnedHeaderRule(scrolledUnder: () -> Boolean): Modifier = edgeRule(scrolledUnder, atTop = false)

/** A pinned footer's rule, on its top edge, while [scrolledUnder] says content continues beneath it. */
fun Modifier.yomuPinnedFooterRule(scrolledUnder: () -> Boolean): Modifier = edgeRule(scrolledUnder, atTop = true)

/**
 * Rules on a scroll container's own edges: [top] while content is scrolled up past its top edge
 * (`canScrollBackward`), [bottom] while more content waits below its bottom edge
 * (`canScrollForward`) — pass true only where that edge meets something pinned.
 */
fun Modifier.yomuScrollEdgeRule(top: Boolean = false, bottom: Boolean = false): Modifier = this
    .edgeRule({ top }, atTop = true)
    .edgeRule({ bottom }, atTop = false)

@Deprecated("Stack has one scroll-edge treatment, the hairline rule.", ReplaceWith("yomuScrollEdgeRule(top, bottom)"))
fun Modifier.yomuScrollEdgeShadow(
    top: Boolean = false,
    bottom: Boolean = false,
    @Suppress("UNUSED_PARAMETER") height: Dp = 28.dp,
): Modifier = yomuScrollEdgeRule(top, bottom)

/** Pins a rule to [state]'s top edge (a header-less scroll area under a pinned title, say). */
fun Modifier.yomuScrollEdgeRule(state: ScrollState, top: Boolean = true, bottom: Boolean = false): Modifier = this
    .then(if (top) edgeRule({ state.canScrollBackward }, atTop = true) else Modifier)
    .then(if (bottom) edgeRule({ state.canScrollForward }, atTop = false) else Modifier)

/** The rule itself; [bleed] draws it that far past the element on both sides (clipped by its container). */
internal fun Modifier.edgeRule(shown: () -> Boolean, atTop: Boolean, bleed: Dp = 0.dp): Modifier = composed {
    val latest by rememberUpdatedState(shown)
    // Recomposes only when the edge flips, never per scrolled pixel.
    val on by remember { derivedStateOf { latest() } }
    val strength = animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = if (yomuAnimationsEnabled()) YomuMotion.scheme.defaultEffectsSpec() else snap(),
        label = "yomuScrollEdgeRule",
    )
    val color = YomuTheme.stack.rule
    drawWithContent {
        drawContent()
        val a = strength.value
        if (a <= 0f) return@drawWithContent
        val h = YomuScrollRuleWidth.toPx()
        val x = bleed.toPx()
        drawRect(
            color = color.copy(alpha = color.alpha * a),
            topLeft = Offset(-x, if (atTop) 0f else size.height - h),
            size = Size(size.width + x * 2, h),
        )
    }
}

/** The rule's thickness. */
val YomuScrollRuleWidth: Dp = 1.dp
