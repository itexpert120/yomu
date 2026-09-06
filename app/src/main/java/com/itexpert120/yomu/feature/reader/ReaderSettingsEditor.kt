package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.core.model.ReaderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal data class SettingsEditState(
    val settings: ReaderSettings = ReaderSettings(),
    val pending: Boolean = false,
    val error: String? = null,
)

/** Serializes full settings snapshots while retaining the newest intent over old store echoes. */
internal class ReaderSettingsEditor(
    private val scope: CoroutineScope,
    private val save: suspend (ReaderSettings) -> Unit,
    private val persistReset: suspend () -> ReaderSettings,
) {
    val state = MutableStateFlow(SettingsEditState())
    private data class Intent(val settings: ReaderSettings?)
    private var intent: Intent? = null
    private var job: Job? = null
    private var observed: ReaderSettings? = null
    private var awaitingEcho: ReaderSettings? = null

    fun observe(settings: ReaderSettings) {
        observed = settings
        if (intent != null) return
        if (awaitingEcho != null && awaitingEcho != settings) return
        awaitingEcho = null
        state.value = state.value.copy(settings = settings)
    }

    fun edit(settings: ReaderSettings) {
        intent = Intent(settings)
        state.value = SettingsEditState(settings, pending = true)
        retry()
    }

    fun reset() {
        intent = Intent(null)
        state.value = state.value.copy(pending = true, error = null)
        retry()
    }

    fun retry() {
        if (job?.isActive == true || intent == null) return
        state.value = state.value.copy(error = null)
        job = scope.launch {
            while (true) {
                val target = intent ?: break
                try {
                    val resolved = target.settings?.also { save(it) } ?: persistReset()
                    if (intent === target) {
                        intent = null
                        awaitingEcho = resolved.takeUnless { it == observed }
                        state.value = SettingsEditState(resolved)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (intent !== target) continue
                    state.value = state.value.copy(error = "Settings weren't saved. Retry to keep your changes.")
                    break
                }
            }
        }
    }
}
