package com.itexpert120.yomu.feature.sync

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuDialog
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTextField
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughEnter
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughExit
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.data.sync.SyncStatus
import com.itexpert120.yomu.data.sync.SyncStatus.Phase
import com.itexpert120.yomu.data.sync.crypto.SyncPhrase
import com.itexpert120.yomu.feature.settings.SettingsCaptionStyle
import com.itexpert120.yomu.feature.settings.SettingsInset
import com.itexpert120.yomu.feature.settings.SettingsPageTitleStyle
import com.itexpert120.yomu.feature.settings.SettingsParagraph
import com.itexpert120.yomu.feature.settings.SettingsRow
import com.itexpert120.yomu.feature.settings.SettingsSectionLabel
import com.itexpert120.yomu.feature.settings.SettingsTopRow

/**
 * Settings → Sync as its own page: on (status, words, turn off, delete), off (what sync is, turn
 * on, join) or unavailable, plus the words / join sheets and the confirmations. [onBack] null drops
 * the back row and shows a pane title instead (the tablet settings detail pane).
 */
@Composable
fun SyncRoute(onBack: (() -> Unit)?, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SyncScreen(
        state = state,
        onBack = onBack,
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
            text = "Your books, progress and notes stay here. Other devices keep syncing with each other. You can join again with your sync words.",
            confirmLabel = "Turn off",
            onConfirm = viewModel::onConfirmTurnOff,
            onDismiss = viewModel::onDismissConfirm,
        )
    }
    if (state.confirmDelete) {
        YomuDialog(
            title = "Delete synced data?",
            text = "This removes your encrypted library data from the sync server and stops sync on every device. Books and notes already on your devices stay. This can't be undone.",
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
            confirmLabel = "OK",
            dismissLabel = null,
            onConfirm = viewModel::onDismissError,
            onDismiss = viewModel::onDismissError,
        )
    }
}

private enum class SyncMode { Unavailable, Off, On }

private val SyncUiState.mode: SyncMode
    get() = when {
        !available -> SyncMode.Unavailable
        !enabled -> SyncMode.Off
        else -> SyncMode.On
    }

/** What the sync status reads as, in one place for the Settings card and the Sync page. */
internal data class SyncSummary(val title: String, val detail: String, val icon: ImageVector)

@Composable
internal fun syncSummary(state: SyncUiState): SyncSummary = when (state.mode) {
    SyncMode.Unavailable -> SyncSummary("Sync isn't available", "Not included in this build", StackIcons.SyncDisabled)
    SyncMode.Off -> if (state.status.phase == Phase.GroupDeleted) {
        SyncSummary("Sync is off", "Turned off on another device", StackIcons.SyncDisabled)
    } else {
        SyncSummary("Sync is off", "Keep your reading the same on every device", StackIcons.Sync)
    }
    SyncMode.On -> statusSummary(state.status)
}

@Composable
private fun statusSummary(status: SyncStatus): SyncSummary = when (status.phase) {
    Phase.Off, Phase.Connecting -> SyncSummary("Connecting…", "Opening a secure connection.", StackIcons.CloudSync)
    Phase.Syncing -> SyncSummary("Syncing…", "Sending and receiving changes.", StackIcons.Sync)
    Phase.UpToDate -> SyncSummary("Up to date", lastSyncedText(status.lastSyncedAt), StackIcons.CloudDone)
    Phase.Offline -> SyncSummary(
        "Offline",
        "Changes are saved and will sync when you're back online.",
        StackIcons.CloudOff,
    )
    Phase.UpdateRequired -> SyncSummary(
        "Update needed",
        "Update Open Reader to keep syncing. Nothing is lost meanwhile.",
        StackIcons.SystemUpdate,
    )
    Phase.ServerOutdated -> SyncSummary(
        "Sync server unavailable",
        "Your changes are saved here and will sync once it's back.",
        StackIcons.Dns,
    )
    Phase.GroupDeleted -> SyncSummary(
        "Turned off on another device",
        "Turn it on again here with your sync words.",
        StackIcons.SyncDisabled,
    )
}

private fun lastSyncedText(at: Long?): String {
    if (at == null) return "Everything on this device is synced."
    val now = System.currentTimeMillis()
    if (now - at < DateUtils.MINUTE_IN_MILLIS) return "Synced just now."
    return "Synced ${DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS)}."
}

/** The sync status at the top of Settings: an ink badge, what sync is doing, and a way in. */
@Composable
internal fun SyncStatusCard(state: SyncUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val stack = YomuTheme.stack
    val summary = syncSummary(state)
    val title = if (state.mode == SyncMode.On) "Sync is on" else summary.title
    // When on, the card's second line is the live status ("Up to date", "Offline", …).
    val detail = if (state.mode == SyncMode.On) summary.title else summary.detail
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(stack.sheet)
            .yomuPressable(onClick = onClick, shape = RoundedCornerShape(24.dp))
            .semantics(mergeDescendants = true) {}
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(stack.ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(StackIcons.Sync, contentDescription = null, tint = stack.onInk, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = StackType.Heading, color = stack.ink)
            Text(
                text = detail,
                style = StackType.Label.copy(fontWeight = FontWeight.Normal),
                color = stack.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(StackIcons.KeyboardArrowRight, contentDescription = null, tint = stack.faint)
    }
}

@Composable
fun SyncScreen(
    state: SyncUiState,
    onBack: (() -> Unit)?,
    onTurnOn: () -> Unit,
    onOpenJoin: () -> Unit,
    onShowWords: () -> Unit,
    onTurnOff: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(stack.sheet)
            .then(if (onBack != null) Modifier.statusBarsPadding() else Modifier),
    ) {
        if (onBack != null) SettingsTopRow(title = "Sync", onBack = onBack)
        AnimatedContent(
            targetState = state.mode,
            transitionSpec = { yomuFadeThroughEnter() togetherWith yomuFadeThroughExit() },
            label = "syncMode",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) { mode ->
            Column(
                modifier = Modifier
                    .widthIn(max = YomuReadableMaxWidth + 48.dp)
                    .fillMaxSize()
                    .padding(horizontal = if (onBack != null) 24.dp else 32.dp)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom)),
            ) {
                if (onBack == null) {
                    Text(
                        text = "Sync",
                        style = StackType.Title,
                        color = stack.ink,
                        modifier = Modifier
                            .padding(top = 28.dp)
                            .semantics { heading() },
                    )
                }
                when (mode) {
                    SyncMode.On -> SyncOn(state, onShowWords, onTurnOff, onDelete)
                    SyncMode.Off, SyncMode.Unavailable -> SyncOff(state, onTurnOn, onOpenJoin)
                }
            }
        }
    }
}

@Composable
private fun SyncOn(
    state: SyncUiState,
    onShowWords: () -> Unit,
    onTurnOff: () -> Unit,
    onDelete: () -> Unit,
) {
    val stack = YomuTheme.stack
    val summary = statusSummary(state.status)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .background(stack.raised, RoundedCornerShape(24.dp))
                .padding(18.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(stack.ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(summary.icon, contentDescription = null, tint = stack.onInk, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(text = summary.title, style = SettingsPageTitleStyle, color = stack.ink)
                Text(
                    text = summary.detail,
                    style = StackType.Label.copy(fontWeight = FontWeight.Normal),
                    color = stack.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        SettingsSectionLabel("This device", top = 22.dp)
        SettingsRow(
            title = "Show sync words",
            subtitle = "To add another device",
            icon = StackIcons.Key,
            chevron = true,
            enabled = !state.busy,
            onClick = onShowWords,
        )
        SettingsSectionLabel("Stop syncing", top = 22.dp)
        // These two act behind a confirmation rather than navigate, so no chevron.
        SettingsRow(
            title = "Turn off sync on this device",
            subtitle = "Your books stay here",
            icon = StackIcons.SyncDisabled,
            enabled = !state.busy,
            onClick = onTurnOff,
        )
        SettingsRow(
            title = "Delete synced data",
            subtitle = "Removes it from the server for every device",
            icon = StackIcons.DeleteForever,
            destructive = true,
            divider = false,
            enabled = !state.busy,
            onClick = onDelete,
        )
    }
}

@Composable
private fun SyncOff(state: SyncUiState, onTurnOn: () -> Unit, onOpenJoin: () -> Unit) {
    val stack = YomuTheme.stack
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.status.phase == Phase.GroupDeleted) {
                SettingsInset(icon = StackIcons.SyncDisabled, modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "Sync was turned off on another device. Your books here are untouched.",
                        style = StackType.BodySmall.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
                        color = stack.body,
                    )
                }
            }
            Text(
                text = "Your reading, on every device",
                style = StackType.Title.copy(lineHeight = 30.sp),
                color = stack.ink,
                modifier = Modifier
                    .padding(top = 28.dp)
                    .semantics { heading() },
            )
            Column(Modifier.padding(top = 14.dp)) {
                SyncPoint(
                    StackIcons.Bookmarks,
                    "Progress, bookmarks, highlights, shelves, reading time and settings stay the same everywhere. Book files stay on each device.",
                )
                SyncPoint(StackIcons.Key, "No account. Twelve words are the only key, so keep them somewhere safe.")
                SyncPoint(StackIcons.Lock, "Everything is encrypted on this phone first. The server only ever sees scrambled data.")
            }
            if (state.mode == SyncMode.Unavailable) {
                SettingsInset(icon = StackIcons.Info, modifier = Modifier.padding(top = 14.dp)) {
                    Text(text = "Sync isn't available in this build.", style = StackType.BodySmall, color = stack.body)
                }
            }
        }
        if (state.mode == SyncMode.Off) {
            Column(
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Both CTAs share one height; the label carries the busy state.
                YomuButton(
                    text = if (state.busy) "Turning on…" else "Turn on sync",
                    onClick = onTurnOn,
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                )
                YomuButton(
                    text = "I have sync words",
                    onClick = onOpenJoin,
                    enabled = !state.busy,
                    emphasis = YomuButtonEmphasis.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun SyncPoint(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = YomuTheme.stack.muted)
        SettingsParagraph(text = text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WordsSheet(state: WordsSheetState?, onDismiss: () -> Unit) {
    YomuBottomSheet(visible = state != null, onDismiss = onDismiss) { dismiss ->
        val words = state?.words ?: return@YomuBottomSheet
        val context = LocalContext.current
        val stack = YomuTheme.stack
        var copied by remember { mutableStateOf(false) }
        Column {
            YomuSheetHeader(
                title = if (state.justCreated) "Your sync words" else "Sync words",
                onClose = dismiss,
            )
            Text(
                text = if (state.justCreated) {
                    "Write these down or keep them in a password manager. Anyone with them can open your synced library, and they can't be recovered."
                } else {
                    "Anyone with these words can open your synced library."
                },
                style = StackType.BodySmall.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
                color = stack.body,
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SyncQrCode(
                    content = SyncQr.encode(words),
                    modifier = Modifier
                        .size(136.dp)
                        .semantics { contentDescription = "QR code with your sync words" },
                )
                Text(
                    text = "On your other device choose I have sync words, then scan this code or type the words.",
                    style = StackType.Label.copy(fontWeight = FontWeight.Normal, lineHeight = 18.sp),
                    color = stack.muted,
                    modifier = Modifier.weight(1f),
                )
            }
            WordGrid(words, Modifier.padding(top = 12.dp))
            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                YomuButton(
                    text = if (copied) "Copied" else "Copy",
                    onClick = {
                        copySensitiveText(context, words.joinToString(" "))
                        copied = true
                    },
                    icon = StackIcons.ContentCopy,
                    emphasis = YomuButtonEmphasis.Secondary,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                )
                YomuButton(
                    text = if (state.justCreated) "I've saved them" else "Done",
                    onClick = dismiss,
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 56.dp),
                )
            }
        }
    }
}

/** Two numbered columns that read top to bottom (1–6, then 7–12), so the order survives copying out. */
@Composable
private fun WordGrid(words: List<String>, modifier: Modifier = Modifier) {
    val stack = YomuTheme.stack
    val perColumn = (words.size + 1) / 2
    val indexStyle = StackType.Caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        words.chunked(perColumn).forEachIndexed { column, list ->
            Column(Modifier.weight(1f)) {
                list.forEachIndexed { index, word ->
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "${column * perColumn + index + 1}",
                            modifier = Modifier
                                .width(20.dp)
                                .alignByBaseline(),
                            style = indexStyle,
                            color = stack.faint,
                        )
                        Text(
                            text = word,
                            modifier = Modifier.alignByBaseline(),
                            style = StackType.Body,
                            color = stack.ink,
                        )
                    }
                    HorizontalDivider(color = stack.rule)
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
        val stack = YomuTheme.stack
        // Keeps the last message on screen while it animates out.
        var shownError by remember { mutableStateOf("") }
        state.error?.let { shownError = it }
        Column {
            YomuSheetHeader(title = "Add this device", onClose = dismiss)
            Text(
                text = "On a device that already syncs, open Settings → Sync → Show sync words. Then scan its code or type the 12 words.",
                style = StackType.BodySmall.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
                color = stack.body,
            )
            AnimatedContent(
                targetState = state.scanning,
                transitionSpec = { yomuContentSwap(forward = targetState) },
                label = "joinMode",
                modifier = Modifier.padding(top = 16.dp),
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
                    Column {
                        YomuButton(
                            text = "Scan code",
                            onClick = onStartScan,
                            icon = StackIcons.QrCodeScanner,
                            emphasis = YomuButtonEmphasis.Secondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                        )
                        OrTypeDivider(Modifier.padding(top = 18.dp, bottom = 12.dp))
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
                                    Icon(StackIcons.ContentPaste, contentDescription = "Paste sync words")
                                }
                            },
                        )
                        AnimatedVisibility(
                            visible = state.error != null,
                            enter = yomuPopupEnter(),
                            exit = yomuPopupExit(),
                        ) {
                            Text(
                                text = shownError,
                                style = SettingsCaptionStyle,
                                color = stack.danger,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        AnimatedVisibility(
                            visible = state.suggestions.isNotEmpty(),
                            enter = yomuPopupEnter(),
                            exit = yomuPopupExit(),
                        ) {
                            FlowRow(
                                modifier = Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.suggestions.forEach { word ->
                                    SuggestionPill(word = word, onClick = { onPickSuggestion(word) })
                                }
                            }
                        }
                    }
                }
            }
            YomuButton(
                text = if (state.busy) "Connecting…" else "Start syncing",
                onClick = onJoin,
                enabled = state.valid && !state.busy,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun OrTypeDivider(modifier: Modifier = Modifier) {
    val stack = YomuTheme.stack
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = stack.rule)
        Text("or type the words", style = SettingsCaptionStyle, color = stack.faint, textAlign = TextAlign.Center)
        HorizontalDivider(Modifier.weight(1f), color = stack.rule)
    }
}

/** A suggested sync word: an outlined pill that replaces the word being typed. */
@Composable
private fun SuggestionPill(word: String, onClick: () -> Unit) {
    val stack = YomuTheme.stack
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = stack.sheet,
        contentColor = stack.ink,
        border = BorderStroke(1.5.dp, stack.track),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(word, style = StackType.BodySmall)
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

@Composable
private fun SyncPreview(state: SyncUiState, mode: YomuThemeMode) {
    YomuDesignTheme(themeMode = mode) {
        SyncScreen(state, onBack = {}, onTurnOn = {}, onOpenJoin = {}, onShowWords = {}, onTurnOff = {}, onDelete = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SyncOffPreview() = SyncPreview(SyncUiState(status = SyncStatus(Phase.GroupDeleted)), YomuThemeMode.Light)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SyncOffDarkPreview() = SyncPreview(SyncUiState(), YomuThemeMode.Dark)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SyncOnPreview() = SyncPreview(SyncUiState(enabled = true, status = SyncStatus(Phase.UpToDate)), YomuThemeMode.Light)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SyncOnDarkPreview() = SyncPreview(SyncUiState(enabled = true, status = SyncStatus(Phase.UpdateRequired)), YomuThemeMode.Dark)

@Preview(widthDp = 390)
@Composable
private fun WordGridPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Light) {
        Column(Modifier.background(YomuTheme.stack.sheet).padding(24.dp)) {
            Spacer(Modifier.height(4.dp))
            WordGrid(List(12) { "word${it + 1}" })
        }
    }
}
