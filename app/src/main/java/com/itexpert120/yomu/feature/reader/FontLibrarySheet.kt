package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.CURATED_GOOGLE_FONTS
import com.itexpert120.yomu.core.model.CuratedFont
import com.itexpert120.yomu.core.model.CustomFontRef

// Cap search results so filtering/rendering the ~1900-family catalog stays snappy.
private const val MAX_FONT_RESULTS = 40

/**
 * Bottom sheet for adding reading fonts from Google Fonts. Shows a curated shortlist by default and a
 * search box over the full bundled catalog; tapping a not-yet-installed font downloads it, installed
 * fonts show a remove action. Downloads run in the ViewModel.
 */
@Composable
internal fun FontLibrarySheet(
    visible: Boolean,
    installed: List<CustomFontRef>,
    downloading: Set<String>,
    error: String?,
    catalog: List<CuratedFont>,
    onDismiss: () -> Unit,
    onInstall: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (!visible) return
    val installedFamilies = installed.map { it.family }.toSet()
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val results = remember(trimmed, catalog) {
        if (trimmed.isEmpty()) {
            CURATED_GOOGLE_FONTS
        } else {
            catalog.asSequence()
                .filter { it.family.contains(trimmed, ignoreCase = true) }
                // Prefix matches first, then the rest alphabetically.
                .sortedWith(compareByDescending<CuratedFont> { it.family.startsWith(trimmed, true) }.thenBy { it.family })
                .take(MAX_FONT_RESULTS)
                .toList()
        }
    }
    YomuBottomSheet(visible = true, onDismiss = onDismiss) { _ ->
        Text(text = "Add fonts", color = YomuTheme.colors.textPrimary, style = YomuTheme.type.section)
        Text(
            text = "Search Google Fonts, or pick a suggested reading font.",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        YomuTextField(
            value = query,
            onValueChange = { query = it },
            label = "Search fonts",
            placeholder = "e.g. Merriweather",
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                text = error,
                color = YomuTheme.colors.danger,
                style = YomuTheme.type.caption,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        if (trimmed.isEmpty()) {
            Text(
                text = "Suggested",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (results.isEmpty()) {
            Text(
                text = "No fonts match “$trimmed”.",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.body,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        results.forEach { font ->
            FontLibraryRow(
                family = font.family,
                subtitle = font.category.ifBlank { "Font" },
                installed = font.family in installedFamilies,
                downloading = font.family in downloading,
                onInstall = { onInstall(font.family) },
                onRemove = { onRemove(font.family) },
            )
        }
    }
}

@Composable
private fun FontLibraryRow(
    family: String,
    subtitle: String,
    installed: Boolean,
    downloading: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            // The whole row installs when the font isn't present yet; once installed, only the
            // trailing remove button is interactive.
            .then(if (!installed && !downloading) Modifier.yomuPressable(onClick = onInstall) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = family, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.body)
            Text(text = subtitle, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        }
        when {
            downloading -> CircularProgressIndicator(
                color = YomuTheme.colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )

            installed -> {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Installed",
                    tint = YomuTheme.colors.accent,
                    modifier = Modifier.size(20.dp),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(YomuTheme.radius.pill))
                        .yomuPressable(onClick = onRemove)
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove $family",
                        tint = YomuTheme.colors.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            else -> Icon(
                imageVector = Icons.Rounded.FileDownload,
                contentDescription = "Download $family",
                tint = YomuTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
