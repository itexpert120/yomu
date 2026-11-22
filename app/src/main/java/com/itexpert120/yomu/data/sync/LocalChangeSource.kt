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
    /** [RecordKind.key]s, emitted after each local write; never the current state on collection. */
    val changes: Flow<Set<String>>
}

/** Room invalidations of each kind's [RecordKind.watchedTables], plus any DataStore write. */
@Singleton
internal class RoomLocalChangeSource @Inject constructor(
    private val database: YomuDatabase,
    private val dataStore: DataStore<Preferences>,
    private val kinds: RecordKinds,
) : LocalChangeSource {
    override val changes: Flow<Set<String>>
        get() {
            val byTable = kinds.all.flatMap { kind -> kind.watchedTables.map { it to kind.key } }
                .groupBy({ it.first }, { it.second })
            val roomChanges = database.invalidationTracker.createFlow(*byTable.keys.toTypedArray(), emitInitialState = false)
                .map { tables -> tables.flatMap { byTable[it].orEmpty() }.toSet() }
            val prefKinds = kinds.all.filter { it.watchesPreferences }.mapTo(HashSet()) { it.key }
            // The first emission is the current state.
            val prefChanges = dataStore.data.drop(1).map { prefKinds }
            return merge(roomChanges, prefChanges)
        }
}
