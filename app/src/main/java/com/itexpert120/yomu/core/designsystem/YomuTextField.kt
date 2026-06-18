package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/** Labeled, custom (non-Material) text field used by editors and forms. */
@Composable
fun YomuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(YomuTheme.radius.md))
                .background(YomuTheme.colors.surface)
                .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.md))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = YomuTheme.type.body.copy(color = YomuTheme.colors.textPrimary),
                cursorBrush = SolidColor(YomuTheme.colors.accent),
                singleLine = singleLine,
                minLines = minLines,
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = YomuTheme.colors.textMuted,
                            style = YomuTheme.type.body,
                        )
                    }
                    inner()
                },
            )
        }
    }
}
