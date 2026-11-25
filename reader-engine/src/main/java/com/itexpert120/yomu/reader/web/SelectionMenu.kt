package com.itexpert120.yomu.reader.web

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
    SearchWeb("Search web"),
    ReadAloud("Read aloud"),
    Share("Share"),
}

/**
 * Replaces Chromium's text-selection menu with the reader's own actions. Chromium still owns the
 * selection, the handles and where the floating toolbar sits; only the items change. A pick reads
 * the selection from the page (`yomuSelection()`), hands it to [onAction] with the action, and
 * ends the selection.
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

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val created = chromium.onCreateActionMode(mode, menu)
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
        }

        private fun selectionText(then: (String?) -> Unit) {
            webView.evaluateJavascript("globalThis.yomuSelection?.()?.text ?? null") { result ->
                then(runCatching { JSONArray("[$result]").opt(0) as? String }.getOrNull())
            }
        }
    }

    private companion object {
        const val MENU_BASE = 0x5E1
    }
}
