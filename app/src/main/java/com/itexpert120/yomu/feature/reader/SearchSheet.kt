package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.reader.ReaderSearchResult

/** A single in-book search match (chapter + highlighted context). Used by the Browse sheet's Search tab. */
@Composable
internal fun SearchResultRow(
    result: ReaderSearchResult,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        result.chapterTitle?.takeIf { it.isNotBlank() }?.let { chapter ->
            Text(
                text = chapter,
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = buildAnnotatedString {
                // Trim long context so the match stays visible within two lines.
                append(result.before.takeLast(48))
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                    append(result.match)
                }
                append(result.after.take(64))
            },
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
