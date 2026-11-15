package com.itexpert120.yomu.feature.sync

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SyncDisabled
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuLoadingIndicator
import com.itexpert120.yomu.core.designsystem.YomuSettingContainer
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.data.sync.SyncStatus
import com.itexpert120.yomu.data.sync.SyncStatus.Phase

/** Stateful entry point for Settings → Sync and its sheets. */
@Composable
fun SyncSection(viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    SyncContent(
        state = state,
        onTurnOn = viewModel::onTurnOn,
        onOpenJoin = viewModel::onOpenJoin,
        onShowWords = viewModel::onShowWords,
        onTurnOff = viewModel::onRequestTurnOff,
        onDelete = viewModel::onRequestDelete,
    )
    WordsSheet(state = state.words, onDismiss = viewModel::onDismissWords)
    JoinSheet(
        state = state.join,
        onDismiss = viewModel::onDismissJoin,
        onInput = viewModel::onJoinInput,
        onPickSuggestion = viewModel::onPickSuggestion,
        onStartScan = viewModel::onStartScan,
        onCancelScan = viewModel::onCancelScan,
        onScanned = viewModel::onScanned,
        onJoin = viewModel::onJoin,
    )
    if (state.confirmTurnOff) {
        ConfirmDialog(
            title = "Turn off sync on this device?",
            body = "Your library stays here, and your other devices keep syncing. You can join again with your sync words.",
            confirm = "Turn off",
            onConfirm = viewModel::onConfirmTurnOff,
            onDismiss = viewModel::onDismissConfirm,
        )
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = "Delete synced data?",
            body = "This removes the synced copy from the server and stops sync on every device. Each device keeps its own library. This can't be undone.",
            confirm = "Delete",
            onConfirm = viewModel::onConfirmDelete,
            onDismiss = viewModel::onDismissConfirm,
        )
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissError,
            confirmButton = { TextButton(onClick = viewModel::onDismissError) { Text("OK") } },
            text = { Text(message) },
        )
    }
}

@Composable
fun SyncContent(
    state: SyncUiState,
    onTurnOn: () -> Unit,
    onOpenJoin: () -> Unit,
    onShowWords: () -> Unit,
    onTurnOff: () -> Unit,
    onDelete: () -> Unit,
) {
    YomuSettingGroup(
        title = "Sync",
        subtitle = "Keep your place, highlights, bookmarks, stats and settings the same on all your devices. Book files stay on each device.",
    ) {
        when {
            !state.available -> YomuSettingContainer(position = YomuSettingPosition.Single) {
                Text(
                    text = "Sync isn't available in this build.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            !state.enabled -> SyncOff(state, onTurnOn, onOpenJoin)

            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StatusRow(state.status)
                YomuSettingRow(
                    title = "Show sync words",
                    subtitle = "Enter or scan them on another device to add it",
                    position = YomuSettingPosition.Middle,
                    enabled = !state.busy,
                    onClick = onShowWords,
                    leadingContent = { Icon(Icons.Rounded.Key, contentDescription = null) },
                ) {}
                YomuSettingRow(
                    title = "Turn off on this device",
                    position = YomuSettingPosition.Middle,
                    enabled = !state.busy,
                    onClick = onTurnOff,
                    leadingContent = { Icon(Icons.Rounded.SyncDisabled, contentDescription = null) },
                ) {}
                YomuSettingRow(
                    title = "Delete synced data",
                    subtitle = "Stops sync everywhere; libraries stay on each device",
                    position = YomuSettingPosition.Last,
                    enabled = !state.busy,
                    onClick = onDelete,
                    leadingContent = {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                ) {}
            }
        }
    }
}

@Composable
private fun SyncOff(state: SyncUiState, onTurnOn: () -> Unit, onOpenJoin: () -> Unit) {
    YomuSettingContainer(position = YomuSettingPosition.Single) {
        if (state.status.phase == Phase.GroupDeleted) {
            Text(
                text = "Sync was turned off from another device. Your library here hasn't changed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "No account needed. Sync uses 12 words that only your devices know, and everything is encrypted before it leaves this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onTurnOn,
            enabled = !state.busy,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonDefaults.MediumContainerHeight),
        ) {
            if (state.busy) YomuLoadingIndicator(modifier = Modifier.size(24.dp)) else Text("Turn on sync")
        }
        FilledTonalButton(
            onClick = onOpenJoin,
            enabled = !state.busy,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("I have sync words") }
    }
}

@Composable
private fun StatusRow(status: SyncStatus) {
    val (title, subtitle, icon) = when (status.phase) {
        Phase.Off, Phase.Connecting -> Triple("Connecting…", null, Icons.Rounded.CloudSync)
        Phase.Syncing -> Triple("Syncing…", null, Icons.Rounded.CloudSync)
        Phase.UpToDate -> Triple("Up to date", null, Icons.Rounded.CloudDone)
        Phase.Offline -> Triple("Offline", "Changes are saved here and will sync when you're back online", Icons.Rounded.CloudOff)
        Phase.UpdateRequired -> Triple(
            "Update Yomu to keep syncing",
            "This version can no longer sync. Your changes are saved here and will sync after you update.",
            Icons.Rounded.SystemUpdate,
        )
        Phase.ServerOutdated -> Triple(
            "Sync is temporarily unavailable",
            "Your changes are saved here and will sync once it's back.",
            Icons.Rounded.CloudOff,
        )
        Phase.GroupDeleted -> Triple("Sync was turned off from another device", null, Icons.Rounded.SyncDisabled)
    }
    val warning = status.phase == Phase.UpdateRequired || status.phase == Phase.ServerOutdated
    YomuSettingRow(
        title = title,
        subtitle = subtitle,
        position = YomuSettingPosition.First,
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        },
    ) {}
}

@Composable
private fun WordsSheet(state: WordsSheetState?, onDismiss: () -> Unit) {
    YomuBottomSheet(visible = state != null, onDismiss = onDismiss) { dismiss ->
        val words = state?.words ?: return@YomuBottomSheet
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SheetHeader(
                title = if (state.justCreated) "Your sync words" else "Sync words",
                body = if (state.justCreated) {
                    "Scan this code or enter these words on your other devices. Keep them somewhere safe: anyone with them can read your synced library, and they can't be recovered."
                } else {
                    "Scan this code or enter these words on another device. Anyone with them can read your synced library."
                },
            )
            SyncQrCode(
                content = SyncQr.encode(words),
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            )
            WordGrid(words)
            Button(
                onClick = dismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) { Text(if (state.justCreated) "I've saved them" else "Done") }
        }
    }
}

@Composable
private fun WordGrid(words: List<String>) {
    // Numbered, in two columns read top to bottom, so the order is unambiguous when copied out.
    val half = (words.size + 1) / 2
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(words.take(half), words.drop(half)).forEachIndexed { column, list ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                list.forEachIndexed { index, word ->
                    YomuSettingContainer(position = YomuSettingPosition.Single, dense = true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "${column * half + index + 1}",
                                style = YomuTheme.type.mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(text = word, style = MaterialTheme.typography.bodyLargeEmphasized)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JoinSheet(
    state: JoinFlowState,
    onDismiss: () -> Unit,
    onInput: (String) -> Unit,
    onPickSuggestion: (String) -> Unit,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onScanned: (String) -> Unit,
    onJoin: () -> Unit,
) {
    YomuBottomSheet(visible = state.visible, onDismiss = onDismiss) { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SheetHeader(
                title = "Add this device",
                body = "On a device that already syncs, open Settings → Sync → Show sync words. Then scan its code or type the 12 words.",
            )
            AnimatedContent(targetState = state.scanning, label = "joinMode") { scanning ->
                if (scanning) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SyncQrScanner(onScanned = onScanned)
                        OutlinedButton(onClick = onCancelScan, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                            Text("Type the words instead")
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onStartScan, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Text("Scan code", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                        }
                        YomuTextField(
                            value = state.input,
                            onValueChange = onInput,
                            label = "Sync words",
                            singleLine = false,
                            minLines = 3,
                            isError = state.error != null,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { onJoin() }),
                        )
                        if (state.suggestions.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.suggestions.forEach { word ->
                                    SuggestionChip(onClick = { onPickSuggestion(word) }, label = { Text(word) })
                                }
                            }
                        }
                    }
                }
            }
            state.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onJoin,
                enabled = state.valid && !state.busy,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                if (state.busy) YomuLoadingIndicator(modifier = Modifier.size(24.dp)) else Text("Start syncing")
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Preview
@Composable
private fun SyncOffPreview() {
    YomuDesignTheme {
        SyncContent(SyncUiState(), {}, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun SyncOnPreview() {
    YomuDesignTheme {
        SyncContent(SyncUiState(enabled = true, status = SyncStatus(Phase.UpToDate)), {}, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun SyncUpdateRequiredPreview() {
    YomuDesignTheme {
        SyncContent(SyncUiState(enabled = true, status = SyncStatus(Phase.UpdateRequired)), {}, {}, {}, {}, {})
    }
}
