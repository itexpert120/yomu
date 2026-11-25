package com.itexpert120.yomu.prototype

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.itexpert120.yomu.reader.web.FoliateReader
import java.io.File

/**
 * Debug-only prototype of the foliate-js engine (v2). Opens an imported EPUB in one WebView and logs
 * timings under the "FoliateReader" tag.
 *
 * adb shell am start -n com.itexpert120.yomu/.prototype.FoliatePrototypeActivity --ei chapter 126
 */
class FoliatePrototypeActivity : ComponentActivity() {
    private val started = SystemClock.elapsedRealtime()
    private var reader: FoliateReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light page: dark bar icons.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val chapter = intent.getIntExtra("chapter", 0)
        val path = intent.getStringExtra("path")
            ?: File(filesDir, "epubs").walkTopDown().firstOrNull { it.extension == "epub" }?.path
        if (path == null) {
            Log.e(TAG, "No imported EPUB found")
            finish()
            return
        }
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.WHITE) }
        val reader = FoliateReader(this, File(path)) { name, json ->
            Log.i(TAG, "$name +${SystemClock.elapsedRealtime() - started} ms $json")
            if (name == "first-page") runOnUiThread { reportFullyDrawn() }
        }.also { reader = it }
        Log.i(TAG, "webview created +${SystemClock.elapsedRealtime() - started} ms")
        root.addView(reader.webView)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        val extra = intent.getStringExtra("params")?.split('&')?.associate { it.substringBefore('=') to it.substringAfter('=', "") }.orEmpty()
        reader.open(chapter, extra)
    }

    override fun onDestroy() {
        super.onDestroy()
        reader?.close()
    }

    private companion object {
        const val TAG = "FoliateReader"
    }
}
