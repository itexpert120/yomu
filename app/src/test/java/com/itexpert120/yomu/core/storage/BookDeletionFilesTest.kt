package com.itexpert120.yomu.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BookDeletionFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun failedBulkStagingRestoresEarlierFiles() {
        val first = temporary.newFile("first.epub").apply { writeText("first") }
        val second = temporary.newFile("second.epub").apply { writeText("second") }
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            BookDeletionFiles.stage(listOf(first, second)) { source, target ->
                source != second && source.renameTo(target)
            }
        }
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
    }

    @Test fun restartRestoresFilesWhenDatabaseDeletionDidNotCommit() {
        val epub = temporary.newFile("book.epub").apply { writeText("publication") }
        BookDeletionFiles.stage(listOf(epub))
        assertFalse(epub.exists())
        BookDeletionFiles.recover(listOf(temporary.root), setOf(epub.absolutePath))
        assertEquals("publication", epub.readText())
        BookDeletionFiles.recover(listOf(temporary.root), setOf(epub.absolutePath))
        assertEquals("publication", epub.readText())
    }

    @Test fun restartFinishesCommittedDeletionButPreservesUnrelatedFiles() {
        val epub = temporary.newFile("removed.epub")
        val other = temporary.newFile("live.epub").apply { writeText("keep") }
        val staged = BookDeletionFiles.stage(listOf(epub))
        BookDeletionFiles.recover(listOf(temporary.root), setOf(other.absolutePath))
        assertFalse(staged.single().second.exists())
        assertEquals("keep", other.readText())
    }
}
