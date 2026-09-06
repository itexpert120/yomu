package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.core.model.ReaderSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSettingsEditorTest {
    @Test fun newestEditWinsOverDelayedWriteResetAndOldEcho() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val initial = ReaderSettings()
        val older = initial.copy(fontScale = 1.2f)
        val newest = initial.copy(fontScale = 1.6f)
        val saved = mutableListOf<ReaderSettings>()
        val editor = ReaderSettingsEditor(this, {
            gate.await()
            saved += it
        }, { initial })
        editor.observe(initial)
        editor.edit(older)
        yield()
        editor.reset()
        editor.edit(newest)
        editor.observe(older)
        assertEquals(newest, editor.state.value.settings)
        gate.complete(Unit)
        yield()
        assertEquals(listOf(older, newest), saved)
        editor.observe(older)
        assertEquals(newest, editor.state.value.settings)
        editor.observe(newest)
        editor.reset()
        yield()
        assertEquals(initial, editor.state.value.settings)
    }

    @Test fun failedSaveAndResetKeepRetryableIntent() = runBlocking {
        var fail = true
        val settings = ReaderSettings().copy(fontScale = 1.4f)
        val editor = ReaderSettingsEditor(this, { check(!fail) }, {
            check(!fail)
            ReaderSettings()
        })
        editor.edit(settings)
        yield()
        assertNotNull(editor.state.value.error)
        assertEquals(settings, editor.state.value.settings)
        fail = false
        editor.retry()
        yield()
        assertNull(editor.state.value.error)
        fail = true
        editor.reset()
        yield()
        assertNotNull(editor.state.value.error)
        fail = false
        editor.retry()
        yield()
        assertEquals(ReaderSettings(), editor.state.value.settings)
    }
}
