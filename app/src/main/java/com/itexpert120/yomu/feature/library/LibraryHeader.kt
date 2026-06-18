package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.itexpert120.yomu.core.designsystem.YomuTheme

@Composable
internal fun LibraryHeader(
    bookCount: Int,
    searchActive: Boolean,
    searchQuery: String,
    sortMode: SortMode,
    groupMode: GroupMode,
    showSortMenu: Boolean,
    showGroupMenu: Boolean,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onGroupModeChange: (GroupMode) -> Unit,
    onSortMenuToggle: () -> Unit,
    onGroupMenuToggle: () -> Unit,
    onDismissMenus: () -> Unit,
    onImport: () -> Unit,
    onThemeToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Library",
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.display,
                )
                Text(
                    text = "$bookCount books",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderCircleButton(onClick = onImport) {
                    Text("+", color = YomuTheme.colors.appBackground, style = YomuTheme.type.section)
                }
                HeaderCircleButton(onClick = onThemeToggle) {
                    Text(
                        text = "\u263E",
                        color = YomuTheme.colors.appBackground,
                        style = YomuTheme.type.section,
                    )
                }
            }
        }

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
            SortDropdown(
                currentMode = sortMode,
                expanded = showSortMenu,
                onToggle = onSortMenuToggle,
                onSelect = onSortModeChange,
            )
            GroupDropdown(
                currentMode = groupMode,
                expanded = showGroupMenu,
                onToggle = onGroupMenuToggle,
                onSelect = onGroupModeChange,
            )
        }
    }
}

@Composable
private fun SearchHint(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Search books, authors",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.body,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search",
            tint = YomuTheme.colors.textSecondary,
            modifier = Modifier.size(22.dp),
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

    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surface)
            .border(1.dp, YomuTheme.colors.accent, RoundedCornerShape(YomuTheme.radius.pill))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .padding(vertical = 10.dp),
            textStyle = YomuTheme.type.body.copy(color = YomuTheme.colors.textPrimary),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search books, authors",
                            color = YomuTheme.colors.textMuted,
                            style = YomuTheme.type.body,
                        )
                    }
                    inner()
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(YomuTheme.colors.surfaceRaised)
                .clickable {
                    if (query.isNotEmpty()) {
                        onQueryChange("")
                    } else {
                        keyboardController?.hide()
                        onClose()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = YomuTheme.colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SortDropdown(
    currentMode: SortMode,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (SortMode) -> Unit,
) {
    Box {
        DropdownPill(
            label = "Sort",
            value = currentMode.label,
            onClick = onToggle,
        )
        if (expanded) {
            Popup(
                onDismissRequest = onToggle,
                properties = PopupProperties(focusable = true),
            ) {
                DropdownMenu(
                    items = SortMode.entries,
                    selectedItem = currentMode,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun GroupDropdown(
    currentMode: GroupMode,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (GroupMode) -> Unit,
) {
    Box {
        DropdownPill(
            label = "Group",
            value = currentMode.label,
            onClick = onToggle,
        )
        if (expanded) {
            Popup(
                onDismissRequest = onToggle,
                properties = PopupProperties(focusable = true),
            ) {
                DropdownMenu(
                    items = GroupMode.entries,
                    selectedItem = currentMode,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun <T> DropdownMenu(
    items: List<T>,
    selectedItem: T,
    onSelect: (T) -> Unit,
) where T : Enum<T> {
    Column(
        modifier = Modifier
            .width(130.dp)
            .shadow(8.dp, RoundedCornerShape(YomuTheme.radius.md))
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(YomuTheme.colors.panel)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.md))
            .padding(vertical = 4.dp),
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(YomuTheme.radius.sm))
                    .background(if (isSelected) YomuTheme.colors.accentSoft else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(item) },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (item as Enum<*>).name.replaceFirstChar { it.titlecase() },
                    color = if (isSelected) YomuTheme.colors.accent else YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.body,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Text(
                        text = "\u2713",
                        color = YomuTheme.colors.accent,
                        style = YomuTheme.type.body,
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownPill(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.pill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        Text(text = value, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.caption)
        androidx.compose.material3.Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = YomuTheme.colors.textMuted,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun HeaderCircleButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(YomuTheme.colors.textPrimary)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
