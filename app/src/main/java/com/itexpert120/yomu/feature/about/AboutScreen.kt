package com.itexpert120.yomu.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.BuildConfig
import com.itexpert120.yomu.R
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTheme

@Composable
fun AboutRoute(onBack: () -> Unit) {
    AboutScreen(onBack = onBack)
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    YomuScreenScaffold(
        title = "About Open Reader",
        subtitle = "A private, reader-first library",
        onBack = onBack,
        showScrollEdgeShadow = false,
    ) {
        AboutContent()
    }
}

@Composable
internal fun AboutContent() {
    AboutHero()
    AboutFacts()

    YomuSettingGroup(
        title = "About the app",
        subtitle = "A native EPUB reader built for calm, long-form reading.",
    ) {
        Text(
            text = "Open Reader is a native Android EPUB reader focused on a calm, " +
                "reader-first experience with deep typography and theme control.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    YomuSettingGroup(
        title = "Privacy",
        subtitle = "What stays on your device, and when Open Reader uses the network.",
    ) {
        PolicyBlock(
            title = "Effective 14 July 2026",
            body = "Open Reader is developed by IT Expert 120. Privacy questions can be sent " +
                "to itexpert120@outlook.com.",
        )
        PolicyBlock(
            title = "Data kept on your device",
            body = "Imported EPUB files, covers, book metadata, reading positions, chapter " +
                "progress, bookmarks, highlights, reading statistics, installed fonts, and " +
                "settings are stored in Open Reader’s private app storage. Open Reader has no accounts, " +
                "advertising, analytics, tracking SDKs, or developer-operated servers. Your " +
                "books and reading history are not uploaded.",
        )
        PolicyBlock(
            title = "Optional network features",
            body = "When you choose Dictionary “Look up,” Open Reader sends only the selected word " +
                "and language code over HTTPS to freedictionaryapi.com. When you install a " +
                "Google Font, Open Reader sends the requested font-family name to " +
                "fonts.googleapis.com and downloads font files from Google-hosted servers. " +
                "Those services also receive ordinary connection information such as your IP " +
                "address and user agent. Open Reader does not control their server logs or retention. " +
                "When you choose “Search web,” Open Reader opens a Google search in your external " +
                "browser; the browser and Google then handle that request under their own " +
                "privacy terms. Pronunciation uses your device’s text-to-speech service.",
        )
        PolicyBlock(
            title = "Security, retention, and deletion",
            body = "Network requests initiated by Open Reader use HTTPS. Local app data is excluded " +
                "from Android cloud backup and device transfer. Removing a book deletes its " +
                "app-private EPUB, cover, progress, bookmarks, and highlights. Other local data " +
                "remains until you clear Open Reader’s storage or uninstall the app. Open Reader cannot " +
                "delete logs independently retained by external dictionary, font, browser, or " +
                "text-to-speech providers.",
        )
        Text(
            text = "Open Reader is not specifically directed to children and does not supply books " +
                "or other reading content. You control the EPUB files you import. Material " +
                "changes to this policy will be reflected here with a new effective date.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    YomuSettingGroup(
        title = "Terms of use",
        subtitle = "The books you bring in remain your responsibility.",
    ) {
        Text(
            text = "Open Reader is provided “as is”, without warranty of any kind, to the fullest " +
                "extent permitted by law. You are responsible for the books you import and " +
                "for complying with their licenses and applicable copyright law. Open Reader does " +
                "not provide, sell, or distribute any books.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    YomuSettingGroup(
        title = "Acknowledgements",
        subtitle = "Open Reader stands on the work of a generous open-source ecosystem.",
    ) {
        Text(
            text = "EPUB parsing and rendering by the Readium Kotlin toolkit (BSD-3-Clause). " +
                "Cover loading by Coil. Built with Jetpack Compose. Bundled reading fonts " +
                "are used under the SIL Open Font License. Dictionary definitions from " +
                "freedictionaryapi.com, sourced from Wiktionary (CC BY-SA).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    Text(
        text = "© 2026 IT Expert 120. Open Reader is an independent application.",
        color = YomuTheme.colors.textMuted,
        style = YomuTheme.type.caption,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun AboutHero() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The mark sits on a scalloped shape — the one decorative flourish on this page.
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFF050505), MaterialTheme.shapes.large),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.open_reader_logo),
                        contentDescription = "Open Reader logo",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Open Reader",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Text(
                    text = "Private · offline · EPUB reader",
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutFacts() {
    YomuSettingList {
        AboutFact(Icons.Rounded.WifiOff, "Offline first", YomuSettingPosition.First)
        AboutFact(Icons.Rounded.Lock, "Private by default", YomuSettingPosition.Middle)
        AboutFact(Icons.AutoMirrored.Rounded.MenuBook, "EPUB focused", YomuSettingPosition.Last)
    }
}

@Composable
private fun AboutFact(icon: ImageVector, title: String, position: YomuSettingPosition) {
    YomuSettingRow(
        title = title,
        position = position,
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailing = {},
    )
}

@Composable
private fun PolicyBlock(title: String, body: String) {
    YomuSettingRow(
        title = title,
        subtitle = body,
        position = YomuSettingPosition.Single,
        trailing = {},
    )
}
