package com.itexpert120.yomu.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun ReaderRoute(onBack: () -> Unit) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val session by viewModel.session.collectAsState()

    ReaderScreen(
        state = state,
        session = session,
        onBack = onBack,
        onOpenSheet = viewModel::onOpenSheet,
        onCloseSheet = viewModel::onCloseSheet,
        onSeek = viewModel::onSeek,
        onNextChapter = viewModel::onNextChapter,
        onPreviousChapter = viewModel::onPreviousChapter,
        onScrollToTop = viewModel::onScrollToTop,
        onScrollToBottom = viewModel::onScrollToBottom,
        onUpdateSettings = viewModel::onUpdateSettings,
        onResetSettings = viewModel::onResetBookSettings,
        onOpenCustomTheme = viewModel::onOpenCustomTheme,
        onCloseCustomTheme = viewModel::onCloseCustomTheme,
        onApplyCustomTheme = viewModel::onApplyCustomTheme,
        onSaveCustomTheme = viewModel::onSaveCustomTheme,
        onDeleteCustomTheme = viewModel::onDeleteCustomTheme,
        onOpenToc = viewModel::onOpenToc,
        onCloseToc = viewModel::onCloseToc,
        onJumpToLocator = viewModel::onJumpToLocator,
        onCloseLookup = viewModel::onCloseLookup,
        onCloseFootnote = viewModel::onCloseFootnote,
        onReadingResumed = viewModel::onReadingResumed,
        onReadingPaused = viewModel::onReadingPaused,
    )
}
