package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.node.Ref
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.itexpert120.yomu.core.designsystem.StackColors
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.designsystem.yomuPressable

@Composable
internal fun SaveFailureNotice(message: String?, onRetry: () -> Unit) {
    ReaderFloatingNotice(value = message) { text ->
        ReaderNoticeCard(text = text, icon = StackIcons.Error, iconIsAlert = true) {
            ReaderNoticeAction("Retry", onClick = onRetry)
        }
    }
}

/**
 * Stack's notice: an ink card (radius 16, soft static shadow) with on-ink text, an optional leading
 * icon and text actions on the right.
 */
@Composable
internal fun ReaderNoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    // The error glyph takes the danger colour that reads on the ink card (the other theme's danger).
    iconIsAlert: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val stack = YomuTheme.stack
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dropShadow(shape, Shadow(radius = 24.dp, spread = (-10).dp, offset = DpOffset(0.dp, 8.dp), color = Color.Black.copy(alpha = 0.5f)))
            .clip(shape)
            .background(stack.ink)
            .heightIn(min = 56.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            val tint = when {
                !iconIsAlert -> stack.onInk
                stack.isDark -> StackColors.Light.danger
                else -> StackColors.Dark.danger
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(
            text = text,
            color = stack.onInk,
            style = StackType.BodySmall.copy(lineHeight = 20.sp),
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** A notice's text action: on-ink 700 for the main one, quieter for a [secondary] one. */
@Composable
internal fun ReaderNoticeAction(label: String, onClick: () -> Unit, secondary: Boolean = false) {
    val stack = YomuTheme.stack
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .yomuPressable(onClick = onClick, shape = RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (secondary) stack.onInk.copy(alpha = 0.78f) else stack.onInk,
            style = StackType.BodySmall.copy(
                fontSize = 14.sp,
                fontWeight = if (secondary) FontWeight.Medium else FontWeight.Bold,
            ),
        )
    }
}

/** Where a [ReaderFloatingNotice] floats: problems at the top, offers by the thumb. */
internal enum class ReaderNoticePlacement { Top, Bottom }

/**
 * A transient notice floating at the top or bottom of the screen, clear of the system bars and any
 * display cutout (measured from the host window, since a popup window gets no insets of its own).
 * It materializes with the shared popup motion and stays composed until its exit has played; the
 * last non-null [value] keeps rendering during that exit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T : Any> ReaderFloatingNotice(
    value: T?,
    placement: ReaderNoticePlacement = ReaderNoticePlacement.Top,
    content: @Composable (T) -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false) }
    if (visibleState.targetState != (value != null)) visibleState.targetState = value != null
    val last = remember { Ref<T>() }
    if (value != null) last.value = value
    if (!visibleState.currentState && !visibleState.targetState) return

    val top = placement == ReaderNoticePlacement.Top
    val inset = if (top) {
        WindowInsets.displayCutout.union(WindowInsets.statusBarsIgnoringVisibility).asPaddingValues().calculateTopPadding()
    } else {
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    }
    val origin = if (top) TopCenterOrigin else BottomCenterOrigin
    Popup(alignment = if (top) Alignment.TopCenter else Alignment.BottomCenter) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = yomuPopupEnter(origin),
            exit = yomuPopupExit(origin),
        ) {
            // The padding sits inside the popup window so the notice's shadow isn't clipped by it.
            Box(
                modifier = Modifier
                    .padding(top = if (top) inset else 0.dp, bottom = if (top) 0.dp else inset + YomuTheme.space.lg)
                    .padding(horizontal = YomuTheme.space.sm, vertical = YomuTheme.space.xs)
                    .widthIn(max = ReaderNoticeMaxWidth),
            ) {
                last.value?.let { content(it) }
            }
        }
    }
}

private val TopCenterOrigin = TransformOrigin(0.5f, 0f)
private val BottomCenterOrigin = TransformOrigin(0.5f, 1f)
private val ReaderNoticeMaxWidth = 480.dp
