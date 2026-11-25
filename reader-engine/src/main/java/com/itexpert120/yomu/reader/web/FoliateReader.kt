package com.itexpert120.yomu.reader.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.webkit.WebViewAssetLoader
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
 * Page turns are drawn natively over the WebView (see [FoliatePageTurns]); they need an Activity,
 * so a reader must be [attachTo]'d one before it's shown. [onEvent] gets the page's events
 * (`relocate`, `first-page`, `center-tap`, …) on the main thread.
 */
class FoliateReader(
    context: Context,
    pageParams: Map<String, String> = emptyMap(),
) : AutoCloseable {
    private val contextWrapper = MutableContextWrapper(context)

    var onEvent: (name: String, json: String) -> Unit = { _, _ -> }
    var onRendererGone: () -> Unit = {}

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
    val webView: WebView = WebView(contextWrapper).apply {
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

    private val turns = FoliatePageTurns(
        container = view,
        webView = webView,
        jump = { dir, id -> webView.evaluateJavascript("globalThis.yomuJump($dir, $id)", null) },
        log = { Log.i(TAG, it) },
    )

    /** Native page turns (paged layout). Off in scrolled layout, where the page scrolls itself. */
    var pageTurns: Boolean
        get() = turns.enabled
        set(value) {
            turns.enabled = value
        }

    init {
        val url = Uri.parse("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/foliate/yomu-reader.html").buildUpon()
            .apply { pageParams.forEach { (key, value) -> appendQueryParameter(key, value) } }
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

    private fun call(js: String) {
        if (pageReady) webView.evaluateJavascript(js, null) else pendingCalls += js
    }

    /** Opens [epub] at the start of spine item [section], with the page's default settings. */
    fun open(epub: File, section: Int) = open(epub, "{\"start\":{\"index\":$section,\"fraction\":0}}")

    /** Restyles the page; [settingsJson] as `settings` in [open]. */
    fun applySettings(settingsJson: String) = webView.evaluateJavascript("globalThis.yomuApplySettings?.($settingsJson)", null)

    /** Jumps to [targetJson]: `{ cfi }`, `{ href }` or `{ index, fraction }`. */
    fun goTo(targetJson: String) = webView.evaluateJavascript("globalThis.yomuGoTo?.($targetJson)", null)

    fun next() = turns.tap(1)

    fun prev() = turns.tap(-1)

    override fun close() {
        turns.release()
        (view.parent as? ViewGroup)?.removeView(view)
        webView.destroy()
        zip?.close()
        zip = null
    }

    private fun onPageEvent(name: String, json: String) {
        when (name) {
            "tap" -> turns.tap(JSONObject(json).getInt("dir"))
            "jumped" -> JSONObject(json).let { turns.onJumped(it.getInt("id"), it.getBoolean("moved")) }
            "settled" -> turns.onSettled(JSONObject(json).getInt("seq"))
            "shell-ready" -> {
                pageReady = true
                pendingCalls.forEach { webView.evaluateJavascript(it, null) }
                pendingCalls.clear()
            }
        }
        onEvent(name, json)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun event(name: String, json: String) {
            main.post { onPageEvent(name, json) }
        }
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
