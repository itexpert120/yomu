package com.itexpert120.yomu.reader.web

import android.annotation.SuppressLint
import android.content.Context
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
 * Page turns are drawn natively over the WebView (see [FoliatePageTurns]). [onEvent] gets the
 * page's events (`relocate`, `first-page`, `center-tap`, …) on the main thread. [context] must be
 * an Activity: turns snapshot its window.
 */
class FoliateReader(
    context: Context,
    epub: File,
    private val onEvent: (name: String, json: String) -> Unit,
    private val onRendererGone: () -> Unit = {},
) : AutoCloseable {
    private val zip = ZipFile(epub)

    /** Font files outside the app's assets (user-installed fonts), served at `/userfonts/<name>`. */
    @Volatile
    var userFonts: Map<String, File> = emptyMap()

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/book/", BookPathHandler(zip))
        .addPathHandler("/userfonts/", UserFontPathHandler())
        .build()

    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.setSupportZoom(false)
        settings.textZoom = 100
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
                main.post(onRendererGone)
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
    val view: FrameLayout = FrameLayout(context).apply { addView(webView) }

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

    /**
     * Opens the book. [configJson] is `{ start, settings }` as described in yomu-reader.js; [extra]
     * goes to the page as query parameters (debug switches).
     */
    fun open(configJson: String, extra: Map<String, String> = emptyMap()) {
        val url = Uri.parse("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/foliate/yomu-reader.html").buildUpon()
            .appendQueryParameter("config", configJson)
            .apply { extra.forEach { (key, value) -> appendQueryParameter(key, value) } }
            .build()
        webView.loadUrl(url.toString())
    }

    /** Opens the book at the start of spine item [section], with the page's default settings. */
    fun open(section: Int, extra: Map<String, String> = emptyMap()) = open("{\"start\":{\"index\":$section,\"fraction\":0}}", extra)

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
        zip.close()
    }

    private fun onPageEvent(name: String, json: String) {
        when (name) {
            "tap" -> turns.tap(JSONObject(json).getInt("dir"))
            "jumped" -> JSONObject(json).let { turns.onJumped(it.getInt("id"), it.getBoolean("moved")) }
            "settled" -> turns.onSettled(JSONObject(json).getInt("seq"))
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

    /** Serves `/book/<entry>` from the EPUB, and `/book/.sizes` as every entry's size. */
    private class BookPathHandler(private val zip: ZipFile) : WebViewAssetLoader.PathHandler {
        private val sizes: String by lazy {
            zip.entries().asSequence().filterNot { it.isDirectory }
                .joinToString(",", "{", "}") { "${quote(it.name)}:${it.size}" }
        }

        override fun handle(path: String): WebResourceResponse {
            if (path == ".sizes") return response("application/json", sizes.toByteArray())
            val entry = zip.getEntry(path) ?: return notFound()
            return WebResourceResponse(mimeType(path), null, zip.getInputStream(entry))
        }

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
