package com.itexpert120.yomu.feature.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuConnectedChoiceGroup
import com.itexpert120.yomu.core.designsystem.YomuDialog
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.yomuSettingPosition
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.data.collections.LibraryTab
import com.itexpert120.yomu.data.collections.Membership
import com.itexpert120.yomu.data.collections.OrganizeCollection
import com.itexpert120.yomu.data.collections.StatusChoice

/**
 * Organizes one or more books: a manual status (Auto follows reading progress) and a
 * checklist of collections, with an inline field to make a new one (which the books then join).
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
) {
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { dismiss ->
        YomuSheetHeader(title = title, subtitle = "Status and collections", onClose = dismiss)
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
            YomuSectionLabel("Status")
            YomuConnectedChoiceGroup(
                options = StatusChoice.Options,
                selected = status,
                label = { it.label },
                icon = { it.status?.shelfStatus?.icon ?: Icons.Rounded.AutoMode },
                onSelect = { onSetStatus(it.status) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Auto files books under Reading, To read or Finished by their progress.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs)) {
            YomuSectionLabel("Collections")
            collections.forEachIndexed { index, collection ->
                val all = collection.membership == Membership.All
                YomuSettingRow(
                    title = collection.name,
                    position = yomuSettingPosition(index, collections.size),
                    role = Role.Checkbox,
                    selected = all,
                    onClick = { onSetCollection(collection.id, !all) },
                ) {
                    TriStateCheckbox(state = collection.membership.toggleableState(), onClick = null)
                }
            }
        }
        NewCollectionField(onCreate = onCreateCollection)
    }
}

/**
 * Every library tab in order — the built-in status tabs and the user's collections — to rearrange,
 * plus collection rename/delete (tap a collection) and a field to add a new one.
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
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { dismiss ->
        YomuSheetHeader(
            title = "Library tabs",
            subtitle = "Arrange or hide tabs, or add a collection of your own",
            onClose = dismiss,
        )
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs)) {
            tabs.forEachIndexed { index, tab ->
                val hidden = tab.key in hiddenKeys
                val subtitle = when {
                    hidden -> "Hidden"
                    tab.collectionId != null -> "${bookCount(tab.count)} · tap to rename"
                    tab.showsOnlyWhenFilled -> "${bookCount(tab.count)} · shown when it has books"
                    else -> bookCount(tab.count)
                }
                YomuSettingRow(
                    title = tab.label,
                    subtitle = subtitle,
                    position = yomuSettingPosition(index, tabs.size),
                    onClick = tab.collectionId?.let { id -> { editing = id } },
                    leadingContent = {
                        Icon(
                            imageVector = tab.status?.icon ?: if (tab.collectionId != null) Icons.Rounded.CollectionsBookmark else Icons.Rounded.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tab.hideable) {
                            YomuTogglePill(
                                checked = !hidden,
                                onCheckedChange = { show -> onSetHidden(tab.key, !show) },
                                modifier = Modifier
                                    .padding(end = YomuTheme.space.xxs)
                                    .semantics { contentDescription = "Show ${tab.label}" },
                            )
                        }
                        IconButton(
                            onClick = { onMove(tab.key, true) },
                            enabled = index > 0,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${tab.label} up")
                        }
                        IconButton(
                            onClick = { onMove(tab.key, false) },
                            enabled = index < tabs.lastIndex,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${tab.label} down")
                        }
                    }
                }
            }
        }
        NewCollectionField(onCreate = onCreate)
    }

    tabs.firstOrNull { it.collectionId != null && it.collectionId == editing }?.let { tab ->
        val id = checkNotNull(tab.collectionId)
        EditCollectionDialog(
            name = tab.label,
            onSave = { onRename(id, it) },
            onDelete = { onDelete(id) },
            onDismiss = { editing = null },
        )
    }
}

private fun Membership.toggleableState() = when (this) {
    Membership.None -> ToggleableState.Off
    Membership.Some -> ToggleableState.Indeterminate
    Membership.All -> ToggleableState.On
}

private fun bookCount(count: Int) = if (count == 1) "1 book" else "$count books"

@Composable
private fun NewCollectionField(onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (name.isNotBlank()) {
            onCreate(name.trim())
            name = ""
        }
    }
    Row(
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
        IconButton(
            onClick = submit,
            enabled = name.isNotBlank(),
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Create collection", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun EditCollectionDialog(
    name: String,
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
        TextButton(onClick = { confirmDelete = true }) {
            Text("Delete collection", color = MaterialTheme.colorScheme.error)
        }
    }
}
