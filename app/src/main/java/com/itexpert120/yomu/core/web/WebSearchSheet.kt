package com.itexpert120.yomu.core.web

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens a Google search for [query] in a partial Custom Tab: a resizable bottom sheet over the
 * app (a side sheet on wide screens) so the reader stays in view. Browsers without partial-tab
 * support fall back to a full-screen Custom Tab.
 */
fun Activity.openWebSearchSheet(query: String) {
    val uri = Uri.parse("https://www.google.com/search").buildUpon()
        .appendQueryParameter("q", query)
        .build()
    val metrics = resources.displayMetrics
    val tab = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setInitialActivityHeightPx(
            (metrics.heightPixels * SHEET_HEIGHT_FRACTION).toInt(),
            CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE,
        )
        // Width only applies from the side-sheet breakpoint up (tablets / landscape).
        .setInitialActivityWidthPx((metrics.widthPixels * SIDE_SHEET_WIDTH_FRACTION).toInt())
        .setActivitySideSheetBreakpointDp(SIDE_SHEET_BREAKPOINT_DP)
        .setToolbarCornerRadiusDp(TOOLBAR_CORNER_RADIUS_DP)
        .build()
    tab.intent.data = uri
    try {
        // Partial Custom Tabs only open as a sheet when launched for a result (or with a session).
        @Suppress("DEPRECATION")
        startActivityForResult(tab.intent, WEB_SEARCH_REQUEST_CODE)
    } catch (_: ActivityNotFoundException) {
        // The device has no activity capable of handling web links.
    }
}

private const val SHEET_HEIGHT_FRACTION = 0.7f
private const val SIDE_SHEET_WIDTH_FRACTION = 0.4f
private const val SIDE_SHEET_BREAKPOINT_DP = 840
private const val TOOLBAR_CORNER_RADIUS_DP = 16
private const val WEB_SEARCH_REQUEST_CODE = 0x5EA
