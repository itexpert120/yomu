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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A grid item: cover (2:3, radius 4, the static cover shadow), title 13/600 and a progress line. */
@Composable
fun YomuBookCard(
    title: String,
    author: String,
    progress: Float,
    coverColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(YomuBookCoverAspectRatio)
                .stackCoverShadow()
                .clip(YomuShapeTokens.cover)
                .background(Brush.verticalGradient(coverColors)),
        )
        Spacer(Modifier.height(8.dp))
        Text(text = title, style = StackType.Label, color = stack.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = author, style = StackType.Caption, color = stack.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
            YomuProgressBar(progress = progress, modifier = Modifier.weight(1f))
            Text(text = "${(progress * 100).toInt()}%", color = stack.muted, style = StackType.Caption)
        }
    }
}

/**
 * A titled settings section in Stack's settings list: a 13/600 muted section label (sentence case)
 * with an optional supporting line, then the section's rows, which draw their own group card
 * (sheet-coloured, radius 24) on the desk. [outlined] wraps the whole section in a sheet card
 * instead, for sections whose content isn't rows.
 */
@Composable
fun YomuSettingGroup(
    title: String,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stack = YomuTheme.stack
    val header: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = StackType.Label, color = stack.muted)
            if (subtitle != null) Text(text = subtitle, style = StackType.Supporting, color = stack.muted)
        }
    }
    if (outlined) {
        Column(modifier = modifier.fillMaxWidth().padding(top = YomuTheme.space.md)) {
            header()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(stack.sheet)
                    .padding(YomuTheme.space.md),
                content = content,
            )
        }
    } else {
        Column(modifier = modifier.fillMaxWidth().padding(top = YomuTheme.space.md)) {
            header()
            Column(content = content)
        }
    }
}
