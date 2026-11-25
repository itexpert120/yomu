package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.CURATED_GOOGLE_FONTS
import com.itexpert120.yomu.core.model.CuratedFont
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.feature.settings.SettingsCaptionStyle
import com.itexpert120.yomu.feature.settings.SettingsSectionLabel
import com.itexpert120.yomu.feature.settings.SettingsSubpage
import java.io.File

// Cap search results so filtering/rendering the ~1900-family catalog stays snappy.
private const val MAX_FONT_RESULTS = 40

/** [onBack] null embeds the library without its back row (the tablet settings detail pane). */
@Composable
fun FontLibraryRoute(onBack: (() -> Unit)?) {
    val viewModel: FontLibraryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    FontLibraryScreen(
        state = state,
        onBack = onBack,
        onInstall = viewModel::onInstallFont,
        onRemove = viewModel::onRemoveFont,
    )
}

/** Full screen for choosing optional reading fonts from Google Fonts. */
@Composable
fun FontLibraryScreen(
    state: FontLibraryUiState,
    onBack: (() -> Unit)?,
    onInstall: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    // Family → installed regular face, so an installed font can preview itself in its row.
    val installedFonts = remember(state.installedFonts) {
        state.installedFonts.associate { it.family to it.regularPath }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    val results = remember(trimmed, state.catalog) {
        if (trimmed.isEmpty()) {
            CURATED_GOOGLE_FONTS
        } else {
            state.catalog.asSequence()
                .filter { it.family.contains(trimmed, ignoreCase = true) }
                // Prefix matches first, then the rest alphabetically.
                .sortedWith(
                    compareByDescending<CuratedFont> { it.family.startsWith(trimmed, true) }
                        .thenBy { it.family },
                )
                .take(MAX_FONT_RESULTS)
                .toList()
        }
    }
    val stack = YomuTheme.stack

    SettingsSubpage(
        title = if (onBack != null) "Reading fonts" else null,
        onBack = onBack,
        contentPadding = if (onBack != null) 24.dp else 32.dp,
    ) {
        if (onBack == null) {
            Text(
                text = "Reading fonts",
                style = StackType.Title,
                color = stack.ink,
                modifier = Modifier.padding(top = 28.dp, bottom = 6.dp),
            )
        }
        YomuTextField(
            value = query,
            onValueChange = { query = it },
            label = "Search Google Fonts",
            imeAction = ImeAction.Search,
            capitalization = KeyboardCapitalization.Words,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (state.error != null) {
            Text(
                text = state.error,
                style = SettingsCaptionStyle,
                color = stack.danger,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (results.isEmpty()) {
            YomuEmptyState(
                icon = StackIcons.MatchCase,
                title = "No fonts match “$trimmed”",
                body = "Check the spelling, or try a shorter name.",
                modifier = Modifier.padding(top = 48.dp),
            ) {
                YomuButton(text = "Clear search", onClick = { query = "" }, emphasis = YomuButtonEmphasis.Secondary)
            }
            return@SettingsSubpage
        }
        SettingsSectionLabel(
            text = if (trimmed.isEmpty()) "Suggested for reading" else "Results",
            top = 22.dp,
        )
        results.forEachIndexed { index, font ->
            FontLibraryRow(
                family = font.family,
                category = font.category.ifBlank { "Font" },
                installedPath = installedFonts[font.family],
                downloading = font.family in state.downloadingFonts,
                divider = index < results.lastIndex,
                onInstall = { onInstall(font.family) },
                onRemove = { onRemove(font.family) },
            )
        }
        Text(
            text = "Fonts are fetched from Google Fonts once, then stay on this device.",
            style = SettingsCaptionStyle,
            color = stack.muted,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun FontLibraryRow(
    family: String,
    category: String,
    // The installed regular face's file, or null while the family isn't installed.
    installedPath: String?,
    downloading: Boolean,
    divider: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    val stack = YomuTheme.stack
    val ruleColor = stack.rule
    val installed = installedPath != null
    val canInstall = !installed && !downloading
    // Installed families preview their own face; the rest would need a download first.
    val previewFamily = remember(installedPath) {
        installedPath?.let { path -> runCatching { FontFamily(Font(file = File(path))) }.getOrNull() }
    }
    val status = when {
        downloading -> "downloading"
        installed -> "installed"
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            // The whole row installs while the font isn't present; once installed only remove acts.
            .then(if (canInstall) Modifier.yomuPressable(onClick = onInstall) else Modifier)
            .then(
                if (divider) {
                    Modifier.drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(stack.raised, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aa",
                color = if (installed) stack.ink else stack.muted,
                style = StackType.Body.copy(fontSize = 22.sp, lineHeight = 26.sp, fontFamily = previewFamily ?: StackType.Body.fontFamily),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = family,
                color = stack.ink,
                style = StackType.Body.copy(
                    fontWeight = if (previewFamily != null) FontWeight.Normal else FontWeight.SemiBold,
                    fontFamily = previewFamily ?: StackType.Body.fontFamily,
                    fontSize = if (previewFamily != null) 16.sp else 15.sp,
                ),
            )
            Text(
                text = listOfNotNull(category, status).joinToString(" · "),
                style = SettingsCaptionStyle,
                color = stack.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (downloading) {
                LinearProgressIndicator(
                    color = stack.ink,
                    trackColor = stack.rule,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }
        when {
            downloading -> Spacer(Modifier.width(48.dp))

            installed -> {
                Icon(StackIcons.Check, contentDescription = "Installed", tint = stack.ink)
                IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                    Icon(StackIcons.Delete, contentDescription = "Remove $family", tint = stack.muted)
                }
            }

            else -> IconButton(onClick = onInstall, shapes = IconButtonDefaults.shapes()) {
                Icon(StackIcons.Download, contentDescription = "Download $family", tint = stack.ink)
            }
        }
    }
}

@Composable
private fun FontLibraryPreview(mode: YomuThemeMode) {
    YomuDesignTheme(themeMode = mode) {
        FontLibraryScreen(
            state = FontLibraryUiState(
                installedFonts = listOf(CustomFontRef(family = CURATED_GOOGLE_FONTS.first().family, regularPath = "")),
                downloadingFonts = setOf(CURATED_GOOGLE_FONTS[1].family),
            ),
            onBack = {},
            onInstall = {},
            onRemove = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FontLibraryLightPreview() = FontLibraryPreview(YomuThemeMode.Light)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun FontLibraryDarkPreview() = FontLibraryPreview(YomuThemeMode.Dark)
