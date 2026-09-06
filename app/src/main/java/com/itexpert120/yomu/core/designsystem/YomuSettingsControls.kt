package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class YomuSettingPosition { Single, First, Middle, Last }

/** Material 3 switch compatibility wrapper used by the settings screens. */
@Composable
fun YomuTogglePill(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

/** A Material 3 settings list item with a trailing control. */
@Composable
fun YomuSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    position: YomuSettingPosition? = null,
    trailing: @Composable () -> Unit,
) {
    ListItem(
        supportingContent = subtitle?.let { text ->
            {
                Text(
                    text = text,
                    color = if (!enabled) {
                        YomuTheme.colors.textSecondary.copy(alpha = 0.38f)
                    } else if (selected) {
                        androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                    } else {
                        YomuTheme.colors.textSecondary
                    },
                    style = YomuTheme.type.caption,
                )
            }
        },
        leadingContent = leadingContent,
        trailingContent = { trailing() },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
            } else {
                if (position != null) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (position != null) {
                    Modifier.clip(
                        RoundedCornerShape(
                            topStart = if (position == YomuSettingPosition.First || position == YomuSettingPosition.Single) 24.dp else 4.dp,
                            topEnd = if (position == YomuSettingPosition.First || position == YomuSettingPosition.Single) 24.dp else 4.dp,
                            bottomStart = if (position == YomuSettingPosition.Last || position == YomuSettingPosition.Single) 24.dp else 4.dp,
                            bottomEnd = if (position == YomuSettingPosition.Last || position == YomuSettingPosition.Single) 24.dp else 4.dp,
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.yomuPressable(
                        onClick = onClick,
                        enabled = enabled,
                        role = Role.Button,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { this.selected = selected },
    ) {
        Text(
            text = title,
            color = if (!enabled) {
                YomuTheme.colors.textPrimary.copy(alpha = 0.38f)
            } else if (selected) {
                androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                YomuTheme.colors.textPrimary
            },
            style = YomuTheme.type.body,
        )
    }
}

/** A contiguous Material 3 list for related settings rows. */
@Composable
fun YomuSettingList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

/** Divider used between related settings rows. */
@Composable
fun YomuSettingDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = YomuTheme.colors.textMuted.copy(alpha = 0.24f),
    )
}
