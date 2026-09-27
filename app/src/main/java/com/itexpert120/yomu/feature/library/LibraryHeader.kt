@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Search stays in place so entering a query never replaces the collection toolbar. */
@Composable
internal fun LibraryTopBar(
    searchActive: Boolean,
    searchQuery: String,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onOptionsSheetToggle: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    showImport: Boolean,
    resultCount: Int,
    bookCount: Int,
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searchActive) {
        if (!searchActive) focusManager.clearFocus()
    }
    // The app bar and the search row share one surface, so the whole header tints together as the
    // library scrolls under it (tracking the same collapse fraction the app bar uses).
    val headerTint = FastOutLinearInEasing.transform(scrollBehavior.state.collapsedFraction)
    val headerColor = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceContainer,
        headerTint,
    )
    // Keep the field distinct from whichever surface is behind it.
    val fieldColor = lerp(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainerHighest,
        headerTint,
    )
    Column(Modifier.background(headerColor)) {
        LargeFlexibleTopAppBar(
            title = { Text("Library") },
            subtitle = if (bookCount > 0) {
                { Text(if (bookCount == 1) "1 book" else "$bookCount books") }
            } else {
                null
            },
            scrollBehavior = scrollBehavior,
            actions = {
                if (showImport) {
                    TooltipIconButton(
                        label = "Import book",
                        icon = Icons.Rounded.Add,
                        emphasized = true,
                        onClick = onImport,
                    )
                }
                TooltipIconButton(
                    label = "Arrange library",
                    icon = Icons.Rounded.Tune,
                    onClick = onOptionsSheetToggle,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
        if (showImport || searchActive) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        if (it.isFocused && !searchActive) onSearchToggle()
                    },
                    placeholder = { Text("Search your library") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search library") },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQueryChange("") }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = fieldColor,
                        unfocusedContainerColor = fieldColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                )
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "$resultCount ${if (resultCount == 1) "book" else "books"} found",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * App-bar icon action with a long-press tooltip. [emphasized] gives the page's one primary action
 * a tonal container, per the M3 Expressive app bar guidance.
 */
@Composable
private fun TooltipIconButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        if (emphasized) {
            FilledTonalIconButton(
                onClick = onClick,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(
                    IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide),
                ),
            ) {
                Icon(icon, contentDescription = label)
            }
        } else {
            IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
                Icon(icon, contentDescription = label)
            }
        }
    }
}
