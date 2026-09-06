package com.itexpert120.yomu.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun ReaderRoute(onBack: () -> Unit) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    ReaderScreen(
        state = state,
        onBack = onBack,
        onRetryOpen = viewModel::onRetryOpen,
        onOpenSheet = viewModel::onOpenSheet,
        onCloseSheet = viewModel::onCloseSheet,
        onSelectChapter = viewModel::onSelectChapter,
        onNextChapter = viewModel::onNextChapter,
        onPreviousChapter = viewModel::onPreviousChapter,
        onUpdateSettings = viewModel::onUpdateSettings,
        onResetSettings = viewModel::onResetBookSettings,
        onRetrySettings = viewModel::onRetrySettings,
        onRetryAnnotations = viewModel::onRetryAnnotations,
        onOpenCustomTheme = viewModel::onOpenCustomTheme,
        onCloseCustomTheme = viewModel::onCloseCustomTheme,
        onApplyCustomTheme = viewModel::onApplyCustomTheme,
        onSaveCustomTheme = viewModel::onSaveCustomTheme,
        onDeleteCustomTheme = viewModel::onDeleteCustomTheme,
        onOpenBrowse = viewModel::onOpenBrowse,
        onSelectBrowseTab = viewModel::onSelectBrowseTab,
        onCloseBrowse = viewModel::onCloseBrowse,
        onJumpToLocator = viewModel::onJumpToLocator,
        onJumpToBookmark = viewModel::onJumpToBookmark,
        onJumpToHighlight = viewModel::onJumpToHighlight,
        onJumpToSearchResult = viewModel::onJumpToSearchResult,
        onDeleteBookmarkById = viewModel::onDeleteBookmarkById,
        onDeleteHighlightById = viewModel::onDeleteHighlightById,
        onOpenSearch = viewModel::onOpenSearch,
        onCloseSearch = viewModel::onCloseSearch,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSubmitSearch = viewModel::onSubmitSearch,
        onCloseLookup = viewModel::onCloseLookup,
        onLookUpWord = viewModel::onLookUpWord,
        onLookupBack = viewModel::onLookupBack,
        onRetryLookup = viewModel::onRetryLookup,
        onPronounce = viewModel::onPronounce,
        onCloseFootnote = viewModel::onCloseFootnote,
        onDeleteHighlight = viewModel::onDeleteHighlight,
        onSetHighlightColor = viewModel::onSetHighlightColor,
        onCloseEditHighlight = viewModel::onCloseEditHighlight,
        onToggleBookmark = viewModel::onToggleBookmark,
        onReadingResumed = viewModel::onReadingResumed,
        onReadingPaused = viewModel::onReadingPaused,
    )
}
