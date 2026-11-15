package com.itexpert120.yomu.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.data.sync.JoinResult
import com.itexpert120.yomu.data.sync.SyncRepository
import com.itexpert120.yomu.data.sync.SyncStatus
import com.itexpert120.yomu.data.sync.crypto.SyncPhrase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The "enter words from another device" sheet. */
data class JoinFlowState(
    val visible: Boolean = false,
    val input: String = "",
    val scanning: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val suggestions: List<String> = emptyList(),
    val valid: Boolean = false,
)

/** What the words sheet shows: the phrase, and whether it was just created (so: "write these down"). */
data class WordsSheetState(val words: List<String>, val justCreated: Boolean)

data class SyncUiState(
    val available: Boolean = true,
    val enabled: Boolean = false,
    val status: SyncStatus = SyncStatus(),
    val busy: Boolean = false,
    val error: String? = null,
    val words: WordsSheetState? = null,
    val join: JoinFlowState = JoinFlowState(),
    val confirmDelete: Boolean = false,
    val confirmTurnOff: Boolean = false,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val sync: SyncRepository,
) : ViewModel() {
    private val local = MutableStateFlow(SyncUiState(available = sync.available))

    val state: StateFlow<SyncUiState> = combine(local, sync.enabled, sync.status) { ui, enabled, status ->
        ui.copy(enabled = enabled, status = status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        viewModelScope.launch { sync.prepare() }
    }

    fun onTurnOn() = busy {
        sync.turnOn()
            .onSuccess { phrase -> local.update { it.copy(words = WordsSheetState(phrase.words, justCreated = true)) } }
            .onFailure { local.update { it.copy(error = "Couldn't turn on sync. Check your connection and try again.") } }
    }

    fun onShowWords() = busy {
        sync.phrase()?.let { phrase -> local.update { it.copy(words = WordsSheetState(phrase.words, justCreated = false)) } }
    }

    fun onDismissWords() = local.update { it.copy(words = null) }

    fun onDismissError() = local.update { it.copy(error = null) }

    fun onOpenJoin() = local.update { it.copy(join = JoinFlowState(visible = true)) }

    fun onDismissJoin() = local.update { it.copy(join = JoinFlowState()) }

    fun onJoinInput(input: String) = local.update { it.copy(join = validate(it.join.copy(input = input))) }

    /** Replaces the word being typed with [word] and moves on to the next one. */
    fun onPickSuggestion(word: String) = local.update { state ->
        val words = state.join.input.trimStart().split(Regex("\\s+")).dropLast(1) + word
        state.copy(join = validate(state.join.copy(input = words.joinToString(" ") + " ")))
    }

    fun onStartScan() = local.update { it.copy(join = it.join.copy(scanning = true, error = null)) }

    fun onCancelScan() = local.update { it.copy(join = it.join.copy(scanning = false)) }

    /** A scanned QR: only Yomu sync codes are accepted. Joins straight away when the words check out. */
    fun onScanned(raw: String) {
        val words = SyncQr.decode(raw)
        if (words == null) {
            local.update { it.copy(join = it.join.copy(error = "That isn't a Yomu sync code.")) }
            return
        }
        local.update { it.copy(join = validate(it.join.copy(input = words, scanning = false))) }
        if (local.value.join.valid) onJoin()
    }

    fun onJoin() {
        val join = local.value.join
        val phrase = (sync.parse(join.input) as? SyncPhrase.ParseResult.Valid)?.phrase ?: return
        if (join.busy) return
        viewModelScope.launch {
            local.update { it.copy(join = it.join.copy(busy = true, error = null)) }
            val error = when (val result = sync.join(phrase)) {
                JoinResult.Joined -> null
                JoinResult.NotFound -> "No synced library uses these words. Check them against your other device."
                JoinResult.UpdateRequired -> "Update Yomu to sync with these words."
                is JoinResult.Failed -> "Couldn't connect. Check your connection and try again."
            }
            local.update { state ->
                if (error == null) state.copy(join = JoinFlowState()) else state.copy(join = state.join.copy(busy = false, error = error))
            }
        }
    }

    fun onRequestTurnOff() = local.update { it.copy(confirmTurnOff = true) }

    fun onConfirmTurnOff() = busy {
        local.update { it.copy(confirmTurnOff = false) }
        sync.turnOff()
    }

    fun onRequestDelete() = local.update { it.copy(confirmDelete = true) }

    fun onDismissConfirm() = local.update { it.copy(confirmDelete = false, confirmTurnOff = false) }

    fun onConfirmDelete() = busy {
        local.update { it.copy(confirmDelete = false) }
        sync.deleteEverywhere().onFailure {
            local.update { it.copy(error = "Couldn't delete the synced copy. Check your connection and try again.") }
        }
    }

    private fun validate(join: JoinFlowState): JoinFlowState {
        val typing = join.input.isNotEmpty() && !join.input.last().isWhitespace()
        val suggestions = if (typing) sync.suggestions(join.input.trim().split(Regex("\\s+")).last()) else emptyList()
        val (valid, error) = when (val parsed = sync.parse(join.input)) {
            is SyncPhrase.ParseResult.Valid -> true to null
            // Only complain once all 12 words are in.
            is SyncPhrase.ParseResult.WrongWordCount ->
                false to
                    (if (parsed.count > SyncPhrase.WORD_COUNT) "That's more than ${SyncPhrase.WORD_COUNT} words." else null)
            is SyncPhrase.ParseResult.UnknownWords ->
                false to
                    (if (typing && parsed.positions == listOf(wordCount(join.input))) null else unknownMessage(parsed.positions))
            SyncPhrase.ParseResult.BadChecksum -> false to "These words don't add up. Look for a typo."
        }
        return join.copy(valid = valid, error = error, suggestions = suggestions.takeUnless { valid }.orEmpty())
    }

    private fun wordCount(input: String) = input.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    private fun unknownMessage(positions: List<Int>) = if (positions.size == 1) {
        "Word ${positions.single()} isn't a sync word."
    } else {
        "Words ${positions.joinToString(", ")} aren't sync words."
    }

    private fun busy(block: suspend () -> Unit) {
        if (local.value.busy) return
        viewModelScope.launch {
            local.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }
}

/** QR payload: a prefix so unrelated QR codes are rejected, then the 12 words. */
internal object SyncQr {
    private const val PREFIX = "yomu-sync:v1:"

    fun encode(words: List<String>) = PREFIX + words.joinToString(" ")

    fun decode(raw: String): String? = raw.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)
}
