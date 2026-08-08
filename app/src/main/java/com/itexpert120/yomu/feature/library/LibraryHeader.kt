@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

/** A small Material top bar with search and arrangement kept in the action area. */
@Composable
internal fun LibraryTopBar(
    searchActive: Boolean,
    searchQuery: String,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onOptionsSheetToggle: () -> Unit,
    elevated: Boolean,
) {
    Column {
        TopAppBar(
            title = { Text("Library") },
            actions = {
                IconButton(onClick = onImport) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Import EPUB",
                    )
                }
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        imageVector = if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (searchActive) "Close search" else "Search library",
                    )
                }
                IconButton(onClick = onOptionsSheetToggle) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Arrange library",
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

        AnimatedVisibility(visible = searchActive) {
            LibrarySearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
            )
        }
    }
}

@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.focusRequester(focusRequester),
                placeholder = { Text("Search books, authors") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                    )
                },
            )
        },
        expanded = false,
        onExpandedChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        content = {},
    )
}
