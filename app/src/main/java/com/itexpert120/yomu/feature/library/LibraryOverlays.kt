package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuDialog
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit

/** Floating notices keep a snackbar-like measure instead of spanning a tablet. */
private val ImportNoticeMaxWidth = 560.dp

/** Removing books says exactly which go, and that it can't be undone. */
@Composable
internal fun ConfirmRemoveDialog(
    visible: Boolean,
    titles: List<String>,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    val count = titles.size
    YomuDialog(
        title = "Remove ${if (count == 1) "this book" else "$count books"}?",
        text = "${titles.naturalList()} will be removed from this device, with " +
            "${if (count == 1) "its" else "their"} file, cover and reading progress. This can't be undone.",
        confirmLabel = "Remove",
        onConfirm = onConfirm,
        onDismiss = onCancel,
        destructive = true,
    )
}

/** "A", "A and B", "A, B and C", then "A, B and 3 more" past three titles. */
private fun List<String>.naturalList(): String = when {
    isEmpty() -> "The selected books"
    size == 1 -> first()
    size <= 3 -> dropLast(1).joinToString(", ") + " and " + last()
    else -> take(2).joinToString(", ") + " and ${size - 2} more"
}

/**
 * Import progress and results as a floating ink notice; while importing it carries a thin running
 * line along its bottom edge. It sits above the selection toolbar when that is showing
 * ([aboveBottomChrome]) instead of overlapping it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImportNotice(
    importing: Boolean,
    importingCount: Int,
    notice: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = YomuTheme.space.md,
    aboveBottomChrome: Boolean = false,
) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val text = when {
        importing && importingCount > 1 -> "Importing $importingCount books…"
        importing -> "Importing…"
        else -> notice
    }
    // Keep the last message on screen while the notice animates out.
    val lastText = remember { arrayOf("") }
    if (text != null) lastText[0] = text
    val stackOffset = if (aboveBottomChrome) SelectionToolbarClearance else 0.dp
    val failed = !importing && canRetry

    AnimatedVisibility(
        visible = text != null,
        enter = yomuChromeEnter(),
        exit = yomuChromeExit(),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .padding(horizontal = horizontalMargin)
                .padding(bottom = navBottom + YomuTheme.space.md + stackOffset)
                .widthIn(max = ImportNoticeMaxWidth)
                .fillMaxWidth()
                .semantics { liveRegion = if (failed) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        ) {
            YomuNotice(
                text = text ?: lastText[0],
                tone = YomuNoticeTone.Floating,
                icon = if (failed) StackIcons.Error else null,
                modifier = Modifier.fillMaxWidth(),
                actionLabel = if (failed) "Retry" else null,
                onAction = onRetry,
            )
            if (importing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(shape)
                        .padding(horizontal = 8.dp)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    trackColor = Color.Transparent,
                    gapSize = 0.dp,
                )
            }
        }
    }
}
