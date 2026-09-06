package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuButton

@Composable
internal fun ContinueReading(book: LibraryBook, onResume: () -> Unit) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.padding(24.dp)) {
            val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f
            val coverWidth = if (maxWidth >= 600.dp) 128.dp else 96.dp
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BookCoverImage(book, Modifier.width(80.dp))
                    ContinueReadingSummary(book, onResume)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCoverImage(book, Modifier.width(coverWidth))
                    ContinueReadingSummary(book, onResume, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingSummary(book: LibraryBook, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val progress = book.progress.coerceIn(0f, 1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Continue reading", style = MaterialTheme.typography.labelLarge)
        Text(book.title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(book.author, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
            drawStopIndicator = {},
        )
        Text("${(progress * 100).toInt()}% read", style = MaterialTheme.typography.labelLarge)
        YomuButton(text = "Resume", onClick = onResume, prominent = true)
    }
}
