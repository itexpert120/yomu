package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

// The ViewModel resolves its bookId from the type-safe route via SavedStateHandle.
@Composable
fun BookDetailsRoute(
    onBack: () -> Unit,
    onRead: () -> Unit,
    onEdit: () -> Unit,
) {
    val viewModel: BookDetailsViewModel = hiltViewModel()
    val book by viewModel.state.collectAsState()

    BookDetailsScreen(
        book = book,
        onBack = onBack,
        onRead = onRead,
        onEdit = onEdit,
        onMarkRead = viewModel::markRead,
        onMarkUnread = viewModel::markUnread,
        onRemove = {
            viewModel.remove()
            onBack()
        },
    )
}
