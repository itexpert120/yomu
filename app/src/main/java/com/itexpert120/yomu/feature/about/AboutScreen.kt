package com.itexpert120.yomu.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.R
import com.itexpert120.yomu.BuildConfig
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuTheme

@Composable
fun AboutRoute(onBack: () -> Unit) {
    AboutScreen(onBack = onBack)
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    YomuScreenScaffold(title = "About", onBack = onBack) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(YomuTheme.radius.lg))
                    .background(YomuTheme.colors.accent)
                    .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_yomu_mark),
                    contentDescription = null,
                    tint = YomuTheme.colors.appBackground,
                    modifier = Modifier.size(46.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = "Yomu", color = YomuTheme.colors.textPrimary, style = YomuTheme.type.display)
                Text(
                    text = "読む · EPUB reader",
                    color = YomuTheme.colors.textSecondary,
                    style = YomuTheme.type.body,
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.mono,
                )
            }
        }

        YomuSettingGroup(title = "About") {
            Text(
                text = "Yomu is a native Android EPUB reader focused on a calm, reader-first " +
                    "experience with deep typography and theme control.",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
        }

        YomuSettingGroup(title = "Built with") {
            Text(
                text = "Jetpack Compose · Kotlin Coroutines · DataStore",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
            Text(
                text = "EPUB rendering via Readium (planned).",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
        }
    }
}
