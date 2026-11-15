@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuScreenHeader
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTwoPane
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.model.ReaderSettings

private const val ReaderDefaultsTitle = "Reading defaults"
private const val ReaderDefaultsSubtitle = "Every new book starts here"

@Composable
fun ReaderDefaultsRoute(onBack: () -> Unit, onOpenFontLibrary: () -> Unit) {
    val viewModel: ReaderDefaultsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    SaveFailureNotice(state.settingsError, viewModel::onRetrySettings)
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Material 3 supporting-pane layout from the expanded breakpoint (the same one YomuTwoPane
        // screens split at): the preview pane stays put while the controls scroll beside it.
        // Below that the preview leads a single scrolling column.
        if (YomuWidthClass.fromWidth(maxWidth).isExpanded) {
            ReaderDefaultsTwoPane(settings = state.settings, onBack = onBack, controls = controls)
        } else {
            YomuScreenScaffold(
                title = ReaderDefaultsTitle,
                subtitle = ReaderDefaultsSubtitle,
                onBack = onBack,
                showScrollEdgeShadow = false,
            ) {
                ReaderDefaultsPreviewPane(settings = state.settings)
                controls()
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
private fun ReaderDefaultsTwoPane(
    settings: ReaderSettings,
    onBack: () -> Unit,
    controls: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    YomuAppSurface {
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            YomuScreenHeader(
                title = ReaderDefaultsTitle,
                onBack = onBack,
                subtitle = ReaderDefaultsSubtitle,
                scrollBehavior = scrollBehavior,
            )
            YomuTwoPane(
                modifier = Modifier.weight(1f),
                startContent = {
                    // Scrolls on its own only if the window is too short for the whole preview.
                    ReaderDefaultsPreviewPane(
                        settings = settings,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = YomuTheme.space.lg),
                    )
                },
                endContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = YomuReadableMaxWidth)
                            .fillMaxWidth()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .windowInsetsPadding(
                                WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom),
                            )
                            .padding(bottom = YomuTheme.space.lg),
                    ) {
                        controls()
                    }
                },
            )
        }
    }
}

/** The live page preview with its label and the caption explaining what the defaults apply to. */
@Composable
private fun ReaderDefaultsPreviewPane(
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        YomuSectionLabel("Live preview")
        ReaderDefaultsPreview(settings = settings)
        Text(
            text = "Applied to every book unless you override settings from its reader.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = YomuTheme.space.xxs),
        )
    }
}

@Composable
private fun ReaderDefaultsPreview(
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
) {
    // Reading-theme colours on purpose: this card is a miniature of the page itself.
    val pageBackground = Color(settings.backgroundArgb)
    val pageText = Color(settings.textArgb)
    val pageMuted = Color(settings.colorPalette.secondaryTextArgb)
    val pillTint = pageText.copy(alpha = PreviewPillTintAlpha)
    val fontName = settings.customFont?.family ?: settings.font.displayName

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = YomuShapeTokens.hero,
        color = pageBackground,
        contentColor = pageText,
        border = BorderStroke(1.dp, Color(settings.colorPalette.borderArgb)),
    ) {
        Column(
            modifier = Modifier.padding(YomuTheme.space.lg),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
                Text(
                    text = "A page that feels like yours.",
                    color = pageText,
                    style = YomuTheme.type.reader,
                )
                Text(
                    text = "Open Reader keeps the words in focus and lets the details recede. " +
                        "These defaults will greet every new book in your library.",
                    color = pageMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
            ) {
                PreviewPill(text = settings.layout.name, background = pillTint, foreground = pageText)
                PreviewPill(text = fontName, background = pillTint, foreground = pageText)
                PreviewPill(
                    text = "${(settings.fontScale * 100).toInt()}%",
                    background = pillTint,
                    foreground = pageText,
                )
            }
        }
    }
}

/** How strongly the preview's pills tint the page colour toward its text colour. */
private const val PreviewPillTintAlpha = 0.12f

@Composable
private fun PreviewPill(
    text: String,
    background: Color,
    foreground: Color,
) {
    Box(
        modifier = Modifier
            .background(background, YomuShapeTokens.pill)
            .padding(horizontal = YomuTheme.space.sm, vertical = YomuTheme.space.xxs),
    ) {
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
