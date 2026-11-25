package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuDialog
import com.itexpert120.yomu.core.designsystem.YomuExtendedFloatingActionButton
import com.itexpert120.yomu.core.designsystem.YomuLoadingIndicator
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit

/** Floating notices keep a snackbar-like measure instead of spanning a tablet. */
private val ImportNoticeMaxWidth = 560.dp

/** Height reserved for the Resume button / selection toolbar when a notice stacks above it. */
private val BottomChromeHeight = 64.dp

@Composable
internal fun ConfirmRemoveDialog(
    visible: Boolean,
    count: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    YomuDialog(
        title = "Remove ${if (count == 1) "this book" else "$count books"}?",
        text = "This deletes the imported file${if (count == 1) "" else "s"} and cover from " +
            "the device. It can't be undone.",
        icon = StackIcons.Delete,
        confirmLabel = "Remove",
        onConfirm = onConfirm,
        onDismiss = onCancel,
        destructive = true,
    )
}

/**
 * Import progress and results as a floating notice. It sits above the Resume button / selection
 * toolbar when either is showing ([aboveBottomChrome]) instead of overlapping it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImportNotice(
    importing: Boolean,
    notice: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = YomuTheme.space.md,
    aboveBottomChrome: Boolean = false,
) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val text = if (importing) "Importing…" else notice
    // Keep the last message on screen while the notice animates out.
    val lastText = remember { arrayOf("") }
    if (text != null) lastText[0] = text
    val stackOffset = if (aboveBottomChrome) BottomChromeHeight + YomuTheme.space.xs else 0.dp

    AnimatedVisibility(
        visible = text != null,
        enter = yomuChromeEnter(),
        exit = yomuChromeExit(),
        modifier = modifier,
    ) {
        YomuNotice(
            text = text ?: lastText[0],
            tone = YomuNoticeTone.Floating,
            modifier = Modifier
                .padding(horizontal = horizontalMargin)
                .padding(bottom = navBottom + YomuTheme.space.md + stackOffset)
                .widthIn(max = ImportNoticeMaxWidth)
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive },
            actionLabel = if (!importing && canRetry) "Retry" else null,
            onAction = onRetry,
            leading = if (importing) {
                { YomuLoadingIndicator(Modifier.size(24.dp)) }
            } else {
                null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FloatingResumeButton(
    book: LibraryBook,
    collapsed: Boolean,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    endMargin: Dp = YomuTheme.space.md,
) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    YomuExtendedFloatingActionButton(
        expanded = !collapsed,
        onClick = onResume,
        modifier = modifier
            .padding(end = endMargin, bottom = navBottom + YomuTheme.space.md),
        icon = StackIcons.PlayArrowFilled,
        label = "Resume",
        contentDescription = "Resume ${book.title}",
    )
}
