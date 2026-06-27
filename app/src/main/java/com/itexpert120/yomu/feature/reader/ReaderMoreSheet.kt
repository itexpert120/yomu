package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuPressable

/** The bottom-bar "More" overflow: less-frequent reader actions kept off the primary controls. */
@Composable
internal fun ReaderMoreSheet(
    visible: Boolean,
    onChapterStart: () -> Unit,
    onChapterEnd: () -> Unit,
    onDismiss: () -> Unit,
) {
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "More",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.body,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            MoreRow(Icons.Rounded.VerticalAlignTop, "Chapter start", onChapterStart)
            MoreRow(Icons.Rounded.VerticalAlignBottom, "Chapter end", onChapterEnd)
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .yomuPressable(onClick = onClick)
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = YomuTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(text = label, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.body)
    }
}
