package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuLoadingIndicator
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuSettingPosition
import com.itexpert120.yomu.core.model.CURATED_GOOGLE_FONTS
import com.itexpert120.yomu.core.model.CuratedFont
import java.io.File

// Cap search results so filtering/rendering the ~1900-family catalog stays snappy.
private const val MAX_FONT_RESULTS = 40

@Composable
fun FontLibraryRoute(onBack: () -> Unit) {
    val viewModel: FontLibraryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
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
    onBack: () -> Unit,
    onInstall: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    // Family → installed regular face, so an installed font can preview itself in its row.
    val installedFonts = remember(state.installedFonts) {
        state.installedFonts.associate { it.family to it.regularPath }
    }
    var query by remember { mutableStateOf("") }
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

    YomuScreenScaffold(
        title = "Reading fonts",
        subtitle = "Choose the voice of your pages",
        onBack = onBack,
        showScrollEdgeShadow = false,
    ) {
        FontLibraryCatalog(
            query = query,
            onQueryChange = { query = it },
            trimmedQuery = trimmed,
            results = results,
            error = state.error,
            installedFonts = installedFonts,
            downloadingFonts = state.downloadingFonts,
            onInstall = onInstall,
            onRemove = onRemove,
        )
    }
}

@Composable
private fun FontLibraryCatalog(
    query: String,
    onQueryChange: (String) -> Unit,
    trimmedQuery: String,
    results: List<CuratedFont>,
    error: String?,
    installedFonts: Map<String, String>,
    downloadingFonts: Set<String>,
    onInstall: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
        YomuTextField(
            value = query,
            onValueChange = onQueryChange,
            label = "Search fonts",
            placeholder = "e.g. Merriweather",
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            YomuNotice(
                text = error,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.ErrorOutline,
                tone = YomuNoticeTone.Error,
            )
        }

        YomuSettingGroup(
            title = if (trimmedQuery.isEmpty()) "Suggested fonts" else "Search results",
            subtitle = if (trimmedQuery.isEmpty()) {
                "A small shortlist to get you started."
            } else {
                "Up to $MAX_FONT_RESULTS matching families."
            },
        ) {
            if (results.isEmpty()) {
                YomuEmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No fonts found",
                    body = "Nothing matches “$trimmedQuery”. Try a shorter name.",
                    compact = true,
                )
            } else {
                YomuSettingList {
                    results.forEachIndexed { index, font ->
                        FontLibraryRow(
                            family = font.family,
                            subtitle = font.category.ifBlank { "Font" },
                            position = yomuSettingPosition(index, results.size),
                            installedPath = installedFonts[font.family],
                            downloading = font.family in downloadingFonts,
                            onInstall = { onInstall(font.family) },
                            onRemove = { onRemove(font.family) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontLibraryRow(
    family: String,
    subtitle: String,
    position: YomuSettingPosition,
    // The installed regular face's file, or null while the family isn't installed.
    installedPath: String?,
    downloading: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    val installed = installedPath != null
    val canInstall = !installed && !downloading
    // Installed families preview their own face in the tile; the rest would need a download first.
    val previewFamily = remember(installedPath) {
        installedPath?.let { path -> runCatching { FontFamily(Font(file = File(path))) }.getOrNull() }
    }
    YomuSettingRow(
        title = family,
        subtitle = subtitle,
        position = position,
        // The whole row installs when the font isn't present yet; once installed, only the
        // trailing remove button is interactive.
        onClick = onInstall.takeIf { canInstall },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = YomuShapeTokens.inset,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Aa",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.titleMediumEmphasized.let { style ->
                            previewFamily?.let { style.copy(fontFamily = it) } ?: style
                        },
                    )
                }
            }
        },
    ) {
        when {
            downloading -> YomuLoadingIndicator(modifier = Modifier.size(28.dp))

            installed -> {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Installed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove $family",
                    )
                }
            }

            else -> IconButton(onClick = onInstall, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = "Download $family",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
