package com.itexpert120.yomu.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

/**
 * Pushes a fresh render to placed home-screen widgets. The library widget is refreshed when
 * progress is saved / the library changes; the activity widget after a reading session is recorded.
 * Each call no-ops cheaply when no widget of that kind is placed (updateAll iterates the empty id
 * set) and is wrapped so a missing provider (e.g. during tests) fails gracefully.
 */
object WidgetUpdater {

    /** Refresh the library (recent books) widget — call when progress / library content changes. */
    suspend fun refreshLibrary(context: Context) {
        runCatching {
            GlanceAppWidgetManager(context.applicationContext)
            LibraryWidget().updateAll(context.applicationContext)
        }
    }

    /** Refresh the reading-activity widget — call after a reading session is recorded. */
    suspend fun refreshActivity(context: Context) {
        runCatching {
            GlanceAppWidgetManager(context.applicationContext)
            ActivityWidget().updateAll(context.applicationContext)
        }
    }
}
