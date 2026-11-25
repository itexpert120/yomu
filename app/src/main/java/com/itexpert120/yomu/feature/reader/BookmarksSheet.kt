package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.reader.ReaderBookmark

/**
 * A bookmark under its chapter heading: the passage it marks when the position carries one (else
 * where in the book it is), when it was made, delete on the trailing button.
 */
@Composable
internal fun BookmarkRow(
    bookmark: ReaderBookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    divider: Boolean = true,
) {
    val passage = remember(bookmark.locatorJson) { LocatorFacts.parse(bookmark.locatorJson)?.text }
    val percent = bookmark.progression?.let { "${(it * 100).toInt()}%" }
    // The position goes in the caption when the passage is the title, else it is the title.
    val caption = remember(passage, percent, bookmark.createdAt) {
        listOfNotNull(percent?.takeIf { passage != null }?.let { "$it of the book" }, readerTimestamp(bookmark.createdAt)).joinToString(" · ")
    }
    val stack = YomuTheme.stack
    ReaderBrowseRow(
        title = AnnotatedString(passage ?: percent?.let { "Page at $it" } ?: "Bookmarked page"),
        caption = caption,
        onClick = onClick,
        titleMaxLines = 2,
        titleStyle = if (passage != null) {
            StackType.BodySmall.copy(fontFamily = rememberReadingSerif(), color = stack.body)
        } else {
            StackType.Body
        },
        divider = divider,
        leading = {
            Icon(StackIcons.BookmarkFilled, contentDescription = null, tint = stack.muted, modifier = Modifier.size(20.dp))
        },
        trailingIcon = StackIcons.Close,
        trailingDescription = "Delete bookmark",
        onTrailingClick = onDelete,
    )
}
