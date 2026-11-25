package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.reader.ReaderHighlight

/** Edit sheet for an existing highlight: recolour it or delete it. */
@Composable
internal fun HighlightEditSheet(
    highlight: ReaderHighlight?,
    onSelectColor: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    YomuBottomSheet(visible = highlight != null, onDismiss = onDismiss) { dismiss ->
        if (highlight == null) return@YomuBottomSheet
        HighlightEditSheetContent(highlight, onSelectColor, onDelete, dismiss)
    }
}

/** The highlight sheet's body, separate from its sheet so it can be previewed. */
@Composable
internal fun HighlightEditSheetContent(
    highlight: ReaderHighlight,
    onSelectColor: (Int) -> Unit,
    onDelete: () -> Unit,
    dismiss: () -> Unit,
) {
    run {
        val stack = YomuTheme.stack
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
        ) {
            YomuSheetHeader(title = "Highlight", onClose = dismiss)
            if (highlight.text.isNotBlank()) {
                Text(
                    text = highlight.text,
                    color = stack.body,
                    style = StackType.BodySmall.copy(fontFamily = rememberReadingSerif()),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HighlightColorPalette(
                selectedArgb = highlight.colorArgb,
                onSelect = onSelectColor,
            )
            Column(Modifier.padding(top = YomuTheme.space.xs)) {
                ReaderRule()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .yomuPressable(onClick = onDelete, pressedScale = 1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(StackIcons.Delete, contentDescription = null, tint = stack.danger)
                    Text(text = "Delete highlight", color = stack.danger, style = StackType.Body)
                }
            }
        }
    }
}

/** A single highlight row: its tint as a bar, the passage in the reading serif, and delete. */
@Composable
internal fun HighlightRow(
    highlight: ReaderHighlight,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    divider: Boolean = true,
) {
    val serif = rememberReadingSerif()
    val stamp = remember(highlight.createdAt) { readerTimestamp(highlight.createdAt) }
    ReaderBrowseRow(
        title = AnnotatedString(highlight.text.ifBlank { "Highlight" }),
        caption = stamp,
        onClick = onClick,
        titleMaxLines = 3,
        titleStyle = StackType.BodySmall.copy(fontFamily = serif, color = YomuTheme.stack.body),
        divider = divider,
        leading = {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(highlight.colorArgb)),
            )
        },
        trailingIcon = StackIcons.Close,
        trailingDescription = "Delete highlight",
        onTrailingClick = onDelete,
    )
}

/** Optional tints for an existing highlight; the current colour carries a check and a ring. */
@Composable
private fun HighlightColorPalette(
    selectedArgb: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = listOf(
        "Yellow" to YomuTheme.reading.highlightYellow,
        "Green" to YomuTheme.reading.highlightGreen,
        "Blue" to YomuTheme.reading.highlightBlue,
        "Pink" to YomuTheme.reading.highlightPink,
    )
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { (name, color) ->
            val argb = color.toArgb()
            ReaderSwatch(
                color = color,
                selected = argb == selectedArgb,
                contentDescription = "$name highlight",
                onClick = { onSelect(argb) },
            )
        }
    }
}
