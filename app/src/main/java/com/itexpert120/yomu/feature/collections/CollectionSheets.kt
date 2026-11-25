@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.itexpert120.yomu.feature.collections

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonSize
import com.itexpert120.yomu.core.designsystem.YomuDialog
import com.itexpert120.yomu.core.designsystem.YomuSectionLabel
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.bleedHorizontally
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.data.collections.LibraryTab
import com.itexpert120.yomu.data.collections.LibraryTabKeys
import com.itexpert120.yomu.data.collections.Membership
import com.itexpert120.yomu.data.collections.OrganizeCollection
import com.itexpert120.yomu.data.collections.StatusChoice
import kotlinx.coroutines.launch

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
                style = StackType.Caption,
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
            NewCollectionField(onCreate = onCreateCollection, modifier = Modifier.padding(top = 14.dp))
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
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .drawBehind { drawRect(container) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (choice == StatusChoice.Auto) "Automatic" else choice.label,
                        style = StackType.Label.copy(fontSize = 14.sp),
                        color = if (checked) stack.onInk else stack.ink,
                    )
                }
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
            Text(
                text = if (collection.of > 0) "${collection.members} of ${collection.of}" else "Some",
                style = StackType.Caption,
                color = stack.muted,
            )
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
 * Shelves: every library tab — All, the status tabs, then "Your collections" — to show or hide with
 * a switch and to reorder by dragging a row's handle (within its group; All stays first). Tapping a
 * collection's name renames or deletes it, and the last row adds a new one.
 */
@Composable
fun ManageTabsSheet(
    visible: Boolean,
    tabs: List<LibraryTab>,
    hiddenKeys: Set<String>,
    onCreate: (String) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onReorder: (order: List<String>) -> Unit,
    onSetHidden: (key: String, hidden: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    YomuBottomSheet(visible = visible, onDismiss = onDismiss) { dismiss ->
        YomuSheetHeader(title = "Shelves", onClose = dismiss)
        Column {
            Text(
                text = "Drag to reorder, switch to hide.",
                color = YomuTheme.stack.muted,
                style = StackType.Supporting,
                modifier = Modifier.padding(bottom = YomuTheme.space.xs),
            )
            ShelfRows(
                tabs = tabs,
                hiddenKeys = hiddenKeys,
                onReorder = onReorder,
                onEdit = { editing = it },
                onSetHidden = onSetHidden,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bleedHorizontally(ShelfRowBleed)
                    .heightIn(min = 56.dp)
                    .yomuPressable(onClick = { creating = true }, role = Role.Button, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = ShelfRowBleed),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(StackIcons.Add, contentDescription = null, tint = YomuTheme.stack.ink)
                Text("New collection", style = StackType.Heading, color = YomuTheme.stack.ink)
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

/** Rows reach 8dp into the sheet's 24dp gutter so the drag handle sits 16dp in, as on the board. */
private val ShelfRowBleed = 8.dp

/**
 * The tab rows in two groups. A drag moves the row with the finger; crossing half a neighbour swaps
 * them (the neighbour glides over on the spatial spring), and the order is saved when it's let go.
 */
@Composable
private fun ShelfRows(
    tabs: List<LibraryTab>,
    hiddenKeys: Set<String>,
    onReorder: (List<String>) -> Unit,
    onEdit: (collectionId: String) -> Unit,
    onSetHidden: (key: String, hidden: Boolean) -> Unit,
) {
    val keys = tabs.map { it.key }
    // The order on screen; it runs ahead of the saved one while a drag is in flight.
    var order by remember(keys) { mutableStateOf(keys) }
    val byKey = tabs.associateBy { it.key }
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val animate = yomuAnimationsEnabled()
    var dragging by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { Animatable(0f) }
    val shifts = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }
    var rowHeight by remember { mutableIntStateOf(0) }

    fun groupOf(key: String) = when {
        key == LibraryTabKeys.ALL -> 0
        LibraryTabKeys.collectionId(key) == null -> 1
        else -> 2
    }

    fun movable(key: String) = key != LibraryTabKeys.ALL

    // Moves [key] one place (when its neighbour is in the same group); returns whether it moved.
    fun step(key: String, down: Boolean): Boolean {
        val from = order.indexOf(key)
        val to = if (down) from + 1 else from - 1
        val neighbour = order.getOrNull(to) ?: return false
        if (!movable(neighbour) || groupOf(neighbour) != groupOf(key)) return false
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        if (animate && rowHeight > 0) {
            val shift = shifts.getOrPut(neighbour) { Animatable(0f) }
            scope.launch {
                shift.snapTo(if (down) rowHeight.toFloat() else -rowHeight.toFloat())
                shift.animateTo(0f, spatial)
            }
        }
        return true
    }

    @Composable
    fun Entry(key: String) {
        val tab = byKey[key] ?: return
        val index = order.indexOf(key)
        val canUp = index > 0 && movable(order[index - 1]) && groupOf(order[index - 1]) == groupOf(key)
        val canDown = index < order.lastIndex && groupOf(order[index + 1]) == groupOf(key)
        ShelfRow(
            tab = tab,
            hidden = key in hiddenKeys,
            draggable = movable(key),
            dragged = dragging == key,
            offset = {
                when (key) {
                    dragging -> dragOffset.value
                    else -> shifts[key]?.value ?: 0f
                }
            },
            onMeasured = { rowHeight = it },
            onDragStart = {
                dragging = key
                scope.launch { dragOffset.snapTo(0f) }
            },
            onDrag = { delta ->
                scope.launch {
                    var offset = dragOffset.value + delta
                    val half = rowHeight / 2f
                    if (rowHeight > 0) {
                        if (offset > half && step(key, down = true)) offset -= rowHeight
                        if (offset < -half && step(key, down = false)) offset += rowHeight
                    }
                    dragOffset.snapTo(offset)
                }
            },
            onDragEnd = {
                onReorder(order)
                scope.launch {
                    if (animate) dragOffset.animateTo(0f, spatial) else dragOffset.snapTo(0f)
                    dragging = null
                }
            },
            onMoveUp = if (canUp) ({ if (step(key, down = false)) onReorder(order) }) else null,
            onMoveDown = if (canDown) ({ if (step(key, down = true)) onReorder(order) }) else null,
            onEdit = tab.collectionId?.let { id -> { onEdit(id) } },
            onSetHidden = onSetHidden,
        )
    }

    order.filter { groupOf(it) < 2 }.forEach { key -> Entry(key) }
    val collections = order.filter { groupOf(it) == 2 }
    if (collections.isNotEmpty()) {
        YomuSectionLabel("Your collections", modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        collections.forEach { key -> Entry(key) }
    }
}

@Composable
private fun ShelfRow(
    tab: LibraryTab,
    hidden: Boolean,
    draggable: Boolean,
    dragged: Boolean,
    offset: () -> Float,
    onMeasured: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onSetHidden: (key: String, hidden: Boolean) -> Unit,
) {
    val stack = YomuTheme.stack
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .zIndex(if (dragged) 1f else 0f)
            .fillMaxWidth()
            .bleedHorizontally(ShelfRowBleed)
            .onSizeChanged { onMeasured(it.height) }
            .graphicsLayer { translationY = offset() }
            .background(stack.sheet)
            .heightIn(min = 56.dp)
            .ruleBelow(!dragged, stack.rule)
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let {
                        CustomAccessibilityAction("Move ${tab.label} up") {
                            it()
                            true
                        }
                    },
                    onMoveDown?.let {
                        CustomAccessibilityAction("Move ${tab.label} down") {
                            it()
                            true
                        }
                    },
                )
            }
            .padding(horizontal = ShelfRowBleed),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            StackIcons.DragIndicator,
            contentDescription = null,
            tint = if (draggable) stack.faint else stack.track,
            modifier = if (draggable) {
                Modifier
                    .size(24.dp)
                    .pointerInput(tab.key) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                onDragStart()
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                            onVerticalDrag = { change, delta ->
                                change.consume()
                                onDrag(delta)
                            },
                        )
                    }
            } else {
                Modifier.size(24.dp)
            },
        )
        Box(Modifier.weight(1f)) {
            if (onEdit != null) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .yomuPressable(onClick = onEdit, role = Role.Button, shape = RoundedCornerShape(16.dp))
                        .semantics { contentDescription = "Rename or delete ${tab.label}" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = stack.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(StackIcons.Edit, contentDescription = null, tint = stack.faint, modifier = Modifier.size(18.dp))
                }
            } else {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = stack.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (tab.hideable) {
            YomuTogglePill(
                checked = !hidden,
                onCheckedChange = { show -> onSetHidden(tab.key, !show) },
                modifier = Modifier.semantics { contentDescription = "Show ${tab.label}" },
            )
        } else {
            Text("Always shown", style = StackType.Value, color = stack.muted)
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
            placeholder = "New collection",
            showLabel = false,
            imeAction = ImeAction.Done,
            onImeAction = submit,
            modifier = Modifier.weight(1f),
        )
        // Ink like the board even while the field is empty; an empty name just does nothing.
        YomuButton(text = "Add", onClick = submit, size = YomuButtonSize.InRow)
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
