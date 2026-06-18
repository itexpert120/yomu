package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme

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

@Composable
internal fun BookContextPanel(
    book: LibraryBook,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, bottom = navBottom + 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.panel))
            .background(YomuTheme.colors.panel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.section,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            }
            ContextAction("Close", onClick = onDismiss)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContextAction("Details")
            ContextAction("Mark read", onClick = onMarkRead)
            ContextAction("Remove", onClick = onRemove)
        }
    }
}

@Composable
private fun ContextAction(text: String, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = YomuTheme.colors.textSecondary, style = YomuTheme.type.caption)
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
