package com.itexpert120.yomu.feature.bookedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EditBookRoute(onBack: () -> Unit) {
    val viewModel: EditBookViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.onCoverPicked(uri, System.currentTimeMillis())
    }

    // Navigate back once the save completes.
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    EditBookScreen(
        state = state,
        onTitleChange = viewModel::onTitleChange,
        onSubtitleChange = viewModel::onSubtitleChange,
        onAuthorChange = viewModel::onAuthorChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onChangeCover = { coverPicker.launch("image/*") },
        onSave = viewModel::save,
        onBack = onBack,
    )
}
