package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuTheme

@Composable
internal fun ConfirmRemoveDialog(
    visible: Boolean,
    count: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) return
    // A real Dialog (window-scoped) gives the standard platform dialog animation + dimming,
    // instead of a hand-rolled fade.
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(YomuTheme.radius.panel))
                .background(YomuTheme.colors.panel)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Remove ${if (count == 1) "this book" else "$count books"}?",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.section,
            )
            Text(
                text = "This deletes the imported file${if (count == 1) "" else "s"} and cover from " +
                    "the device. It can't be undone.",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YomuButton(
                    text = "Cancel",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    emphasis = YomuButtonEmphasis.Secondary,
                )
                YomuButton(
                    text = "Remove",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    emphasis = YomuButtonEmphasis.Primary,
                )
            }
        }
    }
}

@Composable
internal fun ImportNotice(
    importing: Boolean,
    notice: String?,
    modifier: Modifier = Modifier,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val text = if (importing) "Importing…" else notice
    // Hold the last shown text so the pill doesn't go blank during its exit animation.
    var lastText by remember { mutableStateOf("") }
    if (text != null) lastText = text

    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.padding(bottom = navBottom + 20.dp),
    ) {
        Row(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(YomuTheme.radius.pill))
                .clip(RoundedCornerShape(YomuTheme.radius.pill))
                .background(YomuTheme.colors.panel)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lastText,
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.control,
            )
        }
    }
}

@Composable
internal fun FloatingResumeButton(
    book: LibraryBook,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Foreground tracks appBackground so the label reads on the accent in every
    // theme (the dark-theme accent is light, where white text would wash out).
    val onAccent = YomuTheme.colors.appBackground
    Column(
        modifier = modifier
            .padding(end = 16.dp, bottom = navBottom + 16.dp)
            .shadow(10.dp, RoundedCornerShape(YomuTheme.radius.lg))
            .clip(RoundedCornerShape(YomuTheme.radius.lg))
            .background(YomuTheme.colors.accent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onResume,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Resume",
                color = onAccent,
                style = YomuTheme.type.control,
            )
        }
        Text(
            text = book.title,
            color = onAccent.copy(alpha = 0.72f),
            style = YomuTheme.type.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(168.dp),
        )
    }
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

@Composable
internal fun SystemBarTopScrim(modifier: Modifier = Modifier) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val background = YomuTheme.colors.appBackground
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusTop + 18.dp)
            // Full peak: status-bar content must stay legible over scrolling covers.
            .background(Brush.verticalGradient(*fadeOutStops(background)))
    )
}

@Composable
internal fun SystemBarBottomScrim(modifier: Modifier = Modifier) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val background = YomuTheme.colors.appBackground
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(navBottom + 22.dp)
            .background(Brush.verticalGradient(*fadeInStops(background)))
    )
}
