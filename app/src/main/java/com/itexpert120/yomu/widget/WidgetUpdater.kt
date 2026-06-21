package com.itexpert120.yomu.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

/**
 * Pushes a fresh render to any placed "Continue reading" widgets. Called after reading progress is
 * saved so the home-screen tile tracks the user's latest position. No-ops cheaply when no widget is
 * placed (updateAll iterates the empty id set).
 */
object WidgetUpdater {
    suspend fun refreshContinueReading(context: Context) {
        runCatching {
            // Touch the manager so a missing provider (e.g. during tests) fails gracefully.
            GlanceAppWidgetManager(context.applicationContext)
            ContinueReadingWidget().updateAll(context.applicationContext)
        }
    }
}
