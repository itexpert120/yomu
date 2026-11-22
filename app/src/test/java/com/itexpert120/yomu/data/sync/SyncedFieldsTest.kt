package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.data.settings.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * A new setting must be declared synced or device-local before it ships; and the declarations
 * must match what each payload actually carries.
 */
class SyncedFieldsTest {
    private fun assertDeclared(group: String, fields: SyncedFields, modelFields: List<String>) {
        assertEquals("$group fields declared neither synced nor device-local", emptyList<String>(), fields.undeclared(modelFields))
        val stale = (fields.synced.keys + fields.deviceLocal) - modelFields.toSet()
        assertEquals("$group declarations for fields that no longer exist", emptySet<String>(), stale)
    }

    private fun wireNames(serializer: KSerializer<*>) = (0 until serializer.descriptor.elementsCount).map { serializer.descriptor.getElementName(it) }.toSet()

    @Test
    fun readerSettings() {
        assertDeclared("ReaderSettings", ReaderSettingsSync.fields, wireNames(ReaderSettings.serializer()).toList())
        val payloadKeys = SyncJson.parseToJsonElement(ReaderSettingsSync.toPayload(ReaderSettings())).jsonObject.keys
        assertEquals(ReaderSettingsSync.fields.wireFields, payloadKeys)
    }

    @Test
    fun appSettings() {
        // Every setting AppSettingsRepository exposes, as a Flow property.
        val settings = AppSettingsRepository::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && it.parameterCount == 0 && Flow::class.java.isAssignableFrom(it.returnType) && it.name.startsWith("get") }
            .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercase) }
        assertDeclared("App settings", SettingsKind.APP_FIELDS, settings)
        assertEquals(SettingsKind.APP_FIELDS.wireFields, wireNames(AppSettingsPayload.serializer()))
    }

    @Test
    fun librarySettings() {
        val fields = LibraryPreferences::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }
        assertDeclared("LibraryPreferences", SettingsKind.LIBRARY_FIELDS, fields)
        assertEquals(SettingsKind.LIBRARY_FIELDS.wireFields, wireNames(LibraryPayload.serializer()))
    }
}
