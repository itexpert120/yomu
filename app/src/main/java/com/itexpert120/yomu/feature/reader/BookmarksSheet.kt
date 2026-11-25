package com.itexpert120.yomu.feature.reader

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.reader.ReaderBookmark

/** A single bookmark row (jump on tap, delete on the trailing button). Used by the Browse sheet. */
@Composable
internal fun BookmarkRow(
    bookmark: ReaderBookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    position: YomuSettingPosition = YomuSettingPosition.Single,
) {
    ReaderBrowseRow(
        title = AnnotatedString(bookmark.chapterTitle?.takeIf { it.isNotBlank() } ?: "Bookmark"),
        onClick = onClick,
        position = position,
        supporting = bookmark.progression?.let { "${(it * 100).toInt()}%" },
        titleMaxLines = 1,
        leading = {
            Icon(
                imageVector = StackIcons.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingIcon = StackIcons.Delete,
        trailingDescription = "Delete bookmark",
        onTrailingClick = onDelete,
    )
}
