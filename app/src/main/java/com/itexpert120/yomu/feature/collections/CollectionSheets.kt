@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.itexpert120.yomu.feature.collections

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuDialog
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.data.collections.LibraryTab
import com.itexpert120.yomu.data.collections.Membership
import com.itexpert120.yomu.data.collections.OrganizeCollection
import com.itexpert120.yomu.data.collections.StatusChoice

/**
 * Organizes one or more books: a manual status (Automatic follows reading progress) as chips, and a
 * checklist of collections with an inline field to make a new one (which the books then join).
 */
@Composable
fun OrganizeSheet(
    visible: Boolean,
    title: String,
    status: StatusChoice,
    collections: List<OrganizeCollection>,
    onSetStatus: (ManualStatus?) -> Unit,
    onSetCollection: (id: String, member: Boolean) -> Unit,
    onCreateCollection: (name: String) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { dismiss ->
        YomuSheetHeader(title = title, subtitle = subtitle, onClose = dismiss)
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
            YomuSectionLabel("Status")
            StatusChips(status = status, onSetStatus = onSetStatus)
            Text(
                text = "Automatic follows reading progress. On hold and Dropped always win.",
                color = YomuTheme.stack.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column {
            YomuSectionLabel("Collections", modifier = Modifier.padding(bottom = YomuTheme.space.xxs))
            collections.forEachIndexed { index, collection ->
                CollectionCheckRow(
                    collection = collection,
                    divider = index < collections.lastIndex,
                    onSetCollection = onSetCollection,
                )
            }
            NewCollectionField(onCreate = onCreateCollection, modifier = Modifier.padding(top = YomuTheme.space.sm))
        }
    }
}

@Composable
private fun StatusChips(status: StatusChoice, onSetStatus: (ManualStatus?) -> Unit) {
    val haptics = LocalHapticFeedback.current
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        StatusChoice.Options.forEach { choice ->
            val checked = choice == status
            val stack = YomuTheme.stack
            val animate = yomuAnimationsEnabled()
            val container by animateColorAsState(
                targetValue = if (checked) stack.ink else stack.raised,
                animationSpec = if (animate) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
                label = "statusChip",
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = checked,
                        role = Role.RadioButton,
                        indication = null,
                        interactionSource = null,
                        onClick = {
                            if (!checked) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSetStatus(choice.status)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (choice == StatusChoice.Auto) "Automatic" else choice.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (checked) stack.onInk else stack.ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .drawBehind { drawRect(container) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun CollectionCheckRow(
    collection: OrganizeCollection,
    divider: Boolean,
    onSetCollection: (id: String, member: Boolean) -> Unit,
) {
    val stack = YomuTheme.stack
    val all = collection.membership == Membership.All
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .triStateToggleable(
                state = collection.membership.toggleableState(),
                role = Role.Checkbox,
                onClick = { onSetCollection(collection.id, !all) },
            )
            .ruleBelow(divider, stack.rule),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StackCheckbox(collection.membership)
        Text(
            text = collection.name,
            style = MaterialTheme.typography.bodyLarge,
            color = stack.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (collection.membership == Membership.Some) {
            Text("Some", style = MaterialTheme.typography.bodySmall, color = stack.muted)
        }
    }
}

/** A square check: empty, ink with a check (all), or ink with a dash (some). */
@Composable
private fun StackCheckbox(membership: Membership) {
    val stack = YomuTheme.stack
    val on = membership != Membership.None
    val animate = yomuAnimationsEnabled()
    val fill by animateColorAsState(
        targetValue = if (on) stack.ink else Color.Transparent,
        animationSpec = if (animate) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
        label = "checkFill",
    )
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(shape)
            .drawBehind { drawRect(fill) }
            .border(1.5.dp, if (on) stack.ink else stack.faint, shape),
        contentAlignment = Alignment.Center,
    ) {
        when (membership) {
            Membership.All -> Icon(StackIcons.Check, contentDescription = null, tint = stack.onInk, modifier = Modifier.size(18.dp))
            Membership.Some -> Icon(StackIcons.Remove, contentDescription = null, tint = stack.onInk, modifier = Modifier.size(18.dp))
            Membership.None -> Unit
        }
    }
}

/**
 * Shelves: every library tab in order — the built-in status tabs and the user's collections — to
 * show or hide and move up or down; tapping a collection renames or deletes it, and the last row
 * adds a new one.
 */
@Composable
fun ManageTabsSheet(
    visible: Boolean,
    tabs: List<LibraryTab>,
    hiddenKeys: Set<String>,
    onCreate: (String) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onMove: (key: String, up: Boolean) -> Unit,
    onSetHidden: (key: String, hidden: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { dismiss ->
        YomuSheetHeader(
            title = "Shelves",
            subtitle = "Move to reorder · switch to hide",
            onClose = dismiss,
        )
        Column {
            tabs.forEachIndexed { index, tab ->
                ShelfRow(
                    tab = tab,
                    hidden = tab.key in hiddenKeys,
                    canMoveUp = index > 0,
                    canMoveDown = index < tabs.lastIndex,
                    onEdit = tab.collectionId?.let { id -> { editing = id } },
                    onMove = onMove,
                    onSetHidden = onSetHidden,
                )
            }
            TextButton(
                onClick = { creating = true },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = YomuTheme.stack.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Icon(StackIcons.Add, contentDescription = null)
                Text(
                    text = "New collection",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = YomuTheme.space.sm),
                )
            }
        }
    }

    if (creating) {
        NewCollectionDialog(
            onCreate = onCreate,
            onDismiss = { creating = false },
        )
    }

    tabs.firstOrNull { it.collectionId != null && it.collectionId == editing }?.let { tab ->
        val id = checkNotNull(tab.collectionId)
        EditCollectionDialog(
            name = tab.label,
            bookCount = tab.count,
            onSave = { onRename(id, it) },
            onDelete = { onDelete(id) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ShelfRow(
    tab: LibraryTab,
    hidden: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: (() -> Unit)?,
    onMove: (key: String, up: Boolean) -> Unit,
    onSetHidden: (key: String, hidden: Boolean) -> Unit,
) {
    val stack = YomuTheme.stack
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .ruleBelow(true, stack.rule),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (onEdit != null) {
                TextButton(
                    onClick = onEdit,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (hidden) stack.muted else stack.ink),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 12.dp),
                    modifier = Modifier.semantics { contentDescription = "Rename or delete ${tab.label}" },
                ) {
                    Text(tab.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(
                        StackIcons.Edit,
                        contentDescription = null,
                        tint = stack.muted,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(18.dp),
                    )
                }
            } else {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hidden) stack.muted else stack.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val note = when {
                hidden -> "Hidden"
                tab.showsOnlyWhenFilled -> "Shown when it has books"
                !tab.hideable -> "Always shown"
                else -> null
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = stack.muted)
            }
        }
        if (tab.hideable) {
            YomuTogglePill(
                checked = !hidden,
                onCheckedChange = { show -> onSetHidden(tab.key, !show) },
                modifier = Modifier
                    .padding(end = YomuTheme.space.xxs)
                    .semantics { contentDescription = "Show ${tab.label}" },
            )
        }
        IconButton(onClick = { onMove(tab.key, true) }, enabled = canMoveUp, shapes = IconButtonDefaults.shapes()) {
            Icon(
                StackIcons.KeyboardArrowUp,
                contentDescription = "Move ${tab.label} up",
                tint = if (canMoveUp) stack.ink else stack.track,
            )
        }
        IconButton(onClick = { onMove(tab.key, false) }, enabled = canMoveDown, shapes = IconButtonDefaults.shapes()) {
            Icon(
                StackIcons.KeyboardArrowDown,
                contentDescription = "Move ${tab.label} down",
                tint = if (canMoveDown) stack.ink else stack.track,
            )
        }
    }
}

private fun Modifier.ruleBelow(show: Boolean, color: Color): Modifier = if (!show) {
    this
} else {
    drawBehind {
        val rule = 1.dp.toPx()
        drawRect(color = color, topLeft = Offset(0f, size.height - rule), size = size.copy(height = rule))
    }
}

private fun Membership.toggleableState() = when (this) {
    Membership.None -> ToggleableState.Off
    Membership.Some -> ToggleableState.Indeterminate
    Membership.All -> ToggleableState.On
}

@Composable
private fun NewCollectionField(onCreate: (String) -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (name.isNotBlank()) {
            onCreate(name.trim())
            name = ""
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        YomuTextField(
            value = name,
            onValueChange = { name = it },
            label = "New collection",
            placeholder = "e.g. Book club",
            imeAction = ImeAction.Done,
            onImeAction = submit,
            modifier = Modifier.weight(1f),
        )
        YomuButton(text = "Add", onClick = submit, enabled = name.isNotBlank())
    }
}

@Composable
private fun NewCollectionDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    YomuDialog(
        title = "New collection",
        confirmLabel = "Add",
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            onCreate(name.trim())
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        YomuTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            placeholder = "e.g. Book club",
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
private fun EditCollectionDialog(
    name: String,
    bookCount: Int,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(name) { mutableStateOf(name) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) {
        YomuDialog(
            title = "Delete “$name”?",
            text = "The collection and its tab go away. Its books stay in your library.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                onDelete()
                onDismiss()
            },
            onDismiss = { confirmDelete = false },
        )
        return
    }
    YomuDialog(
        title = "Edit collection",
        confirmLabel = "Save",
        confirmEnabled = draft.isNotBlank() && draft.trim() != name,
        onConfirm = {
            onSave(draft)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        YomuTextField(
            value = draft,
            onValueChange = { draft = it },
            label = "Name",
            imeAction = ImeAction.Done,
        )
        Text(
            text = "Deleting a collection keeps its ${if (bookCount == 1) "book" else "$bookCount books"} in your library.",
            style = MaterialTheme.typography.bodySmall,
            color = YomuTheme.stack.muted,
        )
        TextButton(
            onClick = { confirmDelete = true },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Delete collection", style = MaterialTheme.typography.titleMedium)
        }
    }
}
