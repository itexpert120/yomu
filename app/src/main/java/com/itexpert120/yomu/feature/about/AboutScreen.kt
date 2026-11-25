package com.itexpert120.yomu.feature.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.BuildConfig
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.feature.settings.SettingsCaptionStyle
import com.itexpert120.yomu.feature.settings.SettingsRow
import com.itexpert120.yomu.feature.settings.SettingsSubpage
import com.itexpert120.yomu.feature.settings.SettingsTopRow

/** About's own pages, opened from its list and closed with back. */
internal enum class AboutPage { Home, Privacy, Terms, Acknowledgements, Diagnostics }

@Composable
fun AboutRoute(onBack: () -> Unit) {
    AboutPane(onBack = onBack)
}

/**
 * About Open Reader: the name, version and the pages behind it (Privacy, Terms, Acknowledgements,
 * Diagnostics) plus Send feedback. Its pages are pushed in place; back returns to the list first.
 * [onBack] null hides the back button on the list (the tablet settings detail pane).
 */
@Composable
internal fun AboutPane(onBack: (() -> Unit)?, diagnostics: @Composable (onBack: () -> Unit) -> Unit = { DiagnosticsPage(onBack = it) }) {
    var pageName by rememberSaveable { mutableStateOf(AboutPage.Home.name) }
    val page = AboutPage.valueOf(pageName)
    val context = LocalContext.current
    BackHandler(enabled = page != AboutPage.Home) { pageName = AboutPage.Home.name }
    AnimatedContent(
        targetState = page,
        transitionSpec = { yomuContentSwap(forward = targetState != AboutPage.Home) },
        label = "aboutPage",
    ) { current ->
        val home = { pageName = AboutPage.Home.name }
        when (current) {
            AboutPage.Home -> AboutScreen(
                onBack = onBack,
                onOpen = { pageName = it.name },
                onSendFeedback = { sendFeedback(context) },
            )
            AboutPage.Privacy -> PrivacyPage(onBack = home)
            AboutPage.Terms -> ProsePage(title = "Terms", onBack = home, sections = TermsSections)
            AboutPage.Acknowledgements -> ProsePage(title = "Acknowledgements", onBack = home, sections = AcknowledgementSections)
            AboutPage.Diagnostics -> diagnostics(home)
        }
    }
}

@Composable
internal fun AboutScreen(
    onBack: (() -> Unit)?,
    onOpen: (AboutPage) -> Unit,
    onSendFeedback: () -> Unit,
) {
    val stack = YomuTheme.stack
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(stack.sheet)
            .verticalScroll(rememberScrollState()),
    ) {
        // The hero lies on the desk; the list is a sheet over it that runs to the bottom edge.
        Column(
            Modifier
                .fillMaxWidth()
                .background(stack.desk)
                .statusBarsPadding(),
        ) {
            if (onBack != null) SettingsTopRow(title = null, onBack = onBack) else Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 32.dp)) {
                Text(
                    text = "Open Reader",
                    style = MaterialTheme.typography.displayMedium,
                    color = stack.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "A quiet place to read the books you own.",
                    style = StackType.Body,
                    color = stack.body,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .heightIn(min = 28.dp)
                        .background(stack.sheet, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                        style = SettingsCaptionStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = stack.body,
                    )
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(stack.desk)
                .background(stack.sheet, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(horizontal = 24.dp)
                .padding(top = 6.dp)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
                .padding(bottom = 24.dp),
        ) {
            Column(Modifier.widthIn(max = YomuReadableMaxWidth)) {
                SettingsRow(
                    title = "Privacy",
                    subtitle = "What stays on your phone, and what doesn't",
                    icon = StackIcons.Lock,
                    chevron = true,
                    onClick = { onOpen(AboutPage.Privacy) },
                )
                SettingsRow(title = "Terms", icon = StackIcons.Gavel, chevron = true, onClick = { onOpen(AboutPage.Terms) })
                SettingsRow(
                    title = "Acknowledgements",
                    subtitle = "Readium, fonts and open-source libraries",
                    icon = StackIcons.Favorite,
                    chevron = true,
                    onClick = { onOpen(AboutPage.Acknowledgements) },
                )
                SettingsRow(
                    title = "Diagnostics",
                    subtitle = "Share a crash log when something breaks",
                    icon = StackIcons.BugReport,
                    chevron = true,
                    onClick = { onOpen(AboutPage.Diagnostics) },
                )
                SettingsRow(
                    title = "Send feedback",
                    icon = StackIcons.ChatBubble,
                    chevron = true,
                    divider = false,
                    onClick = onSendFeedback,
                )
                Text(
                    text = "© 2026 IT Expert 120. Open Reader is an independent application.",
                    style = StackType.Caption,
                    color = stack.faint,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }
    }
}

/** A titled block of read-only prose, shown as a card on the desk. */
private data class ProseSection(val heading: String?, val body: String)

@Composable
private fun PrivacyPage(onBack: () -> Unit) {
    ProsePage(title = "Privacy", onBack = onBack, sections = PrivacySections, note = "Effective 15 November 2026")
}

@Composable
private fun ProsePage(
    title: String,
    onBack: () -> Unit,
    sections: List<ProseSection>,
    note: String? = null,
) {
    val stack = YomuTheme.stack
    SettingsSubpage(title = title, onBack = onBack, background = stack.desk, contentPadding = 16.dp) {
        if (note != null) {
            Text(
                text = note,
                style = SettingsCaptionStyle,
                color = stack.muted,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
            )
        }
        Column(
            modifier = Modifier.padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sections.forEach { section -> ProseCard(section) }
        }
    }
}

/** Read-only prose on a sheet card — deliberately not a row, so it never looks tappable. */
@Composable
private fun ProseCard(section: ProseSection) {
    val stack = YomuTheme.stack
    Column(
        Modifier
            .fillMaxWidth()
            .background(stack.sheet, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (section.heading != null) {
            Text(
                text = section.heading,
                style = StackType.Heading,
                color = stack.ink,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .semantics { heading() },
            )
        }
        Text(
            text = section.body,
            style = StackType.BodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = stack.body,
        )
    }
}

/** Opens the user's mail app with a feedback message to the developer. */
internal fun sendFeedback(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FeedbackAddress"))
        .putExtra(Intent.EXTRA_SUBJECT, "Open Reader feedback (${BuildConfig.VERSION_NAME})")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No mail app: nothing to hand the message to.
    }
}

private const val FeedbackAddress = "itexpert120@outlook.com"

private val PrivacySections = listOf(
    ProseSection(
        "Who we are",
        "Open Reader is developed by IT Expert 120. Privacy questions can be sent to itexpert120@outlook.com.",
    ),
    ProseSection(
        "Offline by default",
        "Imported EPUB files, covers, book metadata, reading positions, chapter " +
            "progress, bookmarks, highlights, reading statistics, installed fonts, and " +
            "settings are stored in Open Reader’s private app storage. Open Reader has no accounts, " +
            "advertising, analytics, or tracking SDKs. Unless you turn on sync or use one " +
            "of the network features below, nothing leaves your device.",
    ),
    ProseSection(
        "Optional sync",
        "Sync is off until you turn it on, and it needs no account. A 12-word phrase " +
            "created on your device is the only credential. When sync is on, your reading " +
            "positions, chapter progress, bookmarks, highlights, reading sessions, edited " +
            "book details, saved themes, and settings are encrypted on your device before " +
            "they are uploaded. The sync server, hosted on Convex, stores only ciphertext: " +
            "it cannot read your data or see which books you have. Book files and covers " +
            "are never uploaded. Like any online service, the server receives ordinary " +
            "connection information such as your IP address. Anyone who has your 12 words " +
            "can read your synced data, and the words cannot be recovered if you lose them.",
    ),
    ProseSection(
        "Other network features",
        "When you choose Dictionary “Look up,” Open Reader sends only the selected word " +
            "and language code over HTTPS to freedictionaryapi.com. When you install a " +
            "Google Font, Open Reader sends the requested font-family name to " +
            "fonts.googleapis.com and downloads font files from Google-hosted servers. " +
            "Those services also receive ordinary connection information such as your IP " +
            "address and user agent. Open Reader does not control their server logs or retention. " +
            "When you choose “Search web,” Open Reader opens a Google search in your external " +
            "browser; the browser and Google then handle that request under their own " +
            "privacy terms. Pronunciation uses your device’s text-to-speech service.",
    ),
    ProseSection(
        "Crash log",
        "Crashes and unexpected exits are logged on this device only. The log leaves the " +
            "phone only if you share it yourself from Diagnostics.",
    ),
    ProseSection(
        "Security, retention, and deletion",
        "Network requests initiated by Open Reader use encrypted connections. Local app data " +
            "is excluded from Android cloud backup and device transfer. Removing a book " +
            "deletes its app-private EPUB, cover, progress, bookmarks, and highlights from " +
            "this device. “Delete synced data” in Settings removes the encrypted copy from " +
            "the sync server and stops sync on every device; turning sync off on one " +
            "device leaves the synced copy in place for your other devices. Other local " +
            "data remains until you clear Open Reader’s storage or uninstall the app. Open Reader cannot " +
            "delete logs independently retained by external dictionary, font, browser, " +
            "hosting, or text-to-speech providers.",
    ),
    ProseSection(
        "Children and changes",
        "Open Reader is not specifically directed to children and does not supply books " +
            "or other reading content. You control the EPUB files you import. Material " +
            "changes to this policy will be reflected here with a new effective date.",
    ),
)

private val TermsSections = listOf(
    ProseSection(
        "Terms of use",
        "Open Reader is provided “as is”, without warranty of any kind, to the fullest " +
            "extent permitted by law. You are responsible for the books you import and " +
            "for complying with their licenses and applicable copyright law. Open Reader does " +
            "not provide, sell, or distribute any books.",
    ),
)

private val AcknowledgementSections = listOf(
    ProseSection(
        null,
        "EPUB parsing and rendering by the Readium Kotlin toolkit (BSD-3-Clause). " +
            "Cover loading by Coil. Built with Jetpack Compose. Sync runs on Convex. " +
            "Bundled reading fonts are used under the SIL Open Font License. Dictionary " +
            "definitions from freedictionaryapi.com, sourced from Wiktionary (CC BY-SA).",
    ),
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AboutPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Light) {
        AboutScreen(onBack = {}, onOpen = {}, onSendFeedback = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AboutDarkPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
        AboutScreen(onBack = {}, onOpen = {}, onSendFeedback = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PrivacyPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Light) { PrivacyPage(onBack = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PrivacyDarkPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Oled) { PrivacyPage(onBack = {}) }
}
