package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.itexpert120.yomu.app.rememberYomuGraph

@Composable
fun BookDetailsRoute(
    bookId: String,
    onBack: () -> Unit,
    onRead: () -> Unit,
) {
    val graph = rememberYomuGraph()
    val viewModel: BookDetailsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { BookDetailsViewModel(bookId, graph.bookRepository) }
        },
    )
    val book by viewModel.state.collectAsState()

    BookDetailsScreen(
        book = book,
        onBack = onBack,
        onRead = onRead,
        onMarkRead = viewModel::markRead,
        onRemove = {
            viewModel.remove()
            onBack()
        },
    )
}
