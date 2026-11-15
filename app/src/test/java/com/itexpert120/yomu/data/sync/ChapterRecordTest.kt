package com.itexpert120.yomu.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterRecordTest {
    @Test fun idKeepsChapterIdsThatContainSeparators() {
        val id = ChapterRecordId.of("abc123", "OEBPS/ch|1.xhtml#part/2")
        assertEquals("abc123", ChapterRecordId.sha256(id))
        assertEquals("OEBPS/ch|1.xhtml#part/2", ChapterRecordId.chapterId(id))
        assertEquals(SyncKind.Chapter, SyncKind.of(SyncKind.Chapter.name(id)))
        assertEquals(id, SyncKind.Chapter.name(id).substringAfter('/'))
    }

    @Test fun mergeKeepsReadFromEitherSideAndFurthestProgress() {
        val local = ChapterPayload(read = true, progress = 0.4f, updatedAt = 200, manual = false)
        val remote = ChapterPayload(read = false, progress = 0.9f, updatedAt = 100, manual = true)
        val expected = ChapterPayload(read = true, progress = 0.9f, updatedAt = 100, manual = true)
        assertEquals(expected, local.mergedWith(remote))
        assertEquals(expected, remote.mergedWith(local))
    }

    @Test fun mergeWithoutProgressOnOneSideKeepsTheOther() {
        val readOnly = ChapterPayload(read = true, progress = null, updatedAt = 0, manual = false)
        val progressOnly = ChapterPayload(read = false, progress = 0.3f, updatedAt = 50, manual = false)
        assertEquals(ChapterPayload(true, 0.3f, 50, false), readOnly.mergedWith(progressOnly))
        assertEquals(readOnly, readOnly.mergedWith(readOnly))
    }

    @Test fun equalProgressPrefersTheNewerState() {
        val older = ChapterPayload(read = false, progress = 0.5f, updatedAt = 10, manual = false)
        val newer = ChapterPayload(read = false, progress = 0.5f, updatedAt = 20, manual = true)
        assertEquals(newer, older.mergedWith(newer))
    }
}
