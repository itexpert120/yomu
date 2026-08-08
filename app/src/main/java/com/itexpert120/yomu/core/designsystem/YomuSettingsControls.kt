package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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
    leadingContent: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = if (enabled) {
                    YomuTheme.colors.textPrimary
                } else {
                    YomuTheme.colors.textPrimary.copy(alpha = 0.38f)
                },
                style = YomuTheme.type.body,
            )
        },
        supportingContent = subtitle?.let { text ->
            {
                Text(
                    text = text,
                    color = if (enabled) {
                        YomuTheme.colors.textSecondary
                    } else {
                        YomuTheme.colors.textSecondary.copy(alpha = 0.38f)
                    },
                    style = YomuTheme.type.caption,
                )
            }
        },
        leadingContent = leadingContent,
        trailingContent = { trailing() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth(),
    )
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
