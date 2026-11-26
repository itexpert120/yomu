package com.itexpert120.yomu.reader.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ActionMode
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

/**
 * Hosts foliate-js in a WebView. Everything is served from one local origin: the engine's scripts
 * and the app's fonts from assets, user-installed fonts from [userFonts], and the book's files
 * straight out of the EPUB, one at a time. Nothing else loads — any other request gets an empty
 * 404 — and the page's CSP lets only our own scripts run, so scripts inside books never do.
 *
 * The page loads as soon as the reader is built, before any book: a reader can be made ahead of
 * time (on the application context) and adopted later with [attachTo] and [open], so opening a
 * book skips the WebView's creation and the page's script loading.
 *
 * Page turns are foliate's own. A reader built ahead must be [attachTo]'d an Activity before it's
 * shown. [onEvent] gets the page's events (`relocate`, `first-page`, `center-tap`, …) on the main
 * thread.
 */
class FoliateReader(
    context: Context,
    pageParams: Map<String, String> = emptyMap(),
) : AutoCloseable {
    private val contextWrapper = MutableContextWrapper(context)

    var onEvent: (name: String, json: String) -> Unit = { _, _ -> }
    var onRendererGone: () -> Unit = {}

    /** A pick from the text-selection menu, with the page's selection (`yomuSelection()`). */
    var onSelection: (SelectionAction, JSONObject) -> Unit = { _, _ -> }

    /** Selections this is true for are phrases: the menu hides "Look up". */
    var isLookupPhrase: (String) -> Boolean = { false }

    @Volatile
    private var zip: ZipFile? = null

    /** Font files outside the app's assets (user-installed fonts), served at `/userfonts/<name>`. */
    @Volatile
    var userFonts: Map<String, File> = emptyMap()

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context.applicationContext))
        .addPathHandler("/book/", BookPathHandler { zip })
        .addPathHandler("/userfonts/", UserFontPathHandler())
        .build()

    private val main = Handler(Looper.getMainLooper())

    // Calls made before the page has loaded wait here, in order.
    private var pageReady = false
    private val pendingCalls = ArrayList<String>()
    private var opened = false

    /** The book this reader holds (loaded ahead by [preload], or by [open]). */
    var bookFile: File? = null
        private set

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = ReaderWebView(contextWrapper).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.setSupportZoom(false)
        settings.textZoom = 100
        // The host's page colour shows until the book's own page paints.
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = WebView.OVER_SCROLL_NEVER
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = assetLoader.shouldInterceptRequest(request.url) ?: notFound()

            // Links inside books never navigate the page away. Chapter frames (blob: documents this
            // page creates) must still load: this is called for frames too.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.isForMainFrame || request.url.scheme != "blob"

            // The system kills renderers freely in the background; unhandled, that takes the app
            // down with it. The owner rebuilds the reader instead.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.w(TAG, "WebView renderer gone (crashed=${detail.didCrash()})")
                main.post { onRendererGone() }
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.i(TAG, "console ${message.messageLevel()}: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }
        addJavascriptInterface(Bridge(), "YomuHost")
    }

    /** The reader's view: the WebView with the native page-turn layer over it. */
    val view: FrameLayout = FrameLayout(contextWrapper).apply { addView(webView) }

    // The page's own safe area (see updateSafeArea), last sent, in px.
    private var safeArea: Pair<Int, Int>? = null

    init {
        // Debug builds: the page can be inspected from chrome://inspect.
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        webView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSafeArea() }
        // The view can also move without changing size (its container settling after an
        // animation), which changes how much of it the bars cover.
        webView.viewTreeObserver.addOnGlobalLayoutListener { updateSafeArea() }
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            view.post { updateSafeArea() }
            ViewCompat.onApplyWindowInsets(view, insets)
        }
        val url = Uri.parse("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/foliate/yomu-reader.html").buildUpon()
            .apply { pageParams.forEach { (key, value) -> appendQueryParameter(key, value) } }
            // Debug builds: the page logs what it decides (pictures on dark pages) to the console.
            .apply { if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) appendQueryParameter("debug", "1") }
            .build()
        webView.loadUrl(url.toString())
    }

    /** Moves the reader into [activity], e.g. one built ahead on the application context. */
    fun attachTo(activity: Activity) {
        contextWrapper.baseContext = activity
    }

    /**
     * Starts loading [epub] into the page before it's shown: no page size is needed, so this can
     * run ahead of a tap. A reader holds one book; a later [open] must be for the same file.
     */
    fun preload(epub: File) {
        if (bookFile != null) return
        bookFile = epub
        zip = ZipFile(epub)
        call("globalThis.yomuLoadBook()")
    }

    /**
     * Shows [epub] (loading it first unless [preload]ed), once per reader. [configJson] is
     * `{ start, settings }` as described in yomu-reader.js.
     */
    fun open(epub: File, configJson: String) {
        check(!opened) { "A FoliateReader opens one book" }
        check(bookFile == null || bookFile == epub) { "This reader holds another book" }
        opened = true
        if (bookFile == null) {
            bookFile = epub
            zip = ZipFile(epub)
        }
        call("globalThis.yomuStart($configJson)")
    }

    /**
     * Tells the page how much of its top and bottom lies under the system bars (shown or not) or a
     * display cutout, as `env(safe-area-inset-*)` would if WebViews reported it. Things the page
     * draws at its edges keep clear of it.
     */
    private fun updateSafeArea() {
        val insets = ViewCompat.getRootWindowInsets(webView) ?: return
        val unsafe = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val location = IntArray(2).also(webView::getLocationInWindow)
        val windowHeight = webView.rootView.height
        val top = (maxOf(unsafe.top, cutout.top) - location[1]).coerceAtLeast(0)
        val bottom = (maxOf(unsafe.bottom, cutout.bottom) - (windowHeight - location[1] - webView.height)).coerceAtLeast(0)
        if (safeArea == top to bottom) return
        safeArea = top to bottom
        val density = webView.resources.displayMetrics.density
        call("globalThis.yomuSetSafeArea?.({top:${top / density},bottom:${bottom / density}})")
    }

    private fun call(js: String) {
        if (pageReady) webView.evaluateJavascript(js, null) else pendingCalls += js
    }

    /** Opens [epub] at the start of spine item [section], with the page's default settings. */
    fun open(epub: File, section: Int) = open(epub, "{\"start\":{\"index\":$section,\"fraction\":0}}")

    /** Draws [highlightsJson] (`[{ id, cfi, color }]`), replacing the drawn set. */
    fun setHighlights(highlightsJson: String) = call("globalThis.yomuSetHighlights?.($highlightsJson)")

    /** Restyles the page; [settingsJson] as `settings` in [open]. */
    fun applySettings(settingsJson: String) = webView.evaluateJavascript("globalThis.yomuApplySettings?.($settingsJson)", null)

    /** Jumps to [targetJson]: `{ cfi }`, `{ href }` or `{ index, fraction }`. */
    fun goTo(targetJson: String) = webView.evaluateJavascript("globalThis.yomuGoTo?.($targetJson)", null)

    /**
     * Searches the book: [requestJson] is `{ id, query, sections, limit }` (see yomuSearch in
     * yomu-reader.js); hits arrive through [onEvent] as `search-hits`, then `search-done`.
     */
    fun search(requestJson: String) = call("globalThis.yomuSearch?.($requestJson)")

    fun cancelSearch() = call("globalThis.yomuCancelSearch?.()")

    /** Underlines search hits [cfisJson] (a JSON array of CFIs), replacing the previous set. */
    fun setSearchHits(cfisJson: String) = call("globalThis.yomuSetSearchHits?.($cfisJson)")

    /** Runs [js] in the page; [onResult] gets its JSON result, or null before the page has loaded. */
    fun evaluate(js: String, onResult: (String?) -> Unit) {
        if (pageReady) webView.evaluateJavascript(js) { onResult(it) } else onResult(null)
    }

    /**
     * Kills a renderer that stopped responding, so [onRendererGone] rebuilds as after a crash (a new
     * page would share the hung renderer). False when the WebView can't do that.
     */
    fun terminateRenderer(): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_RENDERER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)
        ) {
            return false
        }
        return WebViewCompat.getWebViewRenderProcess(webView)?.terminate() ?: false
    }

    fun next() = call("document.querySelector('foliate-view')?.next()")

    fun prev() = call("document.querySelector('foliate-view')?.prev()")

    override fun close() {
        (view.parent as? ViewGroup)?.removeView(view)
        webView.destroy()
        zip?.close()
        zip = null
    }

    private fun onPageEvent(name: String, json: String) {
        when (name) {
            "shell-ready" -> {
                pageReady = true
                pendingCalls.forEach { webView.evaluateJavascript(it, null) }
                pendingCalls.clear()
            }
        }
        onEvent(name, json)
    }

    private val selectionMenu by lazy {
        SelectionMenu(webView, onAction = { action, selection -> onSelection(action, selection) }, hideLookUp = { isLookupPhrase(it) })
    }

    /** The WebView, with the reader's own text-selection menu in place of Chromium's. */
    private inner class ReaderWebView(context: Context) : WebView(context) {
        override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? = super.startActionMode(callback?.let(selectionMenu::wrap), type)

        override fun startActionMode(callback: ActionMode.Callback?): ActionMode? = super.startActionMode(callback?.let(selectionMenu::wrap))
    }

    private inner class Bridge {
        @JavascriptInterface
        fun event(name: String, json: String) {
            main.post { onPageEvent(name, json) }
        }

        /** Whether the picture the page measured ([json]: `pictureStats`) is line art to invert on dark pages. */
        @JavascriptInterface
        fun pictureTone(json: String): Boolean = runCatching {
            val s = JSONObject(json)
            PictureTone.invertsOnDarkPages(
                PictureStats(
                    opaque = s.getDouble("opaque"),
                    saturation = s.getDouble("saturation"),
                    colorful = s.getDouble("colorful"),
                    dark = s.getDouble("dark"),
                    light = s.getDouble("light"),
                    corners = s.getDouble("corners"),
                    naturalWidth = s.getInt("naturalWidth"),
                    naturalHeight = s.getInt("naturalHeight"),
                    renderedWidthLines = s.getDouble("renderedWidthLines"),
                    renderedHeightLines = s.getDouble("renderedHeightLines"),
                    decorationHint = s.optBoolean("decorationHint"),
                    illustrationHint = s.optBoolean("illustrationHint"),
                    emptyAlt = s.optBoolean("emptyAlt"),
                ),
            )
        }.getOrDefault(false)
    }

    /** Serves `/userfonts/<name>` from [userFonts]. */
    private inner class UserFontPathHandler : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val file = userFonts[path]?.takeIf { it.isFile } ?: return notFound()
            return WebResourceResponse(mimeType(file.name), null, FileInputStream(file))
        }
    }

    /** Serves `/book/<entry>` from the open EPUB, and `/book/.sizes` as every entry's size. */
    private class BookPathHandler(private val zip: () -> ZipFile?) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val zip = zip() ?: return notFound()
            if (path == ".sizes") return response("application/json", sizes(zip).toByteArray())
            val entry = zip.getEntry(path) ?: return notFound()
            return WebResourceResponse(mimeType(path), null, zip.getInputStream(entry))
        }

        private fun sizes(zip: ZipFile) = zip.entries().asSequence().filterNot { it.isDirectory }
            .joinToString(",", "{", "}") { "${quote(it.name)}:${it.size}" }

        private fun quote(value: String) = buildString {
            append('"')
            for (c in value) {
                when {
                    c == '"' || c == '\\' -> append('\\').append(c)
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }
    }

    private companion object {
        const val TAG = "FoliateReader"

        fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

        fun response(mime: String, bytes: ByteArray) = WebResourceResponse(mime, "utf-8", ByteArrayInputStream(bytes))

        fun mimeType(path: String) = when (path.substringAfterLast('.').lowercase()) {
            "xhtml", "xht" -> "application/xhtml+xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "xml", "opf", "ncx" -> "application/xml"
            "svg" -> "image/svg+xml"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "js" -> "text/javascript"
            else -> "application/octet-stream"
        }
    }
}
