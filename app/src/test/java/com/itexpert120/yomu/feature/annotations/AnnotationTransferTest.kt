package com.itexpert120.yomu.feature.annotations

import android.content.Intent
import android.net.Uri
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.data.annotations.AnnotationFile
import com.itexpert120.yomu.data.annotations.AnnotationFileBook
import com.itexpert120.yomu.data.annotations.AnnotationFormat
import com.itexpert120.yomu.data.annotations.AnnotationImportCheck
import com.itexpert120.yomu.data.annotations.AnnotationImportPreview
import com.itexpert120.yomu.data.annotations.AnnotationImportResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationTransferTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val port = FakePort()
    private val transfer = AnnotationTransfer(BookId("b"), port, scope)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun calibreImportIsOfferedOnlyWhenTheBookHasSome() {
        transfer.onAction(AnnotationTransferAction.MenuOpened)
        assertFalse(transfer.state.value.calibreAvailable)

        val other = AnnotationTransfer(BookId("b"), FakePort(calibre = ready(preview())), scope)
        other.onAction(AnnotationTransferAction.MenuOpened)
        assertTrue(other.state.value.calibreAvailable)
    }

    @Test
    fun anImportIsAppliedOnlyAfterThePreviewIsConfirmed() {
        port.calibre = ready(preview(newHighlights = 2))
        transfer.onAction(AnnotationTransferAction.ImportCalibre)

        val importing = transfer.state.value.importing!!
        assertTrue(importing.fromCalibre)
        assertTrue(port.applied.isEmpty())

        port.result = AnnotationImportResult(addedHighlights = 2, updatedHighlights = 1, addedBookmarks = 0)
        transfer.onAction(AnnotationTransferAction.ConfirmImport)

        assertEquals(1, port.applied.size)
        assertNull(transfer.state.value.importing)
        assertEquals("Imported 2 highlights; updated 1 highlight.", transfer.state.value.notice?.text)
    }

    @Test
    fun aCancelledImportWritesNothingAndProblemsEndAsNotices() {
        port.calibre = ready(preview())
        transfer.onAction(AnnotationTransferAction.ImportCalibre)
        transfer.onAction(AnnotationTransferAction.CancelImport)
        transfer.onAction(AnnotationTransferAction.ConfirmImport)
        assertTrue(port.applied.isEmpty())

        // (The file and Calibre checks end the same way; a Uri can't be made in a JVM test.)
        port.calibre = AnnotationImportCheck.NotAnnotationFile
        transfer.onAction(AnnotationTransferAction.ImportCalibre)
        assertEquals("That file isn't an Open Reader highlights file.", transfer.state.value.notice?.text)

        port.calibre = AnnotationImportCheck.NewerVersion(9)
        transfer.onAction(AnnotationTransferAction.ImportCalibre)
        val notice = transfer.state.value.notice!!
        assertTrue(notice.text.startsWith("That file is from a newer version"))

        transfer.onAction(AnnotationTransferAction.DismissNotice(notice.key - 1))
        assertEquals(notice, transfer.state.value.notice)
        transfer.onAction(AnnotationTransferAction.DismissNotice(notice.key))
        assertNull(transfer.state.value.notice)
    }

    @Test
    fun theFormatSheetStaysUpUntilTheExportIsReady() {
        transfer.onAction(AnnotationTransferAction.Export)
        assertTrue(transfer.state.value.choosingFormat)

        port.failExport = true
        transfer.onAction(AnnotationTransferAction.ExportAs(AnnotationFormat.Markdown))

        assertEquals(listOf(AnnotationFormat.Markdown), port.exported)
        assertFalse(transfer.state.value.choosingFormat)
        assertFalse(transfer.state.value.working)
        assertEquals("Something went wrong. Try again.", transfer.state.value.notice?.text)
    }

    @Test
    fun summariesCountWhatChanges() {
        assertEquals("Nothing new to import.", importSummary(AnnotationImportResult(0, 0, 0)))
        assertEquals("Imported 1 highlight and 2 bookmarks.", importSummary(AnnotationImportResult(1, 0, 2)))
        assertEquals("Updated 3 highlights.", importSummary(AnnotationImportResult(0, 3, 0)))
        assertEquals("Everything in it is already in this book.", importChanges(preview()))
        assertEquals(
            "Adds 2 highlights and 1 bookmark. Updates 1 highlight with newer notes, colours or styles.",
            importChanges(preview(newHighlights = 2, updated = 1, newBookmarks = 1)),
        )
        assertEquals("12 highlights (4 with notes) and 1 bookmark", importContents(preview(highlights = 12, notes = 4, bookmarks = 1)))
    }

    @Test
    fun anImportFromAnotherBookIsFlagged() {
        val copy = importDialogCopy(AnnotationImportUi(preview(newHighlights = 1).copy(bookMatches = false, fileBookTitle = "Dune", fileBookAuthor = "Frank Herbert"), fromCalibre = false))

        assertEquals("Import highlights?", copy.title)
        assertTrue(copy.warning!!.contains("“Dune” by Frank Herbert"))
        assertNull(importDialogCopy(AnnotationImportUi(preview(), fromCalibre = false)).warning)
    }

    private fun preview(
        newHighlights: Int = 0,
        updated: Int = 0,
        newBookmarks: Int = 0,
        highlights: Int = 3,
        notes: Int = 0,
        bookmarks: Int = 0,
    ) = AnnotationImportPreview("Book", "Author", true, highlights, notes, bookmarks, newHighlights, updated, newBookmarks)

    private fun ready(preview: AnnotationImportPreview) = AnnotationImportCheck.Ready(preview, AnnotationFile(book = AnnotationFileBook(null, "Book", "Author")))

    private class FakePort(var calibre: AnnotationImportCheck? = null) : AnnotationTransferPort {
        var check: AnnotationImportCheck = AnnotationImportCheck.NotAnnotationFile
        var result: AnnotationImportResult? = AnnotationImportResult(0, 0, 0)
        var failExport = false
        val applied = mutableListOf<AnnotationFile>()
        val exported = mutableListOf<AnnotationFormat>()

        override suspend fun shareIntent(bookId: BookId, format: AnnotationFormat): Intent? {
            exported += format
            if (failExport) error("export failed")
            return null
        }

        override suspend fun checkImport(bookId: BookId, uri: Uri) = check

        override suspend fun checkCalibreImport(bookId: BookId) = calibre

        override suspend fun applyImport(bookId: BookId, file: AnnotationFile): AnnotationImportResult? {
            applied += file
            return result
        }
    }
}
