package com.itexpert120.yomu.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.BuildConfig
import com.itexpert120.yomu.R
import com.itexpert120.yomu.core.designsystem.YomuBadgeShape
import com.itexpert120.yomu.core.designsystem.YomuBadgeTone
import com.itexpert120.yomu.core.designsystem.YomuCard
import com.itexpert120.yomu.core.designsystem.YomuCardTone
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSectionHeader
import com.itexpert120.yomu.core.designsystem.YomuShapeBadge
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme

// The logo artwork is drawn for a near-black plate; this is that plate, not a theme colour.
private val LogoPlate = Color(0xFF050505)

@Composable
fun AboutRoute(onBack: () -> Unit) {
    AboutScreen(onBack = onBack, diagnosticsSection = { DiagnosticsSection() })
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    diagnosticsSection: @Composable () -> Unit = {},
) {
    YomuScreenScaffold(
        title = "About Open Reader",
        onBack = onBack,
        showScrollEdgeShadow = false,
    ) {
        AboutContent(diagnosticsSection = diagnosticsSection)
    }
}

@Composable
internal fun AboutContent(diagnosticsSection: @Composable () -> Unit = {}) {
    AboutHero()

    AboutSection(title = "About the app") {
        ProseCard(
            body = "Open Reader is a native Android EPUB reader built for calm, long-form reading, with " +
                "deep typography and theme control.",
        )
    }

    AboutSection(
        title = "Privacy",
        subtitle = "Effective 15 November 2026",
    ) {
        ProseCard(
            heading = "Who we are",
            body = "Open Reader is developed by IT Expert 120. Privacy questions can be sent to " +
                "itexpert120@outlook.com.",
        )
        ProseCard(
            heading = "Offline by default",
            body = "Imported EPUB files, covers, book metadata, reading positions, chapter " +
                "progress, bookmarks, highlights, reading statistics, installed fonts, and " +
                "settings are stored in Open Reader’s private app storage. Open Reader has no accounts, " +
                "advertising, analytics, or tracking SDKs. Unless you turn on sync or use one " +
                "of the network features below, nothing leaves your device.",
        )
        ProseCard(
            heading = "Optional sync",
            body = "Sync is off until you turn it on, and it needs no account. A 12-word phrase " +
                "created on your device is the only credential. When sync is on, your reading " +
                "positions, chapter progress, bookmarks, highlights, reading sessions, edited " +
                "book details, saved themes, and settings are encrypted on your device before " +
                "they are uploaded. The sync server, hosted on Convex, stores only ciphertext: " +
                "it cannot read your data or see which books you have. Book files and covers " +
                "are never uploaded. Like any online service, the server receives ordinary " +
                "connection information such as your IP address. Anyone who has your 12 words " +
                "can read your synced data, and the words cannot be recovered if you lose them.",
        )
        ProseCard(
            heading = "Other network features",
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
        ProseCard(
            heading = "Security, retention, and deletion",
            body = "Network requests initiated by Open Reader use encrypted connections. Local app data " +
                "is excluded from Android cloud backup and device transfer. Removing a book " +
                "deletes its app-private EPUB, cover, progress, bookmarks, and highlights from " +
                "this device. “Delete synced data” in Settings removes the encrypted copy from " +
                "the sync server and stops sync on every device; turning sync off on one " +
                "device leaves the synced copy in place for your other devices. Other local " +
                "data remains until you clear Open Reader’s storage or uninstall the app. Open Reader cannot " +
                "delete logs independently retained by external dictionary, font, browser, " +
                "hosting, or text-to-speech providers.",
        )
        ProseCard(
            heading = "Children and changes",
            body = "Open Reader is not specifically directed to children and does not supply books " +
                "or other reading content. You control the EPUB files you import. Material " +
                "changes to this policy will be reflected here with a new effective date.",
        )
    }

    AboutSection(title = "Terms of use") {
        ProseCard(
            body = "Open Reader is provided “as is”, without warranty of any kind, to the fullest " +
                "extent permitted by law. You are responsible for the books you import and " +
                "for complying with their licenses and applicable copyright law. Open Reader does " +
                "not provide, sell, or distribute any books.",
        )
    }

    diagnosticsSection()

    AboutSection(title = "Acknowledgements") {
        ProseCard(
            body = "EPUB parsing and rendering by the Readium Kotlin toolkit (BSD-3-Clause). " +
                "Cover loading by Coil. Built with Jetpack Compose. Sync runs on Convex. " +
                "Bundled reading fonts are used under the SIL Open Font License. Dictionary " +
                "definitions from freedictionaryapi.com, sourced from Wiktionary (CC BY-SA).",
        )
    }

    Text(
        text = "© 2026 IT Expert 120. Open Reader is an independent application.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(YomuTheme.space.xxs),
    )
}

@Composable
private fun AboutHero() {
    YomuCard(
        modifier = Modifier.fillMaxWidth(),
        tone = YomuCardTone.Hero,
        contentPadding = PaddingValues(YomuTheme.space.lg),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
        ) {
            // The mark sits on the Yomu cookie — the one decorative flourish on this page.
            YomuShapeBadge(
                icon = null,
                size = 96.dp,
                shape = YomuBadgeShape.Cookie,
                tone = YomuBadgeTone.Solid,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(LogoPlate, YomuShapeTokens.tile),
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
            ) {
                Text(
                    text = "Open Reader",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Text(
                    text = "Private · offline-first · EPUB reader",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    modifier = Modifier.padding(top = YomuTheme.space.xxs),
                    shape = YomuShapeTokens.pill,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = YomuTheme.space.sm, vertical = YomuTheme.space.xxs),
                    )
                }
            }
        }
    }
}

/** A page section: the shared header, then its prose blocks. */
@Composable
private fun AboutSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = YomuTheme.space.xs),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        YomuSectionHeader(title = title, subtitle = subtitle)
        content()
    }
}

/** Read-only prose on the group surface — deliberately not a list row, so it never looks tappable. */
@Composable
private fun ProseCard(body: String, heading: String? = null) {
    YomuCard(modifier = Modifier.fillMaxWidth(), tone = YomuCardTone.Group) {
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs)) {
            if (heading != null) {
                Text(
                    text = heading,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
