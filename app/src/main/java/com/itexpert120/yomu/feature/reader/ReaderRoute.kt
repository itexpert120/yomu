package com.itexpert120.yomu.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun ReaderRoute(onBack: () -> Unit, onAbout: () -> Unit) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val session by viewModel.session.collectAsState()

    ReaderScreen(
        state = state,
        session = session,
        onBack = onBack,
        onCloseSheet = viewModel::onCloseSheet,
        onSeek = viewModel::onSeek,
        onNextChapter = viewModel::onNextChapter,
        onPreviousChapter = viewModel::onPreviousChapter,
        onUpdateSettings = viewModel::onUpdateSettings,
        onAbout = onAbout,
    )
}
