package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuColors
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.reader.ReaderHighlight

/** The highlight colour palette, drawn from the design-system highlight tokens (packed ARGB). */
@Composable
internal fun highlightPalette(): List<Int> {
    val c: YomuColors = YomuTheme.colors
    return remember(c) {
        listOf(
            c.highlightYellow.toArgb(),
            c.highlightGreen.toArgb(),
            c.highlightBlue.toArgb(),
            c.highlightPink.toArgb(),
        )
    }
}

/**
 * Colour picker shown after the user taps "Highlight" on a text selection. Picking a swatch persists
 * the highlight; dismissing cancels.
 */
@Composable
internal fun HighlightColorPickerSheet(
    visible: Boolean,
    snippet: String?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = highlightPalette()
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Highlight", color = YomuTheme.colors.textPrimary, style = YomuTheme.type.body)
            if (!snippet.isNullOrBlank()) {
                Text(
                    text = snippet,
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SwatchRow(palette = palette, selectedArgb = null, onPick = onPick)
        }
    }
}

/** Edit popup for an existing highlight: recolour or delete. */
@Composable
internal fun HighlightEditSheet(
    highlight: ReaderHighlight?,
    onChangeColor: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = highlightPalette()
    YomuBottomSheet(visible = highlight != null, onDismiss = onDismiss) { _ ->
        if (highlight == null) return@YomuBottomSheet
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Highlight", color = YomuTheme.colors.textPrimary, style = YomuTheme.type.body)
            if (highlight.text.isNotBlank()) {
                Text(
                    text = highlight.text,
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SwatchRow(palette = palette, selectedArgb = highlight.colorArgb, onPick = onChangeColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(YomuTheme.radius.pill))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    )
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = YomuTheme.colors.danger,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = "Delete", color = YomuTheme.colors.danger, style = YomuTheme.type.control)
            }
        }
    }
}

/** List of this book's highlights. Tap to jump to the position; per-row delete. */
@Composable
internal fun HighlightsSheet(
    visible: Boolean,
    highlights: List<ReaderHighlight>,
    onJump: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    YomuBottomSheet(visible = visible, onDismiss = onDismiss, scrollable = false) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Highlights",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.body,
            )
            if (highlights.isEmpty()) {
                Text(
                    text = "No highlights yet. Select text and tap Highlight.",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(highlights, key = { it.id }) { item ->
                        HighlightRow(
                            highlight = item,
                            onClick = { onJump(item.locatorJson) },
                            onDelete = { onDelete(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightRow(
    highlight: ReaderHighlight,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(highlight.colorArgb)),
        )
        Text(
            text = highlight.text.ifBlank { "Highlight" },
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Delete",
                tint = YomuTheme.colors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SwatchRow(palette: List<Int>, selectedArgb: Int?, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        palette.forEach { argb ->
            val selected = selectedArgb == argb
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) YomuTheme.colors.textPrimary
                        else YomuTheme.colors.border,
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPick(argb) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
