package com.itexpert120.yomu.feature.about

import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.designsystem.YomuSettingGroup
import com.itexpert120.yomu.core.designsystem.YomuSettingPosition
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.data.diagnostics.CrashLog
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
) : ViewModel() {
    val hasEntries: StateFlow<Boolean> = crashLog.hasEntries

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
    val hasEntries by viewModel.hasEntries.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.shareRequests.collect { context.startActivity(it) }
    }
    DiagnosticsGroup(
        hasEntries = hasEntries,
        onShare = viewModel::onShare,
        onClear = viewModel::onClear,
    )
}

@Composable
internal fun DiagnosticsGroup(
    hasEntries: Boolean,
    onShare: () -> Unit,
    onClear: () -> Unit,
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
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        YomuSettingRow(
            title = "Clear crash log",
            enabled = hasEntries,
            destructive = true,
            position = YomuSettingPosition.Last,
            onClick = onClear,
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
