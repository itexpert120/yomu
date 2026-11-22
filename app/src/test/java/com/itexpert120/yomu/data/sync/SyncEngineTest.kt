package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.data.sync.crypto.SyncKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Two (or more) engines on one in-memory server, each with its own ledger, clock and data. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {
    private val keys = SyncKeys(ByteArray(16) { 7 })
    private val server = InMemorySyncTransport()

    private fun TestScope.device(label: String) = SyncTestDevice(label, label.lowercase(), server, backgroundScope)

    /** Long enough for the change debounce, pull, apply and push to finish. */
    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    private suspend fun TestScope.startAll(vararg devices: SyncTestDevice) {
        server.create(keys.token)
        devices.forEach { it.engine.start(keys) }
        settle()
    }

    /** The decrypted payload the server holds for record [name]; null for a tombstone. */
    private fun serverPayload(name: String): String? {
        val serverKey = keys.serverKey(name)
        val blob = checkNotNull(server.record(keys.token, serverKey)?.blob) { "$name was never pushed" }
        return SyncJson.decodeFromString(SyncEnvelope.serializer(), keys.open(serverKey, blob).decodeToString()).payload
    }

    @Test
    fun devicesConverge() = runTest {
        val a = device("A")
        val b = device("B")
        a.put(SyncKind.Theme, "t1", "a-theme")
        b.put(SyncKind.Collection, "c1", "b-collection")
        startAll(a, b)

        assertEquals("a-theme", b.value(SyncKind.Theme, "t1"))
        assertEquals("b-collection", a.value(SyncKind.Collection, "c1"))
        assertEquals(SyncStatus.Phase.UpToDate, a.engine.status.value.phase)
        assertEquals(SyncStatus.Phase.UpToDate, b.engine.status.value.phase)
    }

    @Test
    fun laterEditWins() = runTest {
        val a = device("A")
        val b = device("B")
        a.put(SyncKind.Theme, "t", "v1")
        startAll(a, b)
        assertEquals("v1", b.value(SyncKind.Theme, "t"))

        b.put(SyncKind.Theme, "t", "v2")
        settle()
        assertEquals("v2", a.value(SyncKind.Theme, "t"))

        a.put(SyncKind.Theme, "t", "v3")
        settle()
        assertEquals("v3", a.value(SyncKind.Theme, "t"))
        assertEquals("v3", b.value(SyncKind.Theme, "t"))
    }

    @Test
    fun concurrentEditsSettleOnTheHigherStamp() = runTest {
        val a = device("A")
        val b = device("B")
        a.put(SyncKind.Theme, "t", "v1")
        startAll(a, b)
        server.online = false
        a.put(SyncKind.Theme, "t", "from-a")
        b.put(SyncKind.Theme, "t", "from-b")
        settle()
        val stampA = a.store.ledgerRows.getValue("theme/t").let { Hlc(it.physicalTime, it.logicalCounter.toInt(), it.originDeviceId) }
        val stampB = b.store.ledgerRows.getValue("theme/t").let { Hlc(it.physicalTime, it.logicalCounter.toInt(), it.originDeviceId) }
        server.online = true
        advanceTimeBy(31_000)
        settle()

        val winner = if (stampA > stampB) "from-a" else "from-b"
        assertEquals(winner, a.value(SyncKind.Theme, "t"))
        assertEquals(winner, b.value(SyncKind.Theme, "t"))
    }

    @Test
    fun newestPreSyncCopyWinsOnFirstContact() = runTest {
        val a = device("A")
        val b = device("B")
        a.put(SyncKind.Theme, "t", "older", timeHint = 100)
        b.put(SyncKind.Theme, "t", "newer", timeHint = 200)
        startAll(a, b)

        assertEquals("newer", a.value(SyncKind.Theme, "t"))
        assertEquals("newer", b.value(SyncKind.Theme, "t"))
    }

    @Test
    fun applyingARecordDoesNotEchoItBack() = runTest {
        val a = device("A")
        val b = device("B")
        a.put(SyncKind.Theme, "t", "v1")
        startAll(a, b)
        val head = server.head(keys.token)
        settle()
        settle()

        assertEquals(head, server.head(keys.token), 0.0)
        assertEquals(0, b.store.dirtyCount())
        assertFalse(b.store.ledgerRows.getValue("theme/t").dirty)
    }

    @Test
    fun localDeleteSendsATombstone() = runTest {
        val a = device("A")
        val b = device("B")
        a.put(SyncKind.Theme, "t", "v1")
        startAll(a, b)
        assertEquals("v1", b.value(SyncKind.Theme, "t"))

        a.delete(SyncKind.Theme, "t")
        settle()
        assertNull(serverPayload("theme/t"))
        assertNull(b.value(SyncKind.Theme, "t"))
    }

    @Test
    fun bookRemovedLocallyKeepsItsDataEverywhereElse() = runTest {
        val a = device("A")
        val b = device("B")
        a.addBook("sha")
        b.addBook("sha")
        a.put(SyncKind.Bookmark, "sha", "mark")
        startAll(a, b)
        assertEquals("mark", b.value(SyncKind.Bookmark, "sha"))

        a.removeBook("sha")
        a.delete(SyncKind.Bookmark, "sha")
        settle()
        assertEquals("mark", serverPayload("bookmark/sha"))
        assertEquals("mark", b.value(SyncKind.Bookmark, "sha"))
        assertFalse(a.store.ledgerRows.getValue("bookmark/sha").deleted)
    }

    @Test
    fun deletedGroupStopsSync() = runTest {
        val a = device("A")
        startAll(a)
        server.destroy(keys.token)
        settle()
        assertEquals(SyncStatus.Phase.GroupDeleted, a.engine.status.value.phase)
    }

    @Test
    fun serverRequiringANewerAppStopsAtStart() = runTest {
        val a = device("A")
        server.create(keys.token)
        server.minClientProtocol = SyncProtocol.VERSION + 1
        a.engine.start(keys)
        settle()
        assertEquals(SyncStatus.Phase.UpdateRequired, a.engine.status.value.phase)
    }

    @Test
    fun serverRequiringANewerAppStopsMidSync() = runTest {
        val a = device("A")
        startAll(a)
        server.minClientProtocol = SyncProtocol.VERSION + 1
        a.put(SyncKind.Theme, "t", "v1")
        settle()
        assertEquals(SyncStatus.Phase.UpdateRequired, a.engine.status.value.phase)
    }

    @Test
    fun outdatedServerStopsAtStart() = runTest {
        val a = device("A")
        server.create(keys.token)
        server.protocol = SyncProtocol.MIN_SERVER - 1
        a.engine.start(keys)
        settle()
        assertEquals(SyncStatus.Phase.ServerOutdated, a.engine.status.value.phase)
    }

    @Test
    fun pausePushesPendingChangesThenStops() = runTest {
        val a = device("A")
        val b = device("B")
        startAll(a, b)
        // Still inside the change debounce: only pause() can get it out before stopping.
        a.put(SyncKind.Theme, "t", "v1")
        a.engine.pause(timeoutMs = 10_000)

        assertEquals(SyncStatus.Phase.Off, a.engine.status.value.phase)
        assertEquals(0, a.store.dirtyCount())
        assertEquals("v1", serverPayload("theme/t"))
        settle()
        assertEquals("v1", b.value(SyncKind.Theme, "t"))
    }

    @Test
    fun pauseWhileOfflineStopsAndKeepsChangesForLater() = runTest {
        val a = device("A")
        startAll(a)
        server.online = false
        a.put(SyncKind.Theme, "t", "v1")
        a.engine.pause(timeoutMs = 10_000)

        assertEquals(SyncStatus.Phase.Off, a.engine.status.value.phase)
        assertEquals(1, a.store.dirtyCount())

        server.online = true
        a.engine.start(keys)
        settle()
        assertEquals(0, a.store.dirtyCount())
        assertEquals("v1", serverPayload("theme/t"))
    }
}
