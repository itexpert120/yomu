package com.itexpert120.yomu.feature.bookdetails

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverExportTest {
    @Test fun failuresDeleteIncompleteDestinationsAndSuccessRetainsPublishedImage() = runBlocking {
        for (failure in listOf("null stream", "copy", "publish", "cancel", "none")) {
            val removed = mutableListOf<String>()
            val result = runCatching {
                CoverExport().export(
                    create = { "destination" },
                    copy = {
                        if (failure == "cancel") throw CancellationException()
                        check(failure !in listOf("null stream", "copy"))
                    },
                    publish = { failure != "publish" },
                    delete = {
                        removed.add(it)
                        true
                    },
                )
            }
            if (failure == "cancel") {
                assertTrue(result.exceptionOrNull() is CancellationException)
            } else {
                assertEquals(failure == "none", result.getOrThrow())
            }
            assertEquals(if (failure == "none") emptyList<String>() else listOf("destination"), removed)
        }
    }

    @Test fun failedCleanupRemainsOwnedForRetry() = runBlocking {
        val exporter = CoverExport()
        assertFalse(exporter.export({ "incomplete" }, { error("copy") }, { true }, { false }))
        val deleted = mutableListOf<String>()
        assertTrue(
            exporter.export({ "complete" }, {}, { true }, {
                deleted.add(it)
                true
            }),
        )
        assertEquals(listOf("incomplete"), deleted)
    }
}
