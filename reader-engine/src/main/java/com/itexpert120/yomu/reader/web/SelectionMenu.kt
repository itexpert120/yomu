package com.itexpert120.yomu.reader.web

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/** What the reader's text-selection menu offers, in menu order. */
enum class SelectionAction(val title: String) {
    Copy("Copy"),
    Highlight("Highlight"),
    LookUp("Look up"),
    SearchInBook("Search in book"),
    SearchWeb("Search web"),
    ReadAloud("Read aloud"),
    Share("Share"),
    CopyWithCitation("Copy with citation"),
}

/**
 * Replaces Chromium's text-selection menu with the reader's own actions, followed by the text
 * actions other apps offer (`ACTION_PROCESS_TEXT`: Translate, dictionary apps). Chromium still owns
 * the selection, the handles and where the floating toolbar sits; only the items change. A pick of
 * ours reads the selection from the page (`yomuSelection()`), hands it to [onAction] with the
 * action, and ends the selection; another app's gets the selected text, read-only.
 */
internal class SelectionMenu(
    private val webView: WebView,
    private val onAction: (SelectionAction, JSONObject) -> Unit,
    private val hideLookUp: (String) -> Boolean,
) {
    fun wrap(callback: ActionMode.Callback): ActionMode.Callback = Wrapped(callback)

    private inner class Wrapped(private val chromium: ActionMode.Callback) : ActionMode.Callback2() {
        // "Look up" is for single words; cached because reading the selection is asynchronous.
        private var lookUpHidden = false

        // Other apps' text actions, looked up once per selection.
        private var textApps: List<TextApp> = emptyList()

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val created = chromium.onCreateActionMode(mode, menu)
            textApps = queryTextApps(webView.context)
            replaceItems(menu)
            return created
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Chromium rebuilds its items as the selection changes; put ours back each time.
            chromium.onPrepareActionMode(mode, menu)
            replaceItems(menu)
            selectionText { text ->
                val hide = text != null && hideLookUp(text)
                if (hide != lookUpHidden) {
                    lookUpHidden = hide
                    mode.invalidate()
                }
            }
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val app = textApps.getOrNull(item.itemId - APP_BASE)
            if (app != null) {
                selectionText { text ->
                    if (!text.isNullOrBlank()) launchTextApp(webView.context, app, text)
                    mode.finish()
                    webView.evaluateJavascript("globalThis.yomuClearSelection?.()", null)
                }
                return true
            }
            val action = SelectionAction.entries.getOrNull(item.itemId - MENU_BASE) ?: return chromium.onActionItemClicked(mode, item)
            webView.evaluateJavascript("globalThis.yomuSelection?.() ?? null") { result ->
                val selection = runCatching { JSONArray("[$result]").optJSONObject(0) }.getOrNull()
                if (selection != null && selection.optString("text").isNotBlank()) onAction(action, selection)
                mode.finish()
                webView.evaluateJavascript("globalThis.yomuClearSelection?.()", null)
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = chromium.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            if (chromium is ActionMode.Callback2) chromium.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }

        private fun replaceItems(menu: Menu) {
            menu.clear()
            SelectionAction.entries.forEachIndexed { order, action ->
                if (action == SelectionAction.LookUp && lookUpHidden) return@forEachIndexed
                menu.add(Menu.NONE, MENU_BASE + action.ordinal, order, action.title)
            }
            textApps.forEachIndexed { index, app ->
                menu.add(Menu.NONE, APP_BASE + index, SelectionAction.entries.size + index, app.label)
            }
        }

        private fun selectionText(then: (String?) -> Unit) {
            webView.evaluateJavascript("globalThis.yomuSelection?.()?.text ?? null") { result ->
                then(runCatching { JSONArray("[$result]").opt(0) as? String }.getOrNull())
            }
        }
    }

    /** An app that takes selected text (`ACTION_PROCESS_TEXT`), e.g. Google Translate's "Translate". */
    private class TextApp(val label: CharSequence, val packageName: String, val activityName: String)

    private companion object {
        const val MENU_BASE = 0x5E1
        const val APP_BASE = 0x6E1

        // Needs the manifest's <queries> entry for ACTION_PROCESS_TEXT on Android 11+.
        fun queryTextApps(context: Context): List<TextApp> = runCatching {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), 0)
                .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.exported }
                .map { info: ResolveInfo -> TextApp(info.loadLabel(pm), info.activityInfo.packageName, info.activityInfo.name) }
                .distinctBy { it.packageName to it.activityName }
                .sortedBy { it.label.toString().lowercase() }
        }.getOrDefault(emptyList())

        fun launchTextApp(context: Context, app: TextApp, text: String) {
            val intent = Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .setClassName(app.packageName, app.activityName)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                // The book can't be edited: apps show their result instead of replacing the text.
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // Uninstalled since the menu opened.
            } catch (_: SecurityException) {
                // Not launchable after all.
            }
        }

        fun Context.findActivity(): Activity? {
            var current: Context? = this
            while (current is ContextWrapper) {
                if (current is Activity) return current
                current = current.baseContext
            }
            return null
        }
    }
}
