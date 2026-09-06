package com.itexpert120.yomu.data.fonts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.itexpert120.yomu.core.model.CustomFontRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FontInstallationStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun interruptedReplacementKeepsOldFilesUntilReferencesAreRepaired() = runBlocking {
        val preferences = MemoryStore()
        val old = CustomFontRef("Family", temporary.newFile("old").absolutePath)
        val next = CustomFontRef("Family", temporary.newFile("new").absolutePath)
        var selected: CustomFontRef? = old
        var fail = true
        fun store() = FontInstallationStore(preferences, { _, replacement ->
            check(!fail)
            selected = replacement
        }, {
            File(it).delete()
            Unit
        })
        store().publish(old)
        assertTrue(runCatching { store().publish(next) }.isFailure)
        assertTrue(File(old.regularPath).exists())
        assertEquals(old, selected)
        fail = false
        val restarted = store()
        restarted.recover()
        assertEquals(next, selected)
        assertFalse(File(old.regularPath).exists())
        assertTrue(File(next.regularPath).exists())
        assertEquals(listOf(next), restarted.installed.first())
        restarted.remove("Family")
        assertNull(selected)
        assertFalse(File(next.regularPath).exists())
        assertTrue(restarted.installed.first().isEmpty())
    }

    @Test fun failedRegistryWritePreservesWorkingInstallation() = runBlocking {
        val preferences = MemoryStore()
        val old = CustomFontRef("Family", temporary.newFile("old").absolutePath)
        val next = CustomFontRef("Family", temporary.newFile("new").absolutePath)
        val store = FontInstallationStore(preferences, { _, _ -> }, {
            File(it).delete()
            Unit
        })
        store.publish(old)
        preferences.fail = true
        assertTrue(runCatching { store.publish(next) }.isFailure)
        assertEquals(listOf(old), store.installed.first())
        assertTrue(File(old.regularPath).exists())
    }

    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        var fail = false
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(data.value)
            check(!fail)
            data.value = next
            return next
        }
    }
}
