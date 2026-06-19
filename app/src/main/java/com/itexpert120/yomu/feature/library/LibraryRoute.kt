package com.itexpert120.yomu.feature.library

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.core.model.ThemePreference

/**
 * Stateful entry point for the library: binds [LibraryViewModel], owns the SAF import launcher,
 * and forwards navigation intents up. The screen itself ([LibraryScreen]) stays stateless.
 */
@Composable
fun LibraryRoute(
    themePreference: ThemePreference,
    onOpenReader: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onThemeToggle: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // The picker returns one or many EPUBs; the use case copies them immediately, so a temporary
    // read grant is enough (no persistable permission needed).
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uris = buildList {
                data?.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri)
                }
                data?.data?.let { add(it) }
            }
            viewModel.onImport(uris)
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
        onOpenReader = onOpenReader,
        onOpenDetails = onOpenDetails,
        onThemeToggle = onThemeToggle,
        onOpenStats = onOpenStats,
        onOpenSettings = onOpenSettings,
        onEnterSelection = viewModel::onEnterSelection,
        onToggleSelect = viewModel::onToggleSelect,
        onExitSelection = viewModel::onExitSelection,
        onSelectAll = viewModel::onSelectAll,
        onDeselectAll = viewModel::onDeselectAll,
        onInvertSelection = viewModel::onInvertSelection,
        onRemoveSelected = viewModel::onRemoveSelected,
        onMarkSelectedRead = viewModel::onMarkSelectedRead,
        onMarkSelectedUnread = viewModel::onMarkSelectedUnread,
        onImport = {
            safLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/epub+zip"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                },
            )
        },
    )
}
