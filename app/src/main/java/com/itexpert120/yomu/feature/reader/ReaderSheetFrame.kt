package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.itexpert120.yomu.core.designsystem.LocalYomuSheetPresentation
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuIconButton
import com.itexpert120.yomu.core.designsystem.YomuSheetPresentation
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * The frame every reader sheet shares (the sheet header decision): one line, the title or the tabs
 * on the left and a quiet close × on the right; no subtitle. Sheets with tabs use the tabs as their
 * header. Tall sheets (Browse, Search) open at a fixed height that leaves the keyboard room.
 */

/**
 * A tabbed sheet's header: the tab words, the active one in ink 600, over a 2dp ink line that
 * follows the [pager] 1:1 while it is swiped (read while drawing) and glides on a tap; the close ×
 * at the end. Sits on a full-bleed rule.
 */
@Composable
internal fun <T> ReaderPagerTabs(
    tabs: List<T>,
    label: (T) -> String,
    pager: PagerState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalInset: Dp = YomuTheme.space.lg,
) {
    val stack = YomuTheme.stack
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val spans = remember(tabs.size) { mutableStateListOf<Pair<Float, Float>?>().apply { repeat(tabs.size) { add(null) } } }
    val animate = yomuAnimationsEnabled()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // A tick when a swipe or tap lands on another tab.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val rule = 1.dp.toPx()
                drawRect(stack.rule, topLeft = Offset(0f, size.height - rule), size = Size(size.width, rule))
            }
            .padding(start = horizontalInset, end = horizontalInset - 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .drawBehind {
                    // Where the pager is, between two tabs while it moves: read here, so a swipe
                    // only redraws the line.
                    val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, tabs.lastIndex.toFloat())
                    val from = position.toInt()
                    val to = (from + 1).coerceAtMost(tabs.lastIndex)
                    val a = spans.getOrNull(from) ?: return@drawBehind
                    val b = spans.getOrNull(to) ?: a
                    val t = position - from
                    val line = 2.dp.toPx()
                    drawRoundRect(
                        color = stack.ink,
                        topLeft = Offset(lerp(a.first, b.first, t), size.height - line),
                        size = Size(lerp(a.second, b.second, t), line),
                        cornerRadius = CornerRadius(line / 2),
                    )
                }
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = pager.targetPage == index
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .onPlaced { coordinates ->
                            val span = coordinates.positionInParent().x to coordinates.size.width.toFloat()
                            if (index < spans.size && spans[index] != span) spans[index] = span
                        }
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            indication = null,
                            interactionSource = null,
                            onClick = {
                                scope.launch {
                                    if (animate) pager.animateScrollToPage(index, animationSpec = spec) else pager.scrollToPage(index)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(tab),
                        color = if (selected) stack.ink else stack.faint,
                        style = StackType.Body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                        maxLines = 1,
                    )
                }
            }
        }
        YomuIconButton(icon = StackIcons.Close, contentDescription = "Close", onClick = onClose, tint = stack.muted)
    }
}

/**
 * The height of a reader sheet's body on a phone. [fill] opens it at that height whatever its
 * content (Browse, Search: lists that must not jump as they fill); otherwise it is a cap and the
 * sheet hugs shorter content. The height is [fraction] of the window, always leaving the status
 * bar and a strip of page above the sheet, and above the keyboard when one is up — the insets are
 * read while laying out, so the sheet follows the keyboard without recomposing. Side sheets (the
 * full-height tablet panel) simply fill.
 */
@Composable
internal fun Modifier.readerSheetHeight(fraction: Float, fill: Boolean): Modifier {
    if (LocalYomuSheetPresentation.current == YomuSheetPresentation.Side) {
        return if (fill) this.layout { m, c -> fixed(m, c, c.maxHeight) } else this
    }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val statusBars = WindowInsets.statusBars
    val navigationBars = WindowInsets.navigationBars
    val ime = WindowInsets.ime
    return this.layout { measurable, constraints ->
        val keyboard = ime.getBottom(this)
        // The sheet's own chrome under its body: the gesture bar (hidden by a keyboard) and padding.
        val below = (if (keyboard > 0) 0 else navigationBars.getBottom(this)) + SheetChromeBelow.roundToPx()
        val room = windowHeight - keyboard - statusBars.getTop(this) - PageStripAbove.roundToPx() - below
        // The body runs on down behind the gesture bar (its rows add LocalYomuSheetBottomInset as
        // content padding), so the sheet's top stays where the room calls for.
        val behind = (if (keyboard > 0) 0 else navigationBars.getBottom(this)) + SheetBottomBreath.roundToPx()
        val target = minOf((windowHeight * fraction).roundToInt(), room).coerceAtLeast(MinSheetBody.roundToPx()) + behind
        if (fill) {
            fixed(measurable, constraints, target)
        } else {
            val placeable = measurable.measure(constraints.copy(maxHeight = target.coerceIn(constraints.minHeight, constraints.maxHeight)))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }
}

private fun androidx.compose.ui.layout.MeasureScope.fixed(
    measurable: androidx.compose.ui.layout.Measurable,
    constraints: androidx.compose.ui.unit.Constraints,
    height: Int,
): androidx.compose.ui.layout.MeasureResult {
    val h = if (constraints.hasBoundedHeight) height.coerceIn(constraints.minHeight, constraints.maxHeight) else height
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    return layout(placeable.width, h) { placeable.place(0, 0) }
}

/** Page left visible above a tall sheet, under the status bar. */
private val PageStripAbove = 40.dp

/** The phone sheet's padding below its body (YomuBottomSheet's top + bottom padding). */
private val SheetChromeBelow = 32.dp

private val MinSheetBody = 200.dp

/** The breathing room YomuBottomSheet keeps under content, above the gesture bar. */
private val SheetBottomBreath = 16.dp
