package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.model.ReaderSettings

@Composable
fun ReaderDefaultsRoute(onBack: () -> Unit, onOpenFontLibrary: () -> Unit) {
    val viewModel: ReaderDefaultsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    SaveFailureNotice(state.settingsError, viewModel::onRetrySettings)
    YomuScreenScaffold(
        title = "Reading defaults",
        subtitle = "Every new book starts here",
        onBack = onBack,
        showScrollEdgeShadow = false,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = YomuWidthClass.fromWidth(maxWidth).isWide
            val controls: @Composable () -> Unit = {
                ReaderPreferenceControls(
                    settings = state.settings,
                    customThemes = state.customThemes,
                    onUpdateSettings = viewModel::onUpdate,
                    onOpenCustomTheme = viewModel::onOpenCustomTheme,
                    onApplyCustomTheme = viewModel::onApplyCustomTheme,
                    customFonts = state.installedFonts,
                    onManageFonts = onOpenFontLibrary,
                )
            }

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ReaderDefaultsPreview(
                        settings = state.settings,
                        modifier = Modifier.weight(0.9f),
                    )
                    Column(modifier = Modifier.weight(1.5f)) {
                        controls()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ReaderDefaultsPreview(settings = state.settings)
                    Text(
                        text = "Applied to every book unless you override settings from its reader.",
                        color = YomuTheme.colors.textSecondary,
                        style = YomuTheme.type.body,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    controls()
                }
            }
        }
    }

    CustomThemeSheet(
        visible = state.customSheetVisible,
        settings = state.settings,
        customThemes = state.customThemes,
        onDismiss = viewModel::onCloseCustomTheme,
        onUpdateSettings = viewModel::onUpdate,
        onSave = viewModel::onSaveCustomTheme,
        onApply = viewModel::onApplyCustomTheme,
        onDelete = viewModel::onDeleteCustomTheme,
    )
}

@Composable
private fun ReaderDefaultsPreview(
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
) {
    val pageBackground = Color(settings.backgroundArgb)
    val pageText = Color(settings.textArgb)
    val pageMuted = Color(settings.colorPalette.secondaryTextArgb)
    val fontName = settings.customFont?.family ?: settings.font.displayName

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = pageBackground,
        contentColor = pageText,
        border = BorderStroke(1.dp, Color(settings.colorPalette.borderArgb)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(pageText, RoundedCornerShape(YomuTheme.radius.pill)),
                )
                Text(
                    text = "LIVE PREVIEW",
                    color = pageMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "A page that feels like yours.",
                    color = pageText,
                    style = YomuTheme.type.reader,
                )
                Text(
                    text = "Open Reader keeps the words in focus and lets the details recede. " +
                        "These defaults will greet every new book in your library.",
                    color = pageMuted,
                    style = YomuTheme.type.body,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PreviewPill(
                    text = settings.layout.name,
                    background = pageText.copy(alpha = 0.12f),
                    foreground = pageText,
                )
                PreviewPill(
                    text = fontName,
                    background = pageText.copy(alpha = 0.12f),
                    foreground = pageText,
                )
                PreviewPill(
                    text = "${(settings.fontScale * 100).toInt()}%",
                    background = pageText.copy(alpha = 0.12f),
                    foreground = pageText,
                )
            }
        }
    }
}

@Composable
private fun PreviewPill(
    text: String,
    background: Color,
    foreground: Color,
) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(YomuTheme.radius.pill))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
