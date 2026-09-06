package com.itexpert120.yomu.feature.bookedit

import com.itexpert120.yomu.core.model.Book
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class BookEditor(
    private val scope: CoroutineScope,
    private val cleanupScope: CoroutineScope,
    load: suspend () -> Book?,
    private val commit: suspend (EditBookUiState) -> Unit,
    private val deleteCover: suspend (String) -> Unit,
) {
    private val mutableState = MutableStateFlow(EditBookUiState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var closed = false
    private var originalCover: String? = null
    private val ownedFiles = mutableSetOf<String>()

    init {
        scope.launch {
            try {
                val book = load()
                originalCover = book?.coverImagePath
                mutableState.value = if (book == null) {
                    EditBookUiState(loaded = true, error = "This book is no longer available.")
                } else {
                    EditBookUiState(
                        loaded = true,
                        available = true,
                        title = book.title,
                        subtitle = book.subtitle.orEmpty(),
                        author = book.author,
                        description = book.description.orEmpty(),
                        coverImagePath = book.coverImagePath,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = EditBookUiState(loaded = true, error = "Couldn't load book details.")
            }
        }
    }

    fun edit(change: (EditBookUiState) -> EditBookUiState) {
        if (!closed && state.value.editable) mutableState.update(change)
    }

    fun pickCover(copy: suspend () -> String) = operation {
        val path = copy()
        ownedFiles += path
        mutableState.update { it.copy(coverImagePath = path) }
        cleanUnused(except = path)
    }

    fun save() = operation {
        val draft = state.value
        commit(draft)
        // Commit and ownership transfer cannot be separated by navigation cancellation.
        ownedFiles.remove(draft.coverImagePath)
        originalCover?.takeIf { it != draft.coverImagePath }?.let { ownedFiles += it }
        originalCover = draft.coverImagePath
        cleanUnused(except = draft.coverImagePath)
        mutableState.update { it.copy(saved = true) }
    }

    private fun operation(block: suspend () -> Unit) = scope.launch {
        mutex.withLock {
            if (closed || !state.value.editable) return@withLock
            mutableState.update { it.copy(busy = true, error = null) }
            withContext(NonCancellable) {
                try {
                    block()
                } catch (_: Exception) {
                    mutableState.update { it.copy(error = "Couldn't save this change. Your draft is available to retry.") }
                } finally {
                    mutableState.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun close() {
        closed = true
        cleanupScope.launch { mutex.withLock { cleanUnused(except = originalCover) } }
    }

    private suspend fun cleanUnused(except: String?) {
        ownedFiles.toList().filter { it != except }.forEach { path ->
            runCatching { deleteCover(path) }.onSuccess { ownedFiles.remove(path) }
        }
    }
}
