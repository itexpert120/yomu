package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuPanel
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuTheme

/**
 * Stand-in for the reader screen. The real reader (Readium behind a Yomu engine boundary) is a
 * later roadmap phase; Resume/open actions route here so navigation is exercised end-to-end.
 */
@Composable
fun ReaderPlaceholderScreen(bookId: String, onBack: () -> Unit) {
    YomuScreenScaffold(title = "Reader", onBack = onBack) {
        YomuPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Reader coming soon",
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.section,
                )
                Text(
                    text = "The EPUB reading surface lands in a later phase. This screen confirms " +
                        "navigation into book “$bookId” works.",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.body,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
