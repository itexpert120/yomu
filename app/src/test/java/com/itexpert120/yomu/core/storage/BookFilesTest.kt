package com.itexpert120.yomu.core.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun files(rename: (File, File) -> Boolean = File::renameTo) = BookFiles(temporary.root, { null }, CoroutineScope(Dispatchers.IO), rename)

    private val epubs get() = File(temporary.root, "epubs")
    private val covers get() = File(temporary.root, "covers")

    private fun epubBytes(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("<container/>".toByteArray())
        }
    }.toByteArray()

    private fun file(dir: File, name: String, text: String = name) = File(dir.apply { mkdirs() }, name).apply { writeText(text) }

    @Test fun stagedBookIsHashedAndDiscardRemovesIt() = runBlocking {
        val staged = files().stage("book") { ByteArrayInputStream(epubBytes()) }
        assertEquals(File(epubs, "book.epub"), staged.file)
        assertTrue(staged.file.exists())
        assertEquals(64, staged.sha256.length)
        staged.discard()
        assertFalse(staged.file.exists())
    }

    @Test fun anInvalidOrEmptyEpubLeavesNothingBehind() = runBlocking {
        val files = files()
        assertTrue(runCatching { files.stage("bad") { ByteArrayInputStream("not a zip".toByteArray()) } }.isFailure)
        assertTrue(runCatching { files.stage("empty") { ByteArrayInputStream(ByteArray(0)) } }.isFailure)
        assertTrue(epubs.listFiles().orEmpty().isEmpty())
    }

    @Test fun leftoverPartialCopiesAreSweptBeforeCopying() = runBlocking {
        val leftover = file(epubs, "old.partial")
        files().stage("book") { ByteArrayInputStream(epubBytes()) }
        assertFalse(leftover.exists())
    }

    @Test fun stagedCoverCanBeDiscarded() = runBlocking {
        val cover = files().stageCover("book", 42) { ByteArrayInputStream("image".toByteArray()) }
        assertEquals(File(covers, "book-42.png").absolutePath, cover.path)
        cover.discard()
        assertFalse(File(cover.path).exists())
    }

    @Test fun deletingUnlinksOnlyAfterTheCommit(): Unit = runBlocking {
        val epub = file(epubs, "book.epub")
        val cover = file(covers, "book.webp")
        files().deleting(listOf(epub.absolutePath, cover.absolutePath)) {
            assertFalse("staged aside during the commit", epub.exists())
        }
        assertFalse(epub.exists())
        assertFalse(cover.exists())
        assertTrue(epubs.listFiles().orEmpty().isEmpty())
    }

    @Test fun aFailedCommitRestoresTheFiles() {
        val epub = file(epubs, "book.epub", "publication")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { files().deleting(listOf(epub.absolutePath)) { error("database failed") } }
        }
        assertEquals("publication", epub.readText())
    }

    @Test fun failedBulkStagingRestoresEarlierFiles() {
        val first = file(epubs, "first.epub")
        val second = file(epubs, "second.epub")
        val files = files { source, target -> source != second && source.renameTo(target) }
        var committed = false
        assertThrows(IllegalStateException::class.java) {
            runBlocking { files.deleting(listOf(first.absolutePath, second.absolutePath)) { committed = true } }
        }
        assertFalse(committed)
        assertEquals("first.epub", first.readText())
        assertEquals("second.epub", second.readText())
    }

    @Test fun reconcileRestoresFilesWhenTheDeletionDidNotCommit() {
        val epub = file(epubs, "book.epub", "publication")
        val trash = File(epubs, ".book.epub.$STAGE_ID.deleting")
        check(epub.renameTo(trash))
        files().reconcile(setOf(epub.absolutePath))
        assertEquals("publication", epub.readText())
        files().reconcile(setOf(epub.absolutePath))
        assertEquals("publication", epub.readText())
    }

    @Test fun reconcileFinishesACommittedDeletionButKeepsLiveFiles() {
        val live = file(epubs, "live.epub")
        val trash = file(epubs, ".removed.epub.$STAGE_ID.deleting")
        files().reconcile(setOf(live.absolutePath))
        assertFalse(trash.exists())
        assertTrue(live.exists())
    }

    @Test fun reconcileSweepsCoversNoBookPointsAt() {
        val live = file(covers, "book-100.png")
        val editedNeverSaved = file(covers, "book-200.png")
        val importNeverCommitted = file(covers, "other.webp")
        val liveEpub = file(epubs, "book.epub")
        files().reconcile(setOf(live.absolutePath, liveEpub.absolutePath))
        assertTrue(live.exists())
        assertTrue(liveEpub.exists())
        assertFalse(editedNeverSaved.exists())
        assertFalse(importNeverCommitted.exists())
    }

    private companion object {
        const val STAGE_ID = "123e4567-e89b-12d3-a456-426614174000"
    }
}
