package com.itexpert120.yomu.feature.annotations

import android.content.Intent
import android.net.Uri
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.data.annotations.AnnotationFile
import com.itexpert120.yomu.data.annotations.AnnotationFormat
import com.itexpert120.yomu.data.annotations.AnnotationImportCheck
import com.itexpert120.yomu.data.annotations.AnnotationImportPreview
import com.itexpert120.yomu.data.annotations.AnnotationImportResult
import com.itexpert120.yomu.data.annotations.BookAnnotations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the person can do with a book's highlights and notes as files (Book Details ⋯, Browse → Highlights). */
sealed interface AnnotationTransferAction {
    /** The menu opened: find out whether the EPUB carries Calibre highlights (offered only then). */
    data object MenuOpened : AnnotationTransferAction

    /** "Export highlights & notes": choose a format. */
    data object Export : AnnotationTransferAction
    data class ExportAs(val format: AnnotationFormat) : AnnotationTransferAction
    data object CancelExport : AnnotationTransferAction

    /** "Import highlights…": the screen opens the system file picker (see [rememberAnnotationImportPicker]). */
    data object PickFile : AnnotationTransferAction

    /** A file picked for "Import highlights…". */
    data class CheckImport(val uri: Uri) : AnnotationTransferAction
    data object ImportCalibre : AnnotationTransferAction
    data object ConfirmImport : AnnotationTransferAction
    data object CancelImport : AnnotationTransferAction
    data class DismissNotice(val key: Long) : AnnotationTransferAction
}

/** The import-preview dialog: what the file holds and what applying it would change. */
data class AnnotationImportUi(
    val preview: AnnotationImportPreview,
    // Highlights stored inside the book by Calibre, rather than an Open Reader file.
    val fromCalibre: Boolean,
)

data class AnnotationNotice(val key: Long, val text: String)

data class AnnotationTransferState(
    val choosingFormat: Boolean = false,
    // Reading a file, building an export or applying an import.
    val working: Boolean = false,
    val importing: AnnotationImportUi? = null,
    val calibreAvailable: Boolean = false,
    val notice: AnnotationNotice? = null,
)

/** The book-annotation operations the transfer flow needs; [BookAnnotations] in the app. */
interface AnnotationTransferPort {
    suspend fun shareIntent(bookId: BookId, format: AnnotationFormat): Intent?
    suspend fun checkImport(bookId: BookId, uri: Uri): AnnotationImportCheck
    suspend fun checkCalibreImport(bookId: BookId): AnnotationImportCheck?
    suspend fun applyImport(bookId: BookId, file: AnnotationFile): AnnotationImportResult?
}

class BookAnnotationsTransferPort @Inject constructor(private val annotations: BookAnnotations) : AnnotationTransferPort {
    override suspend fun shareIntent(bookId: BookId, format: AnnotationFormat) = annotations.shareIntent(bookId, format)
    override suspend fun checkImport(bookId: BookId, uri: Uri) = annotations.checkImport(bookId, uri)
    override suspend fun checkCalibreImport(bookId: BookId) = annotations.checkCalibreImport(bookId)
    override suspend fun applyImport(bookId: BookId, file: AnnotationFile) = annotations.applyImport(bookId, file)
}

/**
 * Export and import of one book's highlights, notes and bookmarks, owned by a screen's ViewModel
 * (it runs in [scope]). Exports leave as a share-sheet [shares] intent; imports are checked first
 * and only applied once the preview is confirmed; results end as a [AnnotationTransferState.notice].
 */
class AnnotationTransfer(
    private val bookId: BookId,
    private val port: AnnotationTransferPort,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AnnotationTransferState())
    val state: StateFlow<AnnotationTransferState> = _state.asStateFlow()

    private val shareChannel = Channel<Intent>(Channel.BUFFERED)

    /** Ready share-sheet choosers, to start from the screen. */
    val shares: Flow<Intent> = shareChannel.receiveAsFlow()

    private var pendingFile: AnnotationFile? = null
    private var noticeKey = 0L
    private var job: Job? = null
    private var calibreChecked = false

    fun onAction(action: AnnotationTransferAction) {
        when (action) {
            AnnotationTransferAction.MenuOpened -> checkCalibre()
            AnnotationTransferAction.Export -> _state.update { it.copy(choosingFormat = true) }
            AnnotationTransferAction.CancelExport -> _state.update { it.copy(choosingFormat = false) }
            AnnotationTransferAction.PickFile -> Unit
            is AnnotationTransferAction.ExportAs -> export(action.format)
            is AnnotationTransferAction.CheckImport -> check(fromCalibre = false) { port.checkImport(bookId, action.uri) }
            AnnotationTransferAction.ImportCalibre -> check(fromCalibre = true) { port.checkCalibreImport(bookId) }
            AnnotationTransferAction.ConfirmImport -> apply()
            AnnotationTransferAction.CancelImport -> {
                pendingFile = null
                _state.update { it.copy(importing = null) }
            }
            is AnnotationTransferAction.DismissNotice -> _state.update { if (it.notice?.key == action.key) it.copy(notice = null) else it }
        }
    }

    // Reads the EPUB, so once per screen.
    private fun checkCalibre() {
        if (calibreChecked) return
        calibreChecked = true
        scope.launch {
            val available = runCatching { port.checkCalibreImport(bookId) is AnnotationImportCheck.Ready }.getOrDefault(false)
            _state.update { it.copy(calibreAvailable = available) }
        }
    }

    // The format sheet stays up (with its loading line) until the file is ready to share.
    private fun export(format: AnnotationFormat) = work {
        val intent = try {
            port.shareIntent(bookId, format)
        } finally {
            _state.update { it.copy(choosingFormat = false) }
        }
        if (intent == null) notify("Couldn't export the highlights.") else shareChannel.send(intent)
    }

    private fun check(fromCalibre: Boolean, read: suspend () -> AnnotationImportCheck?) = work {
        pendingFile = null
        when (val check = read()) {
            is AnnotationImportCheck.Ready -> {
                pendingFile = check.file
                _state.update { it.copy(importing = AnnotationImportUi(check.preview, fromCalibre)) }
            }
            AnnotationImportCheck.NotAnnotationFile -> notify("That file isn't an Open Reader highlights file.")
            is AnnotationImportCheck.NewerVersion -> notify("That file is from a newer version of Open Reader. Update the app to import it.")
            AnnotationImportCheck.BookMissing -> notify("This book is no longer in your library.")
            null -> notify("This book has no Calibre highlights.")
        }
    }

    private fun apply() {
        val file = pendingFile ?: return
        pendingFile = null
        _state.update { it.copy(importing = null) }
        work {
            val result = port.applyImport(bookId, file)
            notify(result?.let(::importSummary) ?: "This book is no longer in your library.")
        }
    }

    // One operation at a time; a failure ends as a notice rather than an exception.
    private fun work(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        _state.update { it.copy(working = true) }
        job = scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notify("Something went wrong. Try again.")
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }

    private fun notify(text: String) {
        val notice = AnnotationNotice(++noticeKey, text)
        _state.update { it.copy(notice = notice) }
    }
}

/** "Imported 3 highlights and 1 bookmark; updated 2 notes." · "Nothing new to import." */
internal fun importSummary(result: AnnotationImportResult): String {
    val added = listOfNotNull(
        result.addedHighlights.takeIf { it > 0 }?.let { count(it, "highlight") },
        result.addedBookmarks.takeIf { it > 0 }?.let { count(it, "bookmark") },
    )
    val parts = buildList {
        if (added.isNotEmpty()) add("Imported ${added.joinToString(" and ")}")
        if (result.updatedHighlights > 0) add("${if (added.isEmpty()) "Updated" else "updated"} ${count(result.updatedHighlights, "highlight")}")
    }
    return if (parts.isEmpty()) "Nothing new to import." else parts.joinToString("; ") + "."
}

/** What the preview dialog says the import will do, e.g. "Adds 3 highlights and 1 bookmark. Updates 2 highlights." */
internal fun importChanges(preview: AnnotationImportPreview): String {
    if (preview.changesNothing) return "Everything in it is already in this book."
    val adds = listOfNotNull(
        preview.newHighlights.takeIf { it > 0 }?.let { count(it, "highlight") },
        preview.newBookmarks.takeIf { it > 0 }?.let { count(it, "bookmark") },
    )
    return buildList {
        if (adds.isNotEmpty()) add("Adds ${adds.joinToString(" and ")}.")
        if (preview.updatedHighlights > 0) add("Updates ${count(preview.updatedHighlights, "highlight")} with newer notes, colours or styles.")
    }.joinToString(" ")
}

/** What the file holds: "12 highlights (4 with notes) and 3 bookmarks". */
internal fun importContents(preview: AnnotationImportPreview): String {
    val highlights = count(preview.highlights, "highlight") + if (preview.notes > 0) " (${preview.notes} with notes)" else ""
    return if (preview.bookmarks > 0) "$highlights and ${count(preview.bookmarks, "bookmark")}" else highlights
}

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
