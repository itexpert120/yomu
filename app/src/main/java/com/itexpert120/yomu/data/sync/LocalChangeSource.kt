package com.itexpert120.yomu.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.itexpert120.yomu.core.database.YomuDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/** Tells the sync engine which record kinds may have changed locally since it last looked. */
internal interface LocalChangeSource {
    /** Emits after each local write; never emits the current state on collection. */
    val changes: Flow<Set<SyncKind>>
}

/** Room table invalidations plus any DataStore write (settings, themes and fonts live there). */
@Singleton
internal class RoomLocalChangeSource @Inject constructor(
    private val database: YomuDatabase,
    private val dataStore: DataStore<Preferences>,
) : LocalChangeSource {
    override val changes: Flow<Set<SyncKind>>
        get() {
            val roomChanges = database.invalidationTracker.createFlow(*TABLE_KINDS.keys.toTypedArray(), emitInitialState = false)
                .map { tables -> tables.flatMap { TABLE_KINDS[it].orEmpty() }.toSet() }
            // The first emission is the current state.
            val prefChanges = dataStore.data.drop(1).map { setOf(SyncKind.Settings, SyncKind.Theme, SyncKind.Font) }
            return merge(roomChanges, prefChanges)
        }

    private companion object {
        /** Which record kinds each table feeds. */
        val TABLE_KINDS = mapOf(
            "books" to setOf(SyncKind.Meta, SyncKind.Progress, SyncKind.Shelf),
            "book_collections" to setOf(SyncKind.Shelf),
            "collections" to setOf(SyncKind.Collection),
            "chapter_reads" to setOf(SyncKind.Chapter),
            "chapter_progress" to setOf(SyncKind.Chapter),
            "bookmarks" to setOf(SyncKind.Bookmark),
            "highlights" to setOf(SyncKind.Highlight),
            "reader_settings" to setOf(SyncKind.BookSettings),
            "reading_sessions" to setOf(SyncKind.Session),
        )
    }
}
