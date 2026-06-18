package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Custom switch (not a Material `Switch`): an animated knob in a pill track. */
@Composable
fun YomuTogglePill(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = YomuTheme.colors
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.surfaceSunken,
        label = "togglePillTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        label = "togglePillKnob",
    )
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(trackColor)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onCheckedChange(!checked) },
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) colors.appBackground else colors.textMuted),
        )
    }
}

/** A labeled settings row with a trailing control (toggle, value, etc.). */
@Composable
fun YomuSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.body)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}
