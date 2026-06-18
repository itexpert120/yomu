package com.itexpert120.yomu.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.itexpert120.yomu.YomuApplication
import com.itexpert120.yomu.core.datastore.yomuPreferences
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.books.FakeBookRepository
import com.itexpert120.yomu.data.settings.AppSettingsRepository
import com.itexpert120.yomu.data.settings.LibraryPrefsRepository

/**
 * Application-scoped manual dependency graph. Holds the singletons screens depend on so they
 * survive configuration changes. Hilt replaces this once Room/import introduce real wiring
 * (see docs/android-build-patterns.md "Hilt Pattern").
 */
class YomuGraph(context: Context) {
    val appContext: Context = context.applicationContext

    val bookRepository: BookRepository = FakeBookRepository()

    val appSettingsRepository = AppSettingsRepository(appContext.yomuPreferences)

    val libraryPrefsRepository = LibraryPrefsRepository(appContext.yomuPreferences)
}

/** Resolves the app graph from the [YomuApplication]. */
@Composable
fun rememberYomuGraph(): YomuGraph {
    val context = LocalContext.current
    return (context.applicationContext as YomuApplication).graph
}
