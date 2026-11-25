package com.itexpert120.yomu.feature.contents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.designsystem.yomuPressable

/**
 * "Current chapter ↑/↓": a small ink pill that rises in while the chapter being read is out of view
 * (scrolled away, or inside a folded volume); its arrow points to where the chapter is. Shared by the
 * reader's Browse sheet and Book Details' contents.
 */
@Composable
fun CurrentChapterPill(visible: Boolean, below: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = yomuPopupEnter(TransformOrigin(0.5f, 1f)),
        exit = yomuPopupExit(TransformOrigin(0.5f, 1f)),
        modifier = modifier,
    ) {
        val stack = YomuTheme.stack
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .yomuPressable(onClick = onClick, shape = CircleShape)
                .background(stack.ink)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (below) StackIcons.KeyboardArrowDown else StackIcons.KeyboardArrowUp,
                contentDescription = null,
                tint = stack.onInk,
                modifier = Modifier.size(20.dp),
            )
            Text(text = "Current chapter", color = stack.onInk, style = StackType.Heading, maxLines = 1)
        }
    }
}

/** Whether item [index] is on screen (at least half of it). */
fun LazyListState.showsItem(index: Int): Boolean {
    if (index < 0) return false
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return true
    val viewportEnd = layoutInfo.viewportEndOffset
    return visible.any { it.index == index && it.offset + it.size / 2 in 0..viewportEnd }
}

/**
 * Scrolls item [index] into view with [lead] items above it (rows that a pinned header covers, plus
 * one of context), gliding on [spec]. A far item is first jumped near, so the glide is a short one.
 */
suspend fun LazyListState.revealItem(index: Int, lead: Int, spec: AnimationSpec<Float>) {
    val target = (index - lead).coerceAtLeast(0)
    fun offsetOf() = layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }?.offset
    if (offsetOf() == null) {
        val approach = if (target > firstVisibleItemIndex) (target - RevealApproachItems).coerceAtLeast(0) else target + RevealApproachItems
        scrollToItem(approach)
    }
    val offset = offsetOf()
    if (offset != null) animateScrollBy(offset.toFloat(), spec) else scrollToItem(target)
}

// How many rows short of the target a long jump lands before gliding the rest.
private const val RevealApproachItems = 6
