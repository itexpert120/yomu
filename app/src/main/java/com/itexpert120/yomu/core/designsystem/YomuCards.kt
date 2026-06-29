package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun YomuBookCard(
    title: String,
    author: String,
    progress: Float,
    coverColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val colors = YomuTheme.colors
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BookCoverAspectRatio)
                .clip(RoundedCornerShape(YomuTheme.radius.md))
                .background(Brush.verticalGradient(coverColors))
                .border(
                    1.dp,
                    colors.border.copy(alpha = 0.35f),
                    RoundedCornerShape(YomuTheme.radius.md),
                )
                .padding(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.42f)
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.7f)),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = title,
                    color = Color.White,
                    style = YomuTheme.type.section,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = author,
                    color = Color.White.copy(alpha = 0.74f),
                    style = YomuTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(YomuTheme.radius.pill))
                    .background(colors.border),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(colors.textPrimary),
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                color = colors.textMuted,
                style = YomuTheme.type.mono,
            )
        }
    }
}

@Composable
fun YomuSettingGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.lg))
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg))
            .padding(YomuTheme.space.md),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
    ) {
        Text(text = title, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.section)
        content()
    }
}

private const val BookCoverAspectRatio = 1f / 1.6f
