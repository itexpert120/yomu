package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Short, indeterminate waits (roughly 200ms–5s): the M3 Expressive loading indicator, a looping
 * morph through Material shapes. Use [contained] when it sits over other content.
 */
@Composable
fun YomuLoadingIndicator(
    modifier: Modifier = Modifier,
    contained: Boolean = false,
) {
    if (contained) ContainedLoadingIndicator(modifier = modifier) else LoadingIndicator(modifier = modifier)
}

/** A centred loading indicator with an optional caption — for whole-pane loading states. */
@Composable
fun YomuLoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        YomuLoadingIndicator()
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Determinate reading progress. The wavy M3 Expressive style marks the book's hero progress; the
 * flat style suits dense rows where a wave would be visual noise.
 */
@Composable
fun YomuProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    wavy: Boolean = false,
) {
    val value = progress.coerceIn(0f, 1f)
    if (wavy) {
        LinearWavyProgressIndicator(
            progress = { value },
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(
            progress = { value },
            modifier = modifier.fillMaxWidth().height(6.dp),
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.secondaryContainer,
        )
    }
}
