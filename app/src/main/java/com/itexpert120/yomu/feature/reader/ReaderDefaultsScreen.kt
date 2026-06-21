package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuTheme

@Composable
fun ReaderDefaultsRoute(onBack: () -> Unit) {
    val viewModel: ReaderDefaultsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    YomuScreenScaffold(title = "Reading defaults", onBack = onBack) {
        Text(
            text = "Applied to every book unless you override settings for a book from its reader.",
            color = YomuTheme.colors.textSecondary,
            style = YomuTheme.type.body,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ReaderPreferenceControls(
                settings = state.settings,
                customThemes = state.customThemes,
                onUpdateSettings = viewModel::onUpdate,
                onOpenCustomTheme = viewModel::onOpenCustomTheme,
                onApplyCustomTheme = viewModel::onApplyCustomTheme,
            )
        }
    }

    CustomThemeSheet(
        visible = state.customSheetVisible,
        settings = state.settings,
        customThemes = state.customThemes,
        onDismiss = viewModel::onCloseCustomTheme,
        onUpdateSettings = viewModel::onUpdate,
        onSave = viewModel::onSaveCustomTheme,
        onApply = viewModel::onApplyCustomTheme,
        onDelete = viewModel::onDeleteCustomTheme,
    )
}
