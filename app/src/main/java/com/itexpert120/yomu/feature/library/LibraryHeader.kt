@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuWidthClass

/**
 * Search stays in place so entering a query never replaces the collection toolbar. Selection mode
 * reuses this same header — the title, navigation icon and actions swap in place and the search
 * field fades out — so the grid below never jumps when selection starts or ends.
 */
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
    selectionMode: Boolean = false,
    selectedCount: Int = 0,
    allSelected: Boolean = false,
    onExitSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searchActive) {
        if (!searchActive) focusManager.clearFocus()
    }
    // The app bar and the search row share one surface, so the whole header tints together as the
    // library scrolls under it (tracking the same collapse fraction the app bar uses).
    val collapseTint = FastOutLinearInEasing.transform(scrollBehavior.state.collapsedFraction)
    // Selection is a contextual mode: the header takes the container tint to signal it.
    val selectionTint by animateFloatAsState(
        targetValue = if (selectionMode) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "selectionTint",
    )
    val headerTint = maxOf(collapseTint, selectionTint)
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
            title = {
                AnimatedContent(
                    targetState = selectionMode,
                    transitionSpec = { headerSwap() },
                    label = "libraryTitle",
                ) { selecting ->
                    Text(
                        if (selecting) {
                            if (selectedCount == 1) "1 selected" else "$selectedCount selected"
                        } else {
                            "Library"
                        },
                    )
                }
            },
            subtitle = if (bookCount > 0) {
                {
                    Text(
                        when {
                            selectionMode -> "of $bookCount ${if (bookCount == 1) "book" else "books"}"
                            bookCount == 1 -> "1 book"
                            else -> "$bookCount books"
                        },
                    )
                }
            } else {
                null
            },
            navigationIcon = {
                AnimatedVisibility(
                    visible = selectionMode,
                    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
                ) {
                    TooltipIconButton(
                        label = "Exit selection",
                        icon = Icons.Rounded.Close,
                        onClick = onExitSelection,
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            actions = {
                AnimatedContent(
                    targetState = selectionMode,
                    transitionSpec = { headerSwap() },
                    label = "libraryActions",
                ) { selecting ->
                    Row {
                        if (selecting) {
                            TooltipIconButton(
                                label = if (allSelected) "Deselect all" else "Select all",
                                icon = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                onClick = if (allSelected) onDeselectAll else onSelectAll,
                            )
                        } else {
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
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
        if (showImport || searchActive) {
            val margin = YomuWidthClass.fromWidth(
                with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() },
            ).margin
            // The field keeps its space while selecting (so nothing below moves) but fades and
            // stops taking input.
            val searchAlpha by animateFloatAsState(
                targetValue = if (selectionMode) 0f else 1f,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "searchAlpha",
            )
            Column(
                modifier = Modifier
                    .graphicsLayer { alpha = searchAlpha }
                    .padding(horizontal = margin, vertical = 8.dp)
                    // A full-width field across a landscape tablet reads as a web form; cap it.
                    .widthIn(max = YomuReadableMaxWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    enabled = !selectionMode,
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

/** Title/action swap between browsing and selection: a quick fade with a small vertical lift. */
private fun <S> AnimatedContentTransitionScope<S>.headerSwap(): ContentTransform = (fadeIn() + androidx.compose.animation.slideInVertically { it / 3 }) togetherWith
    (fadeOut() + androidx.compose.animation.slideOutVertically { -it / 3 })
