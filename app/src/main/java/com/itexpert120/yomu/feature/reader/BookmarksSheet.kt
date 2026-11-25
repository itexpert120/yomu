package com.itexpert120.yomu.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.reader.ReaderBookmark

/** A single bookmark row (jump on tap, delete on the trailing button). Used by the Browse sheet. */
@Composable
internal fun BookmarkRow(
    bookmark: ReaderBookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    divider: Boolean = true,
) {
    val caption = remember(bookmark.progression, bookmark.createdAt) {
        listOfNotNull(
            bookmark.progression?.let { "${(it * 100).toInt()}%" },
            readerTimestamp(bookmark.createdAt),
        ).joinToString(" · ")
    }
    ReaderBrowseRow(
        title = AnnotatedString(bookmark.chapterTitle?.takeIf { it.isNotBlank() } ?: "Bookmark"),
        caption = caption,
        onClick = onClick,
        titleMaxLines = 2,
        divider = divider,
        trailingIcon = StackIcons.Close,
        trailingDescription = "Delete bookmark",
        onTrailingClick = onDelete,
    )
}
