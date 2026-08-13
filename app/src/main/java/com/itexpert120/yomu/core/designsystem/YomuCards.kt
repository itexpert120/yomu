package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(YomuTheme.space.sm)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BookCoverAspectRatio)
                    .clip(MaterialTheme.shapes.large)
                    .background(Brush.verticalGradient(coverColors))
                    .padding(YomuTheme.space.sm),
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
                    Spacer(Modifier.height(YomuTheme.space.xxs))
                    Text(
                        text = author,
                        color = Color.White.copy(alpha = 0.78f),
                        style = YomuTheme.type.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(YomuTheme.space.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
            ) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = YomuTheme.type.caption,
                )
            }
        }
    }
}

@Composable
fun YomuSettingGroup(
    title: String,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (outlined) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(YomuTheme.space.md),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = YomuTheme.type.section,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = YomuTheme.type.caption,
                    )
                }
            }
            content()
        }
    }
}

private const val BookCoverAspectRatio = 1f / 1.6f
