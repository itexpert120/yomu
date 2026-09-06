package com.itexpert120.yomu.feature.bookedit

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BookEditorTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun cannotSaveBeforeLoadOrDeleteCoverBeingCommitted() = runBlocking {
        val load = CompletableDeferred<Book?>()
        val committing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var saved: EditBookUiState? = null
        val editor = BookEditor(this, this, { load.await() }, {
            committing.complete(Unit)
            release.await()
            saved = it
        }, {
            File(it).delete()
            Unit
        })
        editor.save().join()
        assertEquals(null, saved)
        load.complete(Book(BookId("book"), "Original", "Author"))
        editor.state.first { it.editable }
        val cover = files.newFile("cover").apply { writeText("image") }
        editor.pickCover { cover.absolutePath }.join()
        val saving = editor.save()
        committing.await()
        val picking = editor.pickCover { error("A saved draft cannot accept another cover") }
        editor.close()
        release.complete(Unit)
        saving.join()
        picking.join()
        assertEquals(cover.absolutePath, saved?.coverImagePath)
        assertTrue(cover.exists())
    }

    @Test fun failedCommitRetainsDraftAndPendingCoverForRetry() = runBlocking {
        var fails = true
        val editor = BookEditor(this, this, { Book(BookId("book"), "Original", "Author") }, { check(!fails) }, {
            File(it).delete()
            Unit
        })
        editor.state.first { it.editable }
        val cover = files.newFile("cover")
        editor.pickCover { cover.absolutePath }.join()
        editor.edit { it.copy(title = "Edited") }
        editor.save().join()
        assertTrue(editor.state.value.error != null)
        assertEquals("Edited", editor.state.value.title)
        assertTrue(cover.exists())
        fails = false
        editor.save().join()
        assertTrue(editor.state.value.saved)
        editor.close()
    }
}
