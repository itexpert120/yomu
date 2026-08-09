package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest

// The ViewModel resolves its bookId from the type-safe route via SavedStateHandle.
@Composable
fun BookDetailsRoute(
    onBack: () -> Unit,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onOpenChapter: (String) -> Unit,
) {
    val viewModel: BookDetailsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val toc by viewModel.toc.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface one-shot messages (e.g. gallery-save result) as Material snackbars.
    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Box {
        BookDetailsScreen(
            book = state.book,
            bookLoaded = !state.loading,
            toc = toc,
            onBack = onBack,
            onRead = onRead,
            onEdit = onEdit,
            onRemove = {
                viewModel.remove()
                onBack()
            },
            onSaveCover = viewModel::saveCoverToGallery,
            onTocSortChange = viewModel::onTocSortChange,
            onOpenChapter = onOpenChapter,
            onSetChapterRead = viewModel::onSetChapterRead,
            onEnterChapterSelection = viewModel::onEnterChapterSelection,
            onToggleChapterSelection = viewModel::onToggleChapterSelection,
            onToggleChapterBookmark = viewModel::onToggleChapterBookmark,
            onExitChapterSelection = viewModel::onExitChapterSelection,
            onSelectAllChapters = viewModel::onSelectAllChapters,
            onDeselectAllChapters = viewModel::onDeselectAllChapters,
            onMarkSelectedChapters = viewModel::onMarkSelectedChapters,
            onMarkPreviousRead = viewModel::onMarkPreviousRead,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 568.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}
