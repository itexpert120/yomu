@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit

/** Collapsing library heading with discoverable collection actions. */
@Composable
internal fun LibraryTopBar(
    searchActive: Boolean,
    searchQuery: String,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onOptionsSheetToggle: () -> Unit,
    elevated: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    showImport: Boolean,
) {
    // Keep the library content anchored while the search field changes the toolbar's measured
    // height. Without this, the bar fades smoothly but the grid jumps to its new top edge.
    Column(Modifier.animateContentSize()) {
        LargeTopAppBar(
            title = { Text("Library") },
            scrollBehavior = scrollBehavior,
            actions = {
                if (searchActive || !showImport) {
                    IconButton(onClick = onSearchToggle) {
                        Icon(
                            imageVector = if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (searchActive) "Close search" else "Search library",
                        )
                    }
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

        if (!searchActive && showImport) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                YomuButton(
                    text = "Search library",
                    onClick = onSearchToggle,
                    emphasis = YomuButtonEmphasis.Secondary,
                )
                YomuButton(text = "Import book", onClick = onImport)
            }
        }
        AnimatedVisibility(
            visible = searchActive,
            enter = yomuChromeEnter(fromBottom = false),
            exit = yomuChromeExit(toBottom = false),
        ) {
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
