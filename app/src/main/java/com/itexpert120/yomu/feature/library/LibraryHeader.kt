@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuCircleIconButton
import com.itexpert120.yomu.core.designsystem.YomuIconButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuPillFilter
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.core.model.ThemePreference

/** Native Material 3 top app bar with actions that remain reachable on tablet and phone. */
@Composable
internal fun LibraryTopBar(
    bookCount: Int,
    themePreference: ThemePreference,
    onImport: () -> Unit,
    onThemeToggle: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    elevated: Boolean,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "$bookCount books",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YomuCircleIconButton(
                    onClick = onImport,
                    icon = Icons.Rounded.Add,
                    contentDescription = "Import EPUB",
                    emphasis = YomuIconButtonEmphasis.Primary,
                )
                YomuCircleIconButton(
                    onClick = onThemeToggle,
                    icon = themePreference.themeIcon(),
                    contentDescription = "Toggle theme",
                )
                YomuCircleIconButton(
                    onClick = onOpenStats,
                    icon = Icons.Rounded.Insights,
                    contentDescription = "Statistics",
                )
                YomuCircleIconButton(
                    onClick = onOpenSettings,
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (elevated) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.background
            },
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/** Search and the consolidated library arrangement control; scrolls away with the list. */
@Composable
internal fun LibrarySearchAndFilters(
    searchActive: Boolean,
    searchQuery: String,
    sortMode: SortMode,
    groupMode: GroupMode,
    viewMode: LibraryViewMode,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOptionsSheetToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (searchActive) {
            SearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClose = onSearchToggle,
            )
        } else {
            SearchHint(onClick = onSearchToggle)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val summary = listOfNotNull(
                sortMode.label,
                groupMode.takeUnless { it == GroupMode.None }?.label,
                viewMode.label,
            ).joinToString(" · ")
            YomuPillFilter(
                label = "Arrange",
                value = summary,
                onClick = onOptionsSheetToggle,
            )
        }
    }
}

private fun ThemePreference.themeIcon(): ImageVector = when (this) {
    ThemePreference.System -> Icons.Rounded.BrightnessAuto
    ThemePreference.Light -> Icons.Rounded.LightMode
    ThemePreference.Dark -> Icons.Rounded.DarkMode
}

@Composable
private fun SearchHint(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Search books, authors",
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        placeholder = { Text("Search books, authors") },
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    if (query.isNotEmpty()) {
                        onQueryChange("")
                    } else {
                        keyboardController?.hide()
                        onClose()
                    }
                },
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close search")
            }
        },
    )
}
