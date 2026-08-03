package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    trailing: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(text = title, style = YomuTheme.type.body)
        },
        supportingContent = subtitle?.let { text ->
            {
                Text(text = text, style = YomuTheme.type.caption)
            }
        },
        trailingContent = {
            Spacer(Modifier.width(12.dp))
            trailing()
        },
        modifier = modifier,
    )
}
