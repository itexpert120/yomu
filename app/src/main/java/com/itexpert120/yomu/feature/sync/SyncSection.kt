package com.itexpert120.yomu.feature.sync

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.SyncDisabled
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuDialog
import com.itexpert120.yomu.core.designsystem.YomuLoadingIndicator
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuNotice
import com.itexpert120.yomu.core.designsystem.YomuNoticeTone
import com.itexpert120.yomu.core.designsystem.YomuSettingContainer
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughEnter
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughExit
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.data.sync.SyncStatus
import com.itexpert120.yomu.data.sync.SyncStatus.Phase
import com.itexpert120.yomu.data.sync.crypto.SyncPhrase

/** Stateful entry point for Settings → Sync and its sheets. */
@Composable
fun SyncSection(viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        YomuDialog(
            title = "Turn off sync on this device?",
            text = "Your library stays here, and your other devices keep syncing. You can join again with your sync words.",
            icon = Icons.Rounded.SyncDisabled,
            confirmLabel = "Turn off",
            onConfirm = viewModel::onConfirmTurnOff,
            onDismiss = viewModel::onDismissConfirm,
        )
    }
    if (state.confirmDelete) {
        YomuDialog(
            title = "Delete synced data?",
            text = "This removes the synced copy from the server and stops sync on every device. Each device keeps its own library. This can't be undone.",
            icon = Icons.Rounded.DeleteForever,
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = viewModel::onConfirmDelete,
            onDismiss = viewModel::onDismissConfirm,
        )
    }
    state.error?.let { message ->
        YomuDialog(
            title = "Sync couldn't finish",
            text = message,
            icon = Icons.Rounded.ErrorOutline,
            confirmLabel = "OK",
            dismissLabel = null,
            onConfirm = viewModel::onDismissError,
            onDismiss = viewModel::onDismissError,
        )
    }
}

private enum class SyncMode { Unavailable, Off, On }

/** Fade-through for a state handoff, with the container resizing on the shared spatial spring. */
private fun <S> AnimatedContentTransitionScope<S>.syncStateHandoff(): ContentTransform = (yomuFadeThroughEnter() togetherWith yomuFadeThroughExit()) using
    SizeTransform(clip = false) { _, _ -> YomuMotion.scheme.defaultSpatialSpec() }

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
        val mode = when {
            !state.available -> SyncMode.Unavailable
            !state.enabled -> SyncMode.Off
            else -> SyncMode.On
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = { syncStateHandoff() },
            label = "syncMode",
        ) { current ->
            when (current) {
                SyncMode.Unavailable -> YomuSettingContainer(position = YomuSettingPosition.Single) {
                    Text(
                        text = "Sync isn't available in this build.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SyncMode.Off -> SyncOff(state, onTurnOn, onOpenJoin)

                SyncMode.On -> YomuSettingList {
                    StatusRow(state.status)
                    YomuSettingRow(
                        title = "Show sync words",
                        subtitle = "Enter or scan them on another device to add it",
                        position = YomuSettingPosition.Middle,
                        enabled = !state.busy,
                        onClick = onShowWords,
                        leadingContent = { Icon(Icons.Rounded.Key, contentDescription = null) },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    // These two act (behind a confirmation) rather than navigate, so no chevron.
                    YomuSettingRow(
                        title = "Turn off on this device",
                        position = YomuSettingPosition.Middle,
                        enabled = !state.busy,
                        onClick = onTurnOff,
                        leadingContent = { Icon(Icons.Rounded.SyncDisabled, contentDescription = null) },
                    ) {}
                    YomuSettingRow(
                        title = "Delete synced data",
                        destructive = true,
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
}

@Composable
private fun SyncOff(state: SyncUiState, onTurnOn: () -> Unit, onOpenJoin: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
        if (state.status.phase == Phase.GroupDeleted) {
            YomuNotice(
                text = "Sync was turned off from another device. Your library here hasn't changed.",
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.SyncDisabled,
                tone = YomuNoticeTone.Accent,
            )
        }
        YomuSettingContainer(position = YomuSettingPosition.Single) {
            Text(
                text = "No account needed. Sync uses 12 words that only your devices know, and everything is encrypted before it leaves this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Stacked CTAs share one height; the label carries the busy state.
            YomuButton(
                text = if (state.busy) "Turning on…" else "Turn on sync",
                onClick = onTurnOn,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            YomuButton(
                text = "I have sync words",
                onClick = onOpenJoin,
                enabled = !state.busy,
                emphasis = YomuButtonEmphasis.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
            "Update Open Reader to keep syncing",
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
    val working = status.phase == Phase.Off || status.phase == Phase.Connecting || status.phase == Phase.Syncing
    YomuSettingRow(
        title = title,
        subtitle = subtitle,
        position = YomuSettingPosition.First,
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        },
    ) {
        if (working) YomuLoadingIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun WordsSheet(state: WordsSheetState?, onDismiss: () -> Unit) {
    YomuBottomSheet(visible = state != null, onDismiss = onDismiss) { dismiss ->
        val words = state?.words ?: return@YomuBottomSheet
        val context = LocalContext.current
        var copied by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md)) {
            YomuSheetHeader(
                title = if (state.justCreated) "Your sync words" else "Sync words",
                subtitle = if (state.justCreated) {
                    "Scan this code or enter these words on your other devices. Keep them somewhere safe: anyone with them can read your synced library, and they can't be recovered."
                } else {
                    "Scan this code or enter these words on another device. Anyone with them can read your synced library."
                },
                onClose = dismiss,
            )
            SyncQrCode(
                content = SyncQr.encode(words),
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            )
            WordGrid(words)
            Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
                YomuButton(
                    text = if (copied) "Copied" else "Copy phrase",
                    onClick = {
                        copySensitiveText(context, words.joinToString(" "))
                        copied = true
                    },
                    emphasis = YomuButtonEmphasis.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                YomuButton(
                    text = if (state.justCreated) "I've saved them" else "Done",
                    onClick = dismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WordGrid(words: List<String>) {
    // One container; numbered columns read top to bottom so the order is unambiguous when copied out.
    YomuSettingContainer(position = YomuSettingPosition.Single) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 360.dp) 3 else 2
            val perColumn = (words.size + columns - 1) / columns
            val indexStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
            Row(horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm), modifier = Modifier.fillMaxWidth()) {
                words.chunked(perColumn).forEachIndexed { column, list ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                        modifier = Modifier.weight(1f),
                    ) {
                        list.forEachIndexed { index, word ->
                            Row(horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
                                Text(
                                    text = "${column * perColumn + index + 1}",
                                    modifier = Modifier
                                        .width(24.dp)
                                        .alignByBaseline(),
                                    style = indexStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                                Text(
                                    text = word,
                                    modifier = Modifier.alignByBaseline(),
                                    style = MaterialTheme.typography.bodyLargeEmphasized,
                                )
                            }
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
    YomuBottomSheet(visible = state.visible, onDismiss = onDismiss) { dismiss ->
        val context = LocalContext.current
        // Keeps the last message on screen while its notice animates out.
        var shownError by remember { mutableStateOf("") }
        state.error?.let { shownError = it }
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md)) {
            YomuSheetHeader(
                title = "Add this device",
                subtitle = "On a device that already syncs, open Settings → Sync → Show sync words. Then scan its code or type the 12 words.",
                onClose = dismiss,
            )
            AnimatedContent(
                targetState = state.scanning,
                transitionSpec = { yomuContentSwap(forward = targetState) },
                label = "joinMode",
            ) { scanning ->
                if (scanning) {
                    Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
                        SyncQrScanner(onScanned = onScanned)
                        YomuButton(
                            text = "Type the words instead",
                            onClick = onCancelScan,
                            emphasis = YomuButtonEmphasis.Ghost,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
                        YomuButton(
                            text = "Scan code",
                            onClick = onStartScan,
                            emphasis = YomuButtonEmphasis.Secondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Field and suggestions share a column so the hidden chips leave no gap.
                        Column {
                            YomuTextField(
                                value = state.input,
                                onValueChange = onInput,
                                label = "Sync words",
                                singleLine = false,
                                minLines = 3,
                                supportingText = "${wordCount(state.input)} / ${SyncPhrase.WORD_COUNT} words",
                                isError = state.error != null,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { onJoin() }),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { readClipboardText(context)?.let(onInput) },
                                        shapes = IconButtonDefaults.shapes(),
                                    ) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste sync words")
                                    }
                                },
                            )
                            AnimatedVisibility(
                                visible = state.suggestions.isNotEmpty(),
                                enter = yomuPopupEnter(),
                                exit = yomuPopupExit(),
                            ) {
                                FlowRow(
                                    modifier = Modifier.padding(top = YomuTheme.space.xs),
                                    horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                                ) {
                                    state.suggestions.forEach { word ->
                                        SuggestionChip(
                                            onClick = { onPickSuggestion(word) },
                                            label = { Text(word) },
                                            shape = YomuShapeTokens.pill,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Notice and CTA share a column so the hidden notice leaves no gap above the button.
            Column {
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = yomuPopupEnter(),
                    exit = yomuPopupExit(),
                ) {
                    YomuNotice(
                        text = shownError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = YomuTheme.space.md),
                        icon = Icons.Rounded.ErrorOutline,
                        tone = YomuNoticeTone.Error,
                    )
                }
                YomuButton(
                    text = if (state.busy) "Connecting…" else "Start syncing",
                    onClick = onJoin,
                    enabled = state.valid && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun wordCount(input: String): Int = input.split(Regex("\\s+")).count { it.isNotEmpty() }

/** Copies [text] flagged as sensitive, so the system hides it from clipboard previews. */
private fun copySensitiveText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("Open Reader sync words", text)
    val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ClipDescription.EXTRA_IS_SENSITIVE
    } else {
        // Same key, readable by the clipboard UI on Android 12 and older vendor builds.
        "android.content.extra.IS_SENSITIVE"
    }
    clip.description.extras = PersistableBundle().apply { putBoolean(sensitiveKey, true) }
    clipboard.setPrimaryClip(clip)
}

private fun readClipboardText(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
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
