package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit

@Composable
internal fun ConfirmRemoveDialog(
    visible: Boolean,
    count: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("Remove ${if (count == 1) "this book" else "$count books"}?")
        },
        text = {
            Text(
                "This deletes the imported file${if (count == 1) "" else "s"} and cover from " +
                    "the device. It can't be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImportNotice(
    importing: Boolean,
    notice: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val text = if (importing) "Importing…" else notice
    // Hold the last shown text so the pill doesn't go blank during its exit animation.
    var lastText by remember { mutableStateOf("") }
    if (text != null) lastText = text

    AnimatedVisibility(
        visible = text != null,
        enter = yomuChromeEnter(),
        exit = yomuChromeExit(),
        modifier = modifier.padding(bottom = navBottom + 20.dp),
    ) {
        Snackbar(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            action = if (!importing && canRetry) {
                {
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            } else {
                null
            },
        ) {
            Text(lastText)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FloatingResumeButton(
    book: LibraryBook,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    ExtendedFloatingActionButton(
        onClick = onResume,
        modifier = modifier.padding(end = 16.dp, bottom = navBottom + 16.dp),
        icon = {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
            )
        },
        text = {
            Text(
                text = "Resume · ${book.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/**
 * Alpha stops sampled from a smoothstep ease-in-out curve, going from a solid
 * [color] down to fully transparent. A 2–3 stop linear gradient reads as a hard
 * band because the eye latches onto the constant-slope midpoint; sampling the
 * S-curve across many stops keeps the falloff perceptually even, so the fade
 * dissolves instead of drawing a line.
 */
private fun fadeOutStops(color: Color): Array<Pair<Float, Color>> = arrayOf(
    0.00f to color,
    0.12f to color.copy(alpha = 0.96f),
    0.26f to color.copy(alpha = 0.85f),
    0.40f to color.copy(alpha = 0.66f),
    0.52f to color.copy(alpha = 0.46f),
    0.66f to color.copy(alpha = 0.26f),
    0.80f to color.copy(alpha = 0.11f),
    0.92f to color.copy(alpha = 0.03f),
    1.00f to color.copy(alpha = 0f),
)

/** Same smoothstep curve as [fadeOutStops], reversed: transparent rising to solid. */
private fun fadeInStops(color: Color): Array<Pair<Float, Color>> = arrayOf(
    0.00f to color.copy(alpha = 0f),
    0.08f to color.copy(alpha = 0.03f),
    0.20f to color.copy(alpha = 0.11f),
    0.34f to color.copy(alpha = 0.26f),
    0.48f to color.copy(alpha = 0.46f),
    0.60f to color.copy(alpha = 0.66f),
    0.74f to color.copy(alpha = 0.85f),
    0.88f to color.copy(alpha = 0.96f),
    1.00f to color,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SystemBarTopScrim(modifier: Modifier = Modifier) {
    val statusTop =
        WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val background = YomuTheme.colors.appBackground
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusTop + 18.dp)
            // Full peak: status-bar content must stay legible over scrolling covers.
            .background(Brush.verticalGradient(*fadeOutStops(background))),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SystemBarBottomScrim(modifier: Modifier = Modifier) {
    val navBottom =
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val background = YomuTheme.colors.appBackground
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(navBottom + 22.dp)
            .background(Brush.verticalGradient(*fadeInStops(background))),
    )
}
