package com.itexpert120.yomu.prototype

import android.content.res.Configuration
import android.graphics.Color
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val chapter = intent.getIntExtra("chapter", 0)
        val path = intent.getStringExtra("path")
            ?: File(filesDir, "epubs").walkTopDown().firstOrNull { it.extension == "epub" }?.path
        if (path == null) {
            Log.e(TAG, "No imported EPUB found")
            finish()
            return
        }
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        // The reading page's colour, so the bars sit on the page.
        val page = if (night) Color.rgb(0x1A, 0x1E, 0x22) else Color.WHITE
        val root = FrameLayout(this).apply { setBackgroundColor(page) }
        val extra = intent.getStringExtra("params")?.split('&')?.associate { it.substringBefore('=') to it.substringAfter('=', "") }.orEmpty()
        val reader = FoliateReader(this, extra).also { reader = it }
        reader.onEvent = { name, json ->
            Log.i(TAG, "$name +${SystemClock.elapsedRealtime() - started} ms $json")
            if (name == "first-page") reportFullyDrawn()
        }
        Log.i(TAG, "webview created +${SystemClock.elapsedRealtime() - started} ms")
        root.addView(reader.view)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        reader.open(File(path), chapter)
    }

    override fun onDestroy() {
        super.onDestroy()
        reader?.close()
    }

    private companion object {
        const val TAG = "FoliateReader"
    }
}
