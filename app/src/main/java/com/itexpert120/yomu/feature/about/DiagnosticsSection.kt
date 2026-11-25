package com.itexpert120.yomu.feature.about

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.data.diagnostics.CrashLog
import com.itexpert120.yomu.data.reader.ReaderEngineChoice
import com.itexpert120.yomu.feature.settings.SettingsInset
import com.itexpert120.yomu.feature.settings.SettingsParagraph
import com.itexpert120.yomu.feature.settings.SettingsRow
import com.itexpert120.yomu.feature.settings.SettingsSectionLabel
import com.itexpert120.yomu.feature.settings.SettingsSubpage
import com.itexpert120.yomu.feature.settings.SettingsSwitchRow
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

/** About → Diagnostics: share or clear the local crash log, and the reading-engine switch. */
@Composable
internal fun DiagnosticsPage(onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val hasEntries by viewModel.hasEntries.collectAsStateWithLifecycle()
    val useReadium by viewModel.useReadium.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.shareRequests.collect { context.startActivity(it) }
    }
    DiagnosticsScreen(
        hasEntries = hasEntries,
        onShare = viewModel::onShare,
        onClear = viewModel::onClear,
        useReadium = useReadium,
        onUseReadiumChange = viewModel::onUseReadiumChange,
        onBack = onBack,
    )
}

@Composable
internal fun DiagnosticsScreen(
    hasEntries: Boolean,
    onShare: () -> Unit,
    onClear: () -> Unit,
    useReadium: Boolean,
    onUseReadiumChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val stack = YomuTheme.stack
    SettingsSubpage(title = "Diagnostics", onBack = onBack) {
        SettingsParagraph(
            text = "When the app crashes or stops responding, a short log is written on this phone. " +
                "Nothing is sent unless you share it with the developer.",
            modifier = Modifier.padding(top = 10.dp),
        )
        SettingsInset(
            icon = if (hasEntries) StackIcons.Description else StackIcons.CheckCircle,
            modifier = Modifier.padding(top = 18.dp),
        ) {
            if (hasEntries) {
                Text("Crash log has entries", style = StackType.Label.copy(fontSize = 14.sp), color = stack.ink)
                Text(
                    text = "Share it to help fix what went wrong.",
                    style = StackType.Caption,
                    color = stack.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text("Nothing logged. No crashes so far.", style = StackType.BodySmall, color = stack.body)
            }
        }
        SettingsRow(
            title = "Share crash log",
            icon = StackIcons.Share,
            enabled = hasEntries,
            onClick = onShare,
            modifier = Modifier.padding(top = 14.dp),
        )
        SettingsRow(
            title = "Clear crash log",
            icon = StackIcons.Delete,
            enabled = hasEntries,
            destructive = hasEntries,
            divider = false,
            onClick = onClear,
        )
        SettingsSectionLabel("Reading engine", top = 22.dp)
        // The current reading engine is still being built out; the old one stays one switch away.
        SettingsSwitchRow(
            title = "Use previous reading engine",
            subtitle = "Switch back to Readium, the previous engine. Applies to the next book you open.",
            icon = StackIcons.History,
            checked = useReadium,
            onCheckedChange = onUseReadiumChange,
            divider = false,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DiagnosticsPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Light) {
        DiagnosticsScreen(hasEntries = true, onShare = {}, onClear = {}, useReadium = false, onUseReadiumChange = {}, onBack = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DiagnosticsEmptyDarkPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Dark) {
        DiagnosticsScreen(hasEntries = false, onShare = {}, onClear = {}, useReadium = true, onUseReadiumChange = {}, onBack = {})
    }
}
