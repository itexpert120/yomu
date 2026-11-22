package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.database.CollectionDao
import com.itexpert120.yomu.core.database.CollectionEntity
import com.itexpert120.yomu.core.model.ColorStyle
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.data.fonts.FontRepository
import com.itexpert120.yomu.data.settings.AppSettingsRepository
import com.itexpert120.yomu.data.settings.LibraryPrefsRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The singleton settings records: app appearance, library view and the reading defaults. Each
 * carries no edit time; dating them at the epoch means a joining device adopts the group's
 * settings instead of overwriting them with its own.
 */
@Singleton
internal class SettingsKind @Inject constructor(
    private val appSettings: AppSettingsRepository,
    private val libraryPrefs: LibraryPrefsRepository,
    private val readerSettings: ReaderSettingsRepository,
    private val fonts: SyncFonts,
) : RecordKind<JsonObject> {
    override val key = "settings"
    override val bookBound = false
    override val watchesPreferences = true
    override val payload = JsonObject.serializer()

    override suspend fun snapshot(): List<LocalRecord> {
        val app = AppSettingsPayload(appSettings.themePreference.first().name, appSettings.colorStyle.first().name)
        val library = libraryPrefs.preferences.first().let { LibraryPayload(sort = it.sortMode.name, view = it.viewMode.name, coverCrop = it.coverCrop) }
        return listOf(
            LocalRecord(key, SettingsId.APP, null, SyncJson.encodeToString(AppSettingsPayload.serializer(), app), 1),
            LocalRecord(key, SettingsId.LIBRARY, null, SyncJson.encodeToString(LibraryPayload.serializer(), library), 1),
            LocalRecord(key, SettingsId.READER_DEFAULTS, null, ReaderSettingsSync.toPayload(readerSettings.global.first()), 1),
        )
    }

    override suspend fun apply(id: String, payload: JsonObject?, ctx: ApplyContext): Applied {
        if (payload == null) return Applied(null)
        when (id) {
            SettingsId.APP -> {
                val app = SyncJson.decodeFromJsonElement(AppSettingsPayload.serializer(), payload)
                runCatching { ThemePreference.valueOf(app.theme) }.getOrNull()?.let { appSettings.setThemePreference(it) }
                ColorStyle.fromStoredName(app.colorStyle)?.let { appSettings.setColorStyle(it) }
            }

            SettingsId.LIBRARY -> {
                val library = SyncJson.decodeFromJsonElement(LibraryPayload.serializer(), payload)
                SortMode.entries.firstOrNull { it.name == library.sort }?.let { libraryPrefs.setSortMode(it) }
                // library.group is a retired setting (superseded by library tabs); ignored on apply.
                LibraryViewMode.entries.firstOrNull { it.name == library.view }?.let { libraryPrefs.setViewMode(it) }
                libraryPrefs.setCoverCrop(library.coverCrop)
            }

            SettingsId.READER_DEFAULTS -> {
                val merged = ReaderSettingsSync.merge(readerSettings.global.first(), payload, fonts.installed()::get)
                readerSettings.setGlobal(merged)
                return Applied(ReaderSettingsSync.toPayload(merged))
            }
        }
        return stored(payload)
    }
}

/** The user's saved custom reader themes. */
@Singleton
internal class ThemeKind @Inject constructor(private val readerSettings: ReaderSettingsRepository) : RecordKind<CustomReaderTheme> {
    override val key = "theme"
    override val bookBound = false
    override val watchesPreferences = true
    override val payload = CustomReaderTheme.serializer()

    override suspend fun snapshot() = readerSettings.customThemes.first().map { record(it.id, null, it, 1) }

    override suspend fun apply(id: String, payload: CustomReaderTheme?, ctx: ApplyContext): Applied {
        if (payload == null) readerSettings.deleteCustomTheme(id) else readerSettings.saveCustomTheme(payload)
        return stored(payload)
    }
}

/**
 * Installed custom fonts, by family; each device downloads its own files. A font still downloading
 * counts as installed, so a failed install never reads as a removal.
 */
@Singleton
internal class FontKind @Inject constructor(private val fonts: SyncFonts, private val store: SyncStore) : RecordKind<FontPayload> {
    override val key = "font"
    override val bookBound = false
    override val watchesPreferences = true

    // Fonts first, so settings in the same batch that use them resolve to installed files.
    override val applyOrder = 0
    override val payload = FontPayload.serializer()

    override suspend fun snapshot(): List<LocalRecord> {
        val families = fonts.installed().keys + store.metadataWithPrefix(PENDING_PREFIX).values
        return families.map { record(it, null, FontPayload(it), 1) }
    }

    override suspend fun apply(id: String, payload: FontPayload?, ctx: ApplyContext): Applied {
        if (payload == null) {
            store.deleteMetadata(PENDING_PREFIX + id)
            fonts.remove(id)
        } else if (id !in fonts.installed()) {
            store.putMetadata(PENDING_PREFIX + id, id)
            install(id)
        }
        return stored(payload)
    }

    override suspend fun retryPending() {
        store.metadataWithPrefix(PENDING_PREFIX).values.forEach { install(it) }
    }

    private suspend fun install(family: String) {
        if (family in fonts.installed() || fonts.install(family)) store.deleteMetadata(PENDING_PREFIX + family)
    }

    private companion object {
        const val PENDING_PREFIX = "fontPending/"
    }
}

/** The user's collections (name and order); memberships travel with each book. */
@Singleton
internal class CollectionKind @Inject constructor(private val collectionDao: CollectionDao) : RecordKind<CollectionPayload> {
    override val key = "collection"
    override val bookBound = false
    override val watchedTables = setOf("collections")
    override val payload = CollectionPayload.serializer()

    override suspend fun snapshot() = collectionDao.collections().map { record(it.id, null, CollectionPayload(it.name, it.position), maxOf(it.createdAt, 1)) }

    override suspend fun apply(id: String, payload: CollectionPayload?, ctx: ApplyContext): Applied {
        val editedAt = maxOf(ctx.hlc.physical, 1)
        if (payload == null) {
            collectionDao.deleteCollection(id, editedAt = editedAt)
        } else {
            val createdAt = collectionDao.collections().firstOrNull { it.id == id }?.createdAt ?: editedAt
            collectionDao.upsertCollection(CollectionEntity(id, payload.name, payload.position, createdAt))
        }
        return stored(payload)
    }
}

/** Custom fonts as sync needs them. */
internal interface SyncFonts {
    /** Installed fonts by family. */
    suspend fun installed(): Map<String, CustomFontRef>

    /** Downloads and installs [family]; false if that failed (e.g. offline). */
    suspend fun install(family: String): Boolean
    suspend fun remove(family: String)
}

@Singleton
internal class RepositorySyncFonts @Inject constructor(private val fonts: FontRepository) : SyncFonts {
    override suspend fun installed() = fonts.installed.first().associateBy { it.family }
    override suspend fun install(family: String) = fonts.install(family).isSuccess
    override suspend fun remove(family: String) {
        fonts.remove(family)
    }
}
