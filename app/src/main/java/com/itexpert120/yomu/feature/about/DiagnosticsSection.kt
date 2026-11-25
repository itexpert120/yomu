package com.itexpert120.yomu.feature.about

import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuTogglePill
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.data.diagnostics.CrashLog
import com.itexpert120.yomu.data.reader.ReaderEngineChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val crashLog: CrashLog,
    private val engineChoice: ReaderEngineChoice,
) : ViewModel() {
    val hasEntries: StateFlow<Boolean> = crashLog.hasEntries

    val useReadium: StateFlow<Boolean> = engineChoice.useReadium

    fun onUseReadiumChange(value: Boolean) = engineChoice.setUseReadium(value)

    private val _shareRequests = Channel<Intent>(Channel.CONFLATED)
    val shareRequests: Flow<Intent> = _shareRequests.receiveAsFlow()

    fun onShare() {
        viewModelScope.launch { crashLog.shareIntent()?.let { _shareRequests.send(it) } }
    }

    fun onClear() {
        viewModelScope.launch { crashLog.clear() }
    }
}

/** Stateful slot for the About page: share or clear the local crash/diagnostics log. */
@Composable
fun DiagnosticsSection(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val hasEntries by viewModel.hasEntries.collectAsStateWithLifecycle()
    val useReadium by viewModel.useReadium.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.shareRequests.collect { context.startActivity(it) }
    }
    DiagnosticsGroup(
        hasEntries = hasEntries,
        onShare = viewModel::onShare,
        onClear = viewModel::onClear,
        useReadium = useReadium,
        onUseReadiumChange = viewModel::onUseReadiumChange,
    )
}

@Composable
internal fun DiagnosticsGroup(
    hasEntries: Boolean,
    onShare: () -> Unit,
    onClear: () -> Unit,
    useReadium: Boolean = false,
    onUseReadiumChange: (Boolean) -> Unit = {},
) {
    YomuSettingGroup(
        title = "Diagnostics",
        subtitle = "Crashes and unexpected exits are logged on this device only. " +
            "Nothing is sent unless you share the log.",
    ) {
        YomuSettingRow(
            title = "Share crash log",
            subtitle = if (hasEntries) "Send it to the developer to help fix a crash" else "Nothing logged yet",
            enabled = hasEntries,
            position = YomuSettingPosition.First,
            onClick = onShare,
        ) {
            Icon(
                imageVector = StackIcons.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        YomuSettingRow(
            title = "Clear crash log",
            enabled = hasEntries,
            destructive = true,
            position = YomuSettingPosition.Middle,
            onClick = onClear,
        ) {
            Icon(
                imageVector = StackIcons.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
        // The new reading engine is still being built out; the old one stays one switch away.
        YomuSettingRow(
            title = "Use previous reading engine",
            subtitle = "Readium, which has highlights, search and the selection menu. " +
                "Applies to the next book you open.",
            position = YomuSettingPosition.Last,
            role = Role.Switch,
            onClick = { onUseReadiumChange(!useReadium) },
        ) {
            YomuTogglePill(checked = useReadium, onCheckedChange = onUseReadiumChange)
        }
    }
}
