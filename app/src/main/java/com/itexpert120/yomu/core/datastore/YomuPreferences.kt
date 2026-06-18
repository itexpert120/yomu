package com.itexpert120.yomu.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single process-wide Preferences DataStore for app/library settings. Repositories in
 * data/settings read and write typed slices of it; nothing else touches DataStore directly
 * (docs/android-build-patterns.md).
 */
val Context.yomuPreferences: DataStore<Preferences> by preferencesDataStore(name = "yomu_prefs")
