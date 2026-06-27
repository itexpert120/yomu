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
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.reader.ReaderHighlight

/** Edit popup for an existing highlight: recolour (optional) or delete it. */
@Composable
internal fun HighlightEditSheet(
    highlight: ReaderHighlight?,
    onSelectColor: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            HighlightColorPalette(
                selectedArgb = highlight.colorArgb,
                onSelect = onSelectColor,
            )
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

/** Optional tint chips for an existing highlight; the current colour is ringed. */
@Composable
private fun HighlightColorPalette(
    selectedArgb: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = listOf(
        YomuTheme.colors.highlightYellow,
        YomuTheme.colors.highlightGreen,
        YomuTheme.colors.highlightBlue,
        YomuTheme.colors.highlightPink,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { color ->
            val argb = color.toArgb()
            val selected = argb == selectedArgb
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, YomuTheme.colors.textPrimary, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(argb) },
                    ),
            )
        }
    }
}
