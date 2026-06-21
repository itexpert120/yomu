package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.text.HtmlCompat
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuTheme

/**
 * Bottom sheet that shows a tapped footnote's content in place, instead of the reader jumping to the
 * note at the bottom of the chapter. [html] is the raw note content from the engine; we render it as
 * plain text (footnotes are short and the design system styles the type).
 */
@Composable
internal fun FootnoteSheet(html: String?, onDismiss: () -> Unit) {
    YomuBottomSheet(visible = html != null, onDismiss = onDismiss) { _ ->
        if (html == null) return@YomuBottomSheet
        val text = remember(html) {
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.body,
            )
        }
    }
}
