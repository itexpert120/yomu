package com.itexpert120.yomu.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.feature.annotations.AnnotationTransferAction
import com.itexpert120.yomu.feature.annotations.AnnotationTransferHost
import com.itexpert120.yomu.feature.annotations.rememberAnnotationImportPicker

@Composable
fun ReaderRoute(onBack: () -> Unit, onOpenFontLibrary: () -> Unit = {}) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // A reading session runs while this destination is resumed (foreground, on top), whatever the
    // screen below is composing; it is banked on pause and when the reader leaves the back stack.
    LifecycleResumeEffect(viewModel) {
        viewModel.onReadingResumed()
        onPauseOrDispose { viewModel.onReadingPaused() }
    }

    val pickAnnotationFile = rememberAnnotationImportPicker { viewModel.onAction(ReaderAction.Annotations(it)) }
    val onAction = remember(viewModel, onBack, onOpenFontLibrary, pickAnnotationFile) {
        { action: ReaderAction ->
            when {
                action == ReaderAction.Back -> onBack()
                // The Reading settings sheet stays open underneath, to pick the new font on return.
                action == ReaderAction.OpenFontLibrary -> onOpenFontLibrary()
                action is ReaderAction.Annotations && action.action == AnnotationTransferAction.PickFile -> pickAnnotationFile()
                else -> viewModel.onAction(action)
            }
        }
    }
    // The reader's sheets, dialogs and notices follow the page, not the app: a Night page gets dark
    // sheets, Paper and Sepia light ones (the chrome itself already takes the page colour).
    val settings = state.experience.settings
    val pageMode = when {
        settings.isLightBackground -> YomuThemeMode.Light
        settings.theme == ReaderThemeMode.Black -> YomuThemeMode.Oled
        else -> YomuThemeMode.Dark
    }
    YomuDesignTheme(themeMode = pageMode) {
        ReaderScreen(state = state, onAction = onAction)
        AnnotationTransferHost(
            state = state.annotationTransfer,
            onAction = { onAction(ReaderAction.Annotations(it)) },
            shares = viewModel.annotations.shares,
        )
    }
}
