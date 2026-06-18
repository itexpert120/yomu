package com.itexpert120.yomu.feature.library

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.itexpert120.yomu.app.rememberYomuGraph
import com.itexpert120.yomu.core.model.ThemePreference

/**
 * Stateful entry point for the library: binds [LibraryViewModel], owns the SAF import launcher,
 * and forwards navigation intents up. The screen itself ([LibraryScreen]) stays stateless.
 */
@Composable
fun LibraryRoute(
    themePreference: ThemePreference,
    onOpenBook: (String) -> Unit,
    onResume: (String) -> Unit,
    onThemeToggle: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val graph = rememberYomuGraph()
    val viewModel: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LibraryViewModel(graph.bookRepository, graph.libraryPrefsRepository) }
        },
    )
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    LibraryScreen(
        state = state,
        themePreference = themePreference,
        onSearchToggle = viewModel::onSearchToggle,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSortModeChange = viewModel::onSortModeChange,
        onGroupModeChange = viewModel::onGroupModeChange,
        onViewModeChange = viewModel::onViewModeChange,
        onGridColumnsChange = viewModel::onGridColumnsChange,
        onMarkRead = viewModel::onMarkRead,
        onRemove = viewModel::onRemove,
        onBookClick = onOpenBook,
        onResume = onResume,
        onThemeToggle = onThemeToggle,
        onOpenSettings = onOpenSettings,
        onImport = {
            safLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/epub+zip"
                },
            )
        },
    )
}
