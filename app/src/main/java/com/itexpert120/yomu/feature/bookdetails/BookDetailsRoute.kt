package com.itexpert120.yomu.feature.bookdetails

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
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
    val book by viewModel.state.collectAsState()
    val toc by viewModel.toc.collectAsState()
    val context = LocalContext.current

    // Surface one-shot messages (e.g. gallery-save result) as a brief toast.
    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    BookDetailsScreen(
        book = book,
        toc = toc,
        onBack = onBack,
        onRead = onRead,
        onEdit = onEdit,
        onMarkRead = viewModel::markRead,
        onMarkUnread = viewModel::markUnread,
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
        onExitChapterSelection = viewModel::onExitChapterSelection,
        onSelectAllChapters = viewModel::onSelectAllChapters,
        onMarkSelectedChapters = viewModel::onMarkSelectedChapters,
        onMarkPreviousRead = viewModel::onMarkPreviousRead,
    )
}
