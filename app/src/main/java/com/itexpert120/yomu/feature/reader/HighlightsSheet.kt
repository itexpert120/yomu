package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
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
    YomuBottomSheet(visible = highlight != null, onDismiss = onDismiss) { dismiss ->
        if (highlight == null) return@YomuBottomSheet
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
        ) {
            YomuSheetHeader(title = "Highlight", onClose = dismiss)
            if (highlight.text.isNotBlank()) {
                Text(
                    text = highlight.text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HighlightColorPalette(
                selectedArgb = highlight.colorArgb,
                onSelect = onSelectColor,
            )
            TextButton(
                onClick = onDelete,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = "Delete highlight", style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/** A single highlight row: its tint, the highlighted passage, and a delete action. */
@Composable
internal fun HighlightRow(
    highlight: ReaderHighlight,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    position: YomuSettingPosition = YomuSettingPosition.Single,
) {
    ReaderBrowseRow(
        title = AnnotatedString(highlight.text.ifBlank { "Highlight" }),
        onClick = onClick,
        position = position,
        leading = { ReaderSwatch(color = Color(highlight.colorArgb), size = 24.dp) },
        trailingIcon = Icons.Rounded.Delete,
        trailingDescription = "Delete highlight",
        onTrailingClick = onDelete,
    )
}

/** Optional tints for an existing highlight; the current colour morphs to the selected shape. */
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
