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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.reader.HighlightStyle
import com.itexpert120.yomu.core.reader.ReaderHighlight
import kotlinx.coroutines.delay

/**
 * The highlight sheet, for a highlight just made or one tapped on the page: its colour (the board's
 * palette), how it's drawn, a note, then Copy text / Share / Delete as on the board.
 */
@Composable
internal fun HighlightEditSheet(
    highlight: ReaderHighlight?,
    onSelectColor: (Int) -> Unit,
    onSelectStyle: (HighlightStyle) -> Unit,
    onNoteChange: (id: String, note: String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    YomuBottomSheet(visible = highlight != null, onDismiss = onDismiss) { dismiss ->
        if (highlight == null) return@YomuBottomSheet
        HighlightEditSheetContent(highlight, onSelectColor, onSelectStyle, onNoteChange, onCopy, onShare, onDelete, dismiss)
    }
}

/** The highlight sheet's body, separate from its sheet so it can be previewed. */
@Composable
internal fun HighlightEditSheetContent(
    highlight: ReaderHighlight,
    onSelectColor: (Int) -> Unit,
    onSelectStyle: (HighlightStyle) -> Unit,
    onNoteChange: (id: String, note: String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    dismiss: () -> Unit,
) {
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
        YomuConnectedChoiceGroup(
            options = HighlightStyle.entries,
            selected = highlight.style,
            label = { it.label },
            onSelect = onSelectStyle,
            icon = { it.icon },
            iconOnTop = true,
            modifier = Modifier.fillMaxWidth(),
        )
        HighlightNoteField(highlight, onNoteChange)
        Column(Modifier.padding(top = YomuTheme.space.xxs)) {
            HighlightActionRow(StackIcons.ContentCopy, "Copy text", onClick = onCopy)
            HighlightActionRow(StackIcons.Share, "Share", onClick = onShare)
            HighlightActionRow(StackIcons.Delete, "Delete highlight", danger = true, onClick = onDelete)
        }
    }
}

/**
 * The note, saved as it's typed (after a short pause) and when the sheet closes; clearing it
 * removes the note. Local text survives recomposition and rotation; the id goes with every save.
 */
@Composable
private fun HighlightNoteField(highlight: ReaderHighlight, onNoteChange: (id: String, note: String) -> Unit) {
    var note by rememberSaveable(highlight.id) { mutableStateOf(highlight.note.orEmpty()) }
    val currentOnNoteChange by rememberUpdatedState(onNoteChange)
    val latest by rememberUpdatedState(note)
    val saved by rememberUpdatedState(highlight.note?.trim().orEmpty())
    LaunchedEffect(highlight.id, note) {
        if (note.trim() == highlight.note?.trim().orEmpty()) return@LaunchedEffect
        delay(NoteSaveDelayMillis)
        currentOnNoteChange(highlight.id, note)
    }
    DisposableEffect(highlight.id) {
        onDispose { if (latest.trim() != saved) currentOnNoteChange(highlight.id, latest) }
    }
    YomuTextField(
        value = note,
        onValueChange = { note = it },
        label = "Note",
        placeholder = "Add a note",
        singleLine = false,
        minLines = 2,
        capitalization = KeyboardCapitalization.Sentences,
    )
}

/** One of the board's rows under a rule: muted icon, 15 text; Delete in the danger colour. */
@Composable
private fun HighlightActionRow(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val stack = YomuTheme.stack
    Column {
        ReaderRule()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .yomuPressable(onClick = onClick, pressedScale = 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) stack.danger else stack.muted)
            Text(text = text, color = if (danger) stack.danger else stack.ink, style = StackType.Body)
        }
    }
}

/**
 * A single highlight row: its tint as a bar, the passage in the reading serif, its note under it
 * (with a note mark), and delete.
 */
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
        supporting = highlight.note?.takeIf { it.isNotBlank() }?.let { note ->
            {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        StackIcons.Notes,
                        contentDescription = "Note",
                        tint = YomuTheme.stack.muted,
                        modifier = Modifier.padding(top = 2.dp).size(16.dp),
                    )
                    Text(
                        text = note,
                        color = YomuTheme.stack.muted,
                        style = StackType.BodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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

private const val NoteSaveDelayMillis = 700L

private val HighlightStyle.label: String
    get() = when (this) {
        HighlightStyle.Highlight -> "Highlight"
        HighlightStyle.Underline -> "Underline"
        HighlightStyle.Squiggly -> "Squiggly"
        HighlightStyle.Strikethrough -> "Strike"
    }

private val HighlightStyle.icon: ImageVector
    get() = when (this) {
        HighlightStyle.Highlight -> HighlightStyleIcons.Highlight
        HighlightStyle.Underline -> HighlightStyleIcons.Underline
        HighlightStyle.Squiggly -> HighlightStyleIcons.Squiggly
        HighlightStyle.Strikethrough -> HighlightStyleIcons.Strikethrough
    }

/**
 * Small drawn previews of each style: a letter "A" marked the way the style marks text. Drawn in
 * one colour so the segmented control tints them like any icon; the highlight's block is a lighter
 * fill of the same colour.
 */
internal object HighlightStyleIcons {
    val Highlight: ImageVector by lazy {
        styleIcon("HighlightStyle.Highlight") {
            path(fill = SolidColor(Color.Black), fillAlpha = 0.28f) {
                moveTo(3.5f, 4f)
                lineTo(20.5f, 4f)
                lineTo(20.5f, 20f)
                lineTo(3.5f, 20f)
                close()
            }
        }
    }
    val Underline: ImageVector by lazy {
        styleIcon("HighlightStyle.Underline") {
            stroke(2f) {
                moveTo(4.5f, 21f)
                lineTo(19.5f, 21f)
            }
        }
    }
    val Squiggly: ImageVector by lazy {
        styleIcon("HighlightStyle.Squiggly") {
            stroke(1.6f) {
                moveTo(3.5f, 21f)
                quadTo(5.375f, 19f, 7.25f, 21f)
                quadTo(9.125f, 23f, 11f, 21f)
                quadTo(12.875f, 19f, 14.75f, 21f)
                quadTo(16.625f, 23f, 18.5f, 21f)
                quadTo(19.5f, 20f, 20.5f, 20.6f)
            }
        }
    }
    val Strikethrough: ImageVector by lazy {
        styleIcon("HighlightStyle.Strikethrough") {
            stroke(2f) {
                moveTo(4f, 10.5f)
                lineTo(20f, 10.5f)
            }
        }
    }

    // The letter: two legs and a crossbar, in 1.5 strokes, over the style's mark.
    private fun styleIcon(name: String, mark: ImageVector.Builder.() -> Unit): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        mark()
        stroke(1.5f) {
            moveTo(7f, 17.5f)
            lineTo(12f, 6f)
            lineTo(17f, 17.5f)
            moveTo(8.7f, 14.5f)
            lineTo(15.3f, 14.5f)
        }
    }.build()

    private fun ImageVector.Builder.stroke(width: Float, pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) = path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}
