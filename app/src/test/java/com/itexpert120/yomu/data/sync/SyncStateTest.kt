package com.itexpert120.yomu.data.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The metadata keys are what existing installs already store; they must never change. */
class SyncStateTest {
    private val store = InMemorySyncStore()
    private val state = SyncState(store)

    @Test
    fun storedKeysStayFixed() = runTest {
        state.setCursor(42.0)
        state.saveClock(Hlc(1_700_000_000_000, 2, "dev1"))
        state.markAwaitingBook("sha1")
        state.markFontPending("Inter")
        state.putOffer(ProgressOffer("sha2", "{}", 0.5f, "Pixel", "1700000000000:00000:dev2"))
        val device = state.device()

        assertEquals(
            mapOf(
                "cursor" to "42.0",
                "clock" to "1700000000000:00002:dev1",
                "pendingBook/sha1" to "sha1",
                "fontPending/Inter" to "Inter",
                "offer/sha2" to """{"sha256":"sha2","locatorJson":"{}","progress":0.5,"device":"Pixel","hlc":"1700000000000:00000:dev2"}""",
                "device" to device,
            ),
            store.metadataWithPrefix(""),
        )
    }

    @Test
    fun readsWhatItStored() = runTest {
        assertEquals(0.0, state.cursor(), 0.0)
        assertNull(state.clock())
        state.setCursor(7.0)
        state.saveClock(Hlc(5, 1, "d"))
        assertEquals(7.0, state.cursor(), 0.0)
        assertEquals(Hlc(5, 1, "d"), state.clock())
        assertEquals(state.device(), state.device())
        assertEquals(12, state.device().length)

        state.markAwaitingBook("a")
        state.markAwaitingBook("b")
        state.clearAwaitingBook("a")
        assertEquals(setOf("b"), state.awaitingBooks())

        state.markFontPending("Inter")
        state.clearFontPending("Inter")
        assertEquals(emptySet<String>(), state.pendingFonts())
    }

    @Test
    fun offerIsPerBook() = runTest {
        val offer = ProgressOffer("sha", "{}", 0.5f, null, "1:00000:d")
        state.putOffer(offer)
        state.putOffer(offer.copy(sha256 = "sha-other"))
        assertEquals(offer, state.offer("sha").first())
        state.clearOffer("sha")
        assertNull(state.offer("sha").first())
    }
}
