@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuScreenTitle
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.designsystem.yomuStableSystemBarInsets

/**
 * Search stays in place so entering a query never replaces the collection toolbar; once scrolled
 * into the library it folds into a search action and returns on demand. Selection mode
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
    // Leading/trailing edge of the (centred, width-capped) library content, so the title and the
    // search row line up with the grid or list below.
    contentInset: Dp = 16.dp,
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
    val animate = yomuAnimationsEnabled()
    // Selection is a contextual mode: the header takes the container tint to signal it.
    val selectionTint = animateFloatAsState(
        targetValue = if (selectionMode) 1f else 0f,
        animationSpec = if (animate) MaterialTheme.motionScheme.defaultEffectsSpec() else snap(),
        label = "selectionTint",
    )
    // The app bar and the search row share one surface, so the whole header tints together as the
    // library scrolls under it (tracking the same collapse fraction the app bar uses). Evaluated in
    // the draw phase so scrolling repaints the header without recomposing it.
    val headerTint: () -> Float = {
        maxOf(
            YomuMotion.EmphasizedAccel.transform(scrollBehavior.state.collapsedFraction),
            selectionTint.value,
        )
    }
    // Once the title has collapsed (scrolled into the library), the search field folds away and a
    // search action takes its place; tapping it brings the field back focused. An in-progress search
    // (focused or with a query) always keeps the field.
    var searchRevealed by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val titleCollapsed by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior.state.collapsedFraction > 0.5f }
    }
    LaunchedEffect(titleCollapsed) { if (!titleCollapsed) searchRevealed = false }
    val searchFieldVisible = !titleCollapsed || searchRevealed || searchFocused || searchQuery.isNotEmpty()
    val searchFocusRequester = remember { FocusRequester() }
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.drawBehind {
            drawRect(lerp(scheme.background, scheme.surfaceContainer, headerTint()))
        },
    ) {
        LargeFlexibleTopAppBar(
            title = {
                AnimatedContent(
                    targetState = selectionMode,
                    transitionSpec = { headerSwap() },
                    label = "libraryTitle",
                ) { selecting ->
                    YomuScreenTitle(
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
                // The slot's width animates with the icon, so the title glides into place instead
                // of snapping left once the close button has faded out.
                // (Standard, non-bouncing springs: an overshooting width would wobble the title.)
                val slotSpring = remember { MotionScheme.standard() }
                AnimatedVisibility(
                    visible = selectionMode,
                    enter = if (animate) {
                        expandHorizontally(slotSpring.defaultSpatialSpec(), expandFrom = Alignment.Start) +
                            fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            scaleIn(MaterialTheme.motionScheme.fastSpatialSpec())
                    } else {
                        EnterTransition.None
                    },
                    exit = if (animate) {
                        shrinkHorizontally(slotSpring.defaultSpatialSpec(), shrinkTowards = Alignment.Start) +
                            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            scaleOut(MaterialTheme.motionScheme.fastSpatialSpec())
                    } else {
                        ExitTransition.None
                    },
                ) {
                    TooltipIconButton(
                        label = "Exit selection",
                        icon = StackIcons.Close,
                        onClick = onExitSelection,
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            // Same stable insets as YomuScreenHeader, so the bar stays put while the outgoing
            // reader still hides the system bars. The bar's own title inset is 16dp; the extra
            // gutter shifts it to the content's leading edge.
            windowInsets = yomuStableSystemBarInsets()
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                .add((contentInset - 16.dp).coerceAtLeast(0.dp).let { WindowInsets(left = it, right = it) }),
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
                                icon = if (allSelected) StackIcons.Deselect else StackIcons.SelectAll,
                                onClick = if (allSelected) onDeselectAll else onSelectAll,
                            )
                        } else {
                            AnimatedVisibility(
                                visible = !searchFieldVisible,
                                enter = yomuPopupEnter(),
                                exit = yomuPopupExit(),
                            ) {
                                TooltipIconButton(
                                    label = "Search library",
                                    icon = StackIcons.Search,
                                    onClick = { searchRevealed = true },
                                )
                            }
                            if (showImport) {
                                TooltipIconButton(
                                    label = "Import book",
                                    icon = StackIcons.Add,
                                    emphasized = true,
                                    onClick = onImport,
                                )
                            }
                            TooltipIconButton(
                                label = "Arrange library",
                                icon = StackIcons.Tune,
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
            // Selection folds the search row away rather than leaving an empty band: the height
            // animates on a non-bouncing spring so the grid glides up by just that row instead of
            // jumping, while the title and actions above stay put.
            val layoutSpring = remember { MotionScheme.standard() }
            // Focus the field once it has been revealed from the search action.
            LaunchedEffect(searchRevealed) {
                if (searchRevealed) {
                    // The field is only attached after this frame's layout; focusing earlier is a no-op.
                    withFrameNanos { }
                    runCatching { searchFocusRequester.requestFocus() }
                }
            }
            AnimatedVisibility(
                visible = !selectionMode && searchFieldVisible,
                enter = if (animate) {
                    expandVertically(layoutSpring.defaultSpatialSpec(), expandFrom = Alignment.Top) +
                        fadeIn(layoutSpring.defaultEffectsSpec())
                } else {
                    EnterTransition.None
                },
                exit = if (animate) {
                    shrinkVertically(layoutSpring.defaultSpatialSpec(), shrinkTowards = Alignment.Top) +
                        fadeOut(layoutSpring.fastEffectsSpec())
                } else {
                    ExitTransition.None
                },
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = contentInset, vertical = YomuTheme.space.xs)
                        // A full-width field across a landscape tablet reads as a web form; cap it.
                        .widthIn(max = YomuReadableMaxWidth)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            // The pill paints its own container in the draw phase, keeping the
                            // field distinct from whichever header tint is behind it.
                            .clip(YomuShapeTokens.pill)
                            .drawBehind {
                                drawRect(
                                    lerp(scheme.surfaceContainerHigh, scheme.surfaceContainerHighest, headerTint()),
                                )
                            }
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged {
                                if (it.isFocused && !searchActive) onSearchToggle()
                                // Leaving an empty, revealed field lets it fold away again.
                                if (searchFocused && !it.isFocused && searchQuery.isEmpty()) searchRevealed = false
                                searchFocused = it.isFocused
                            },
                        placeholder = { Text("Search your library") },
                        leadingIcon = { Icon(StackIcons.Search, contentDescription = "Search library") },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { onSearchQueryChange("") }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(StackIcons.Close, contentDescription = "Clear search")
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                        shape = YomuShapeTokens.pill,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
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
                            modifier = Modifier.padding(horizontal = YomuTheme.space.md),
                        )
                    }
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
private fun <S> AnimatedContentTransitionScope<S>.headerSwap(): ContentTransform {
    if (!yomuAnimationsEnabled()) return EnterTransition.None togetherWith ExitTransition.None
    val lift = YomuMotion.scheme.fastSpatialSpec<IntOffset>()
    return (fadeIn(YomuMotion.scheme.fastEffectsSpec()) + slideInVertically(lift) { it / 3 }) togetherWith
        (fadeOut(YomuMotion.scheme.fastEffectsSpec()) + slideOutVertically(lift) { -it / 3 })
}
