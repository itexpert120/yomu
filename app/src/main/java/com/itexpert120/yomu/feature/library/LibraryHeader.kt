package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

private val FieldHeight = 52.dp

/**
 * Search pinned at the top of the library: back, and one raised field that opens focused. Clear
 * empties the field without leaving search; the IME's search key keeps the query as a recent search.
 */
@Composable
internal fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalInset: Dp = 0.dp,
) {
    val stack = YomuTheme.stack
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Opens focused, keyboard up. The field attaches during this frame's layout; focus after it.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp + horizontalInset, end = 16.dp + horizontalInset, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
            Icon(StackIcons.ArrowBack, contentDescription = "Close search", tint = stack.ink)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = YomuReadableMaxWidth)
                .height(FieldHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(stack.raised)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(StackIcons.Search, contentDescription = null, tint = stack.muted)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "Titles, authors",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = stack.muted,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = stack.ink,
                    ),
                    cursorBrush = SolidColor(stack.ink),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                        capitalization = KeyboardCapitalization.None,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSubmit(query)
                            focusManager.clearFocus()
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = "Search your library" },
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        runCatching { focusRequester.requestFocus() }
                    },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(StackIcons.Close, contentDescription = "Clear search", tint = stack.ink, modifier = Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }
        }
    }
}

/**
 * Selection mode's header: close, how many are selected, and Select all / Deselect all as words.
 */
@Composable
internal fun LibrarySelectionHeader(
    selectedCount: Int,
    allSelected: Boolean,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalInset: Dp = 0.dp,
) {
    val stack = YomuTheme.stack
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp + horizontalInset, end = 12.dp + horizontalInset, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onExitSelection, shapes = IconButtonDefaults.shapes()) {
            Icon(StackIcons.Close, contentDescription = "Exit selection", tint = stack.ink)
        }
        Text(
            text = "$selectedCount selected",
            style = MaterialTheme.typography.titleLarge,
            color = stack.ink,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TextButton(
            onClick = if (allSelected) onDeselectAll else onSelectAll,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = stack.ink),
        ) {
            Text(
                text = if (allSelected) "Deselect all" else "Select all",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
