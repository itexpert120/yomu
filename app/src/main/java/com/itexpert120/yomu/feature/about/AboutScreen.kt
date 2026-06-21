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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.BuildConfig
import com.itexpert120.yomu.R
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuTheme

@Composable
fun AboutRoute(onBack: () -> Unit) {
    AboutScreen(onBack = onBack)
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    YomuScreenScaffold(title = "About", onBack = onBack) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(YomuTheme.radius.lg))
                    .background(Color(0xFF050505))
                    .border(
                        1.dp,
                        YomuTheme.colors.border.copy(alpha = 0.6f),
                        RoundedCornerShape(YomuTheme.radius.lg),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_yomu_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(72.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Yomu",
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.display
                )
                Text(
                    text = "読む · EPUB reader",
                    color = YomuTheme.colors.textSecondary,
                    style = YomuTheme.type.body,
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
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

        YomuSettingGroup(title = "Privacy") {
            Text(
                text = "Yomu keeps your library, reading progress, highlights, and statistics on " +
                        "this device only. There are no accounts, ads, or analytics, and nothing " +
                        "about your reading is collected or shared. The books you add never leave " +
                        "your device.",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
            Text(
                text = "The only feature that uses the internet is dictionary “Look up”: the " +
                        "selected word is sent to a third-party dictionary service " +
                        "(freedictionaryapi.com) to fetch its definition. Pronunciation is spoken " +
                        "on-device by your system text-to-speech. No other data is transmitted.",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
        }

        YomuSettingGroup(title = "Terms of use") {
            Text(
                text = "Yomu is provided “as is”, without warranty of any kind, to the fullest " +
                        "extent permitted by law. You are responsible for the books you import and " +
                        "for complying with their licenses and applicable copyright law. Yomu does " +
                        "not provide, sell, or distribute any books.",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
        }

        YomuSettingGroup(title = "Acknowledgements") {
            Text(
                text = "EPUB parsing and rendering by the Readium Kotlin toolkit (BSD-3-Clause). " +
                        "Charts by Vico (Apache-2.0). Cover loading by Coil. Built with Jetpack " +
                        "Compose. Bundled reading fonts are used under the SIL Open Font License. " +
                        "Dictionary definitions from freedictionaryapi.com, sourced from Wiktionary " +
                        "(CC BY-SA).",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
        }

        Text(
            text = "© 2026 Yomu. All rights reserved.",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, bottom = 8.dp),
        )
    }
}
