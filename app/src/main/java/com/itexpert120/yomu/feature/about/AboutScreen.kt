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
                .clip(RoundedCornerShape(YomuTheme.radius.lg))
                .background(YomuTheme.colors.surfaceRaised)
                .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
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
                    painter = painterResource(R.drawable.open_reader_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(64.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Open Reader",
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.display,
                )
                Text(
                    text = "Private · offline · EPUB reader",
                    color = YomuTheme.colors.textSecondary,
                    style = YomuTheme.type.body,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(YomuTheme.radius.pill))
                        .background(YomuTheme.colors.surfaceSunken)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.mono,
                    )
                }
            }
        }

        YomuSettingGroup(title = "About") {
            Text(
                text = "Open Reader is a native Android EPUB reader focused on a calm, " +
                    "reader-first experience with deep typography and theme control.",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
        }

        YomuSettingGroup(title = "Privacy policy") {
            Text(
                text = "Effective 14 July 2026 · Open Reader is developed by IT Expert 120. Privacy " +
                    "questions can be sent to itexpert120@outlook.com.",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
            Text(
                text = "Data kept on your device",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.section,
            )
            Text(
                text = "Imported EPUB files, covers, book metadata, reading positions, chapter " +
                    "progress, bookmarks, highlights, reading statistics, installed fonts, and " +
                    "settings are stored in Open Reader’s private app storage. Open Reader has no accounts, " +
                    "advertising, analytics, tracking SDKs, or developer-operated servers. Your " +
                    "books and reading history are not uploaded.",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
            Text(
                text = "Optional network features",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.section,
            )
            Text(
                text = "When you choose Dictionary “Look up,” Open Reader sends only the selected word " +
                    "and language code over HTTPS to freedictionaryapi.com. When you install a " +
                    "Google Font, Open Reader sends the requested font-family name to " +
                    "fonts.googleapis.com and downloads font files from Google-hosted servers. " +
                    "Those services also receive ordinary connection information such as your IP " +
                    "address and user agent. Open Reader does not control their server logs or retention. " +
                    "When you choose “Search web,” Open Reader opens a Google search in your external " +
                    "browser; the browser and Google then handle that request under their own " +
                    "privacy terms. Pronunciation uses your device’s text-to-speech service.",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
            Text(
                text = "Security, retention, and deletion",
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.section,
            )
            Text(
                text = "Network requests initiated by Open Reader use HTTPS. Local app data is excluded " +
                    "from Android cloud backup and device transfer. Removing a book deletes its " +
                    "app-private EPUB, cover, progress, bookmarks, and highlights. Other local data " +
                    "remains until you clear Open Reader’s storage or uninstall the app. Open Reader cannot " +
                    "delete logs independently retained by external dictionary, font, browser, or " +
                    "text-to-speech providers.",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
            Text(
                text = "Open Reader is not specifically directed to children and does not supply books " +
                    "or other reading content. You control the EPUB files you import. Material " +
                    "changes to this policy will be reflected here with a new effective date.",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
        }

        YomuSettingGroup(title = "Terms of use") {
            Text(
                text = "Open Reader is provided “as is”, without warranty of any kind, to the fullest " +
                    "extent permitted by law. You are responsible for the books you import and " +
                    "for complying with their licenses and applicable copyright law. Open Reader does " +
                    "not provide, sell, or distribute any books.",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
        }

        YomuSettingGroup(title = "Acknowledgements") {
            Text(
                text = "EPUB parsing and rendering by the Readium Kotlin toolkit (BSD-3-Clause). " +
                    "Cover loading by Coil. Built with Jetpack Compose. Bundled reading fonts " +
                    "are used under the SIL Open Font License. " +
                    "Dictionary definitions from freedictionaryapi.com, sourced from Wiktionary " +
                    "(CC BY-SA).",
                color = YomuTheme.colors.textSecondary,
                style = YomuTheme.type.body,
            )
        }

        Text(
            text = "© 2026 IT Expert 120. Open Reader is an independent application.",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, bottom = 8.dp),
        )
    }
}
