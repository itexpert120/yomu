package com.itexpert120.yomu.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ReaderRoute(onBack: () -> Unit) {
    val viewModel: ReaderViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // A reading session runs while this destination is resumed (foreground, on top), whatever the
    // screen below is composing; it is banked on pause and when the reader leaves the back stack.
    LifecycleResumeEffect(viewModel) {
        viewModel.onReadingResumed()
        onPauseOrDispose { viewModel.onReadingPaused() }
    }

    val onAction = remember(viewModel, onBack) {
        { action: ReaderAction ->
            if (action == ReaderAction.Back) onBack() else viewModel.onAction(action)
        }
    }
    ReaderScreen(state = state, onAction = onAction)
}
