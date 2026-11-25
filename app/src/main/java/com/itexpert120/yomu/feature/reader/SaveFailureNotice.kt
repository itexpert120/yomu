package com.itexpert120.yomu.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.node.Ref
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit

@Composable
internal fun SaveFailureNotice(message: String?, onRetry: () -> Unit) {
    ReaderFloatingNotice(value = message) { text ->
        YomuNotice(
            text = text,
            icon = StackIcons.Error,
            tone = YomuNoticeTone.Floating,
            actionLabel = "Retry",
            onAction = onRetry,
        )
    }
}

/**
 * A transient notice floating at the top of the screen, clear of the status bar and any display
 * cutout (measured from the host window, since a popup window gets no insets of its own). It
 * materializes with the shared popup motion and stays composed until its exit has played; the last
 * non-null [value] keeps rendering during that exit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T : Any> ReaderFloatingNotice(
    value: T?,
    content: @Composable (T) -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false) }
    if (visibleState.targetState != (value != null)) visibleState.targetState = value != null
    val last = remember { Ref<T>() }
    if (value != null) last.value = value
    if (!visibleState.currentState && !visibleState.targetState) return

    val topInset = WindowInsets.displayCutout
        .union(WindowInsets.statusBarsIgnoringVisibility)
        .asPaddingValues()
        .calculateTopPadding()
    Popup(alignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = yomuPopupEnter(TopCenterOrigin),
            exit = yomuPopupExit(TopCenterOrigin),
        ) {
            // The padding sits inside the popup window so the notice's shadow isn't clipped by it.
            Box(
                modifier = Modifier
                    .padding(top = topInset)
                    .padding(horizontal = YomuTheme.space.md, vertical = YomuTheme.space.xs)
                    .widthIn(max = ReaderNoticeMaxWidth),
            ) {
                last.value?.let { content(it) }
            }
        }
    }
}

private val TopCenterOrigin = TransformOrigin(0.5f, 0f)
private val ReaderNoticeMaxWidth = 480.dp
