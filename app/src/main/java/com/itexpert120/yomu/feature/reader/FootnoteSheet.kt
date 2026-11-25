package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTheme

/**
 * Bottom sheet that shows a tapped footnote's content in place, instead of the reader jumping to the
 * note at the bottom of the chapter. [html] is the raw note content from the engine, rendered as
 * plain text in the reading serif. The note's own leading label ("[1]", "1.", "*") becomes the
 * sheet's title ("Note 1") rather than repeating in the text.
 */
@Composable
internal fun FootnoteSheet(html: String?, onDismiss: () -> Unit) {
    YomuBottomSheet(visible = html != null, onDismiss = onDismiss) { dismiss ->
        if (html == null) return@YomuBottomSheet
        val note = remember(html) {
            splitFootnoteLabel(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim())
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            YomuSheetHeader(title = note.label?.let { "Note $it" } ?: "Footnote", onClose = dismiss)
            Text(
                text = note.text,
                color = YomuTheme.stack.body,
                style = StackType.Body.copy(fontFamily = rememberReadingSerif(), fontSize = 16.sp, lineHeight = 25.sp),
            )
        }
    }
}

/** A footnote's text with its leading marker taken off; [label] is the marker's number or letter. */
internal data class FootnoteText(val label: String?, val text: String)

// "[1]", "[a]", "(12)", "1.", "12)", "*", "†" at the very start, followed by the note itself.
private val BracketedLabel = Regex("""^[\[(]\s*([\p{L}\p{N}]{1,4}|[*†‡§]{1,3})\s*[\])][.:]?\s*""")
private val NumberedLabel = Regex("""^(\p{N}{1,4})[.)]\s+""")
private val SymbolLabel = Regex("""^([*†‡§]{1,3})\s*""")

/** Strips a note's leading label ("[1]", "1.", "*"), keeping the text whole if nothing would remain. */
internal fun splitFootnoteLabel(raw: String): FootnoteText {
    val text = raw.trim()
    for (pattern in listOf(BracketedLabel, NumberedLabel, SymbolLabel)) {
        val match = pattern.find(text) ?: continue
        val rest = text.substring(match.range.last + 1).trim()
        if (rest.isEmpty()) return FootnoteText(null, text)
        val label = match.groupValues[1].takeIf { it.first().isLetterOrDigit() }
        return FootnoteText(label, rest)
    }
    return FootnoteText(null, text)
}
