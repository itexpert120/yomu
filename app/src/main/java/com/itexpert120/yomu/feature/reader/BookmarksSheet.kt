package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.reader.ReaderBookmark

/** A single bookmark row (jump on tap, delete on the trailing button). Used by the Browse sheet. */
@Composable
internal fun BookmarkRow(
    bookmark: ReaderBookmark,
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
        Icon(
            Icons.Rounded.Bookmark,
            contentDescription = null,
            tint = YomuTheme.colors.accent,
            modifier = Modifier.size(16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.chapterTitle?.takeIf { it.isNotBlank() } ?: "Bookmark",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${(bookmark.progression * 100).toInt()}%",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
        }
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
