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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme

@Composable
internal fun FloatingResumeButton(modifier: Modifier = Modifier, book: LibraryBook) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier = modifier
            .padding(end = 20.dp, bottom = navBottom + 20.dp)
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.textPrimary)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "Resume", color = YomuTheme.colors.appBackground, style = YomuTheme.type.control)
        Text(
            text = "${(book.progress * 100).toInt()}%",
            color = YomuTheme.colors.appBackground.copy(alpha = 0.72f),
            style = YomuTheme.type.mono,
        )
    }
}

@Composable
internal fun BookContextPanel(
    book: LibraryBook,
    onDismiss: () -> Unit,
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
                    text = "Book actions",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            }
            ContextAction("Close", onClick = onDismiss)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContextAction("Details")
            ContextAction("Move to group")
            ContextAction("Mark read")
            ContextAction("Remove")
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

@Composable
internal fun SystemBarTopScrim(modifier: Modifier = Modifier) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val background = YomuTheme.colors.appBackground
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusTop + 10.dp)
            .background(
                Brush.verticalGradient(
                    0.00f to background,
                    0.58f to background.copy(alpha = 0.96f),
                    0.82f to background.copy(alpha = 0.42f),
                    1.00f to background.copy(alpha = 0f),
                )
            )
    )
}

@Composable
internal fun SystemBarBottomScrim(modifier: Modifier = Modifier) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val background = YomuTheme.colors.appBackground
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(navBottom + 12.dp)
            .background(
                Brush.verticalGradient(
                    0.00f to background.copy(alpha = 0f),
                    0.32f to background.copy(alpha = 0.30f),
                    0.72f to background.copy(alpha = 0.92f),
                    1.00f to background,
                )
            )
    )
}
