package com.itexpert120.yomu.prototype

import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.itexpert120.yomu.reader.epub.EpubBook
import com.itexpert120.yomu.reader.ui.PagedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Debug-only prototype of the native reading engine (v2). Opens an imported EPUB with no WebView and
 * logs timings under the "NativeReader" tag.
 *
 * adb shell am start -n com.itexpert120.yomu/.prototype.NativeReaderPrototypeActivity --ei chapter 124
 */
class NativeReaderPrototypeActivity : ComponentActivity() {
    private val started = SystemClock.elapsedRealtime()

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
        val typeface = Typeface.createFromAsset(assets, "fonts/Lora-Regular.ttf")
        var book by mutableStateOf<EpubBook?>(null)
        lifecycleScope.launch {
            book = withContext(Dispatchers.IO) { EpubBook.open(File(path)) }.also { openedBook = it }
            Log.i(TAG, "book opened +${SystemClock.elapsedRealtime() - started} ms (${book?.spine?.size} spine items)")
        }
        setContent {
            Box(Modifier.fillMaxSize().background(Background)) {
                val opened = book
                if (opened != null) {
                    var reported by remember { mutableStateOf(false) }
                    PagedReader(
                        book = opened,
                        startChapter = chapter,
                        typeface = typeface,
                        background = Background,
                        textColor = Ink,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                        onFirstPage = {
                            if (!reported) {
                                reported = true
                                Log.i(TAG, "first page drawn +${SystemClock.elapsedRealtime() - started} ms")
                                reportFullyDrawn()
                            }
                        },
                    )
                }
            }
        }
    }

    // The book's zip stays open while reading; it's released with the screen.
    private var openedBook: EpubBook? = null

    override fun onDestroy() {
        super.onDestroy()
        openedBook?.close()
    }

    private companion object {
        const val TAG = "NativeReader"
        val Background = Color(0xFF1A1C20)
        val Ink = Color(0xFFE2E3E6)
    }
}
