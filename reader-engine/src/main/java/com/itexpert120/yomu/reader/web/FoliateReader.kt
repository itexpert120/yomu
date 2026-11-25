package com.itexpert120.yomu.reader.web

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
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
import java.util.zip.ZipFile

/**
 * Hosts foliate-js in a WebView. Everything is served from one local origin: the engine's scripts
 * and the app's fonts from assets, and the book's files straight out of the EPUB, one at a time.
 * Nothing else loads — any other request gets an empty 404 — and the page's CSP lets only our own
 * scripts run, so scripts inside books never do.
 */
class FoliateReader(
    context: Context,
    epub: File,
    private val onEvent: (name: String, json: String) -> Unit,
) : AutoCloseable {
    private val zip = ZipFile(epub)

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/book/", BookPathHandler(zip))
        .build()

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

    private val main = Handler(Looper.getMainLooper())

    private val turns = FoliatePageTurns(
        container = view,
        webView = webView,
        jump = { dir, id -> webView.evaluateJavascript("globalThis.yomuJump($dir, $id)", null) },
        log = { Log.i(TAG, it) },
    )

    /** Opens the book at spine [section]; [extra] goes to the page as query parameters. */
    fun open(section: Int, extra: Map<String, String> = emptyMap()) {
        val url = Uri.parse("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/foliate/yomu-reader.html").buildUpon()
            .appendQueryParameter("section", section.toString())
            .apply { extra.forEach { (key, value) -> appendQueryParameter(key, value) } }
            .build()
        webView.loadUrl(url.toString())
    }

    fun next() = turns.tap(1)

    fun prev() = turns.tap(-1)

    override fun close() {
        turns.release()
        webView.destroy()
        zip.close()
    }

    private fun onPageEvent(name: String, json: JSONObject) {
        when (name) {
            "tap" -> turns.tap(json.getInt("dir"))
            "jumped" -> turns.onJumped(json.getInt("id"), json.getBoolean("moved"))
            "settled" -> turns.onSettled(json.getInt("seq"))
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun event(name: String, json: String) {
            when (name) {
                "tap", "jumped", "settled" -> main.post { onPageEvent(name, JSONObject(json)) }
            }
            onEvent(name, json)
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
