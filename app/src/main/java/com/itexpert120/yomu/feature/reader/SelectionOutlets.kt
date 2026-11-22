package com.itexpert120.yomu.feature.reader

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.browser.customtabs.CustomTabsIntent
import com.itexpert120.yomu.core.web.openWebSearchSheet
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject

/** Where selected text can go outside the book. */
interface SelectionOutlet {
    fun copy(text: String)
    fun share(text: String)
    fun searchWeb(query: String)
}

/** Speaks reader text aloud. One per reading experience; [shutdown] when it ends. */
interface ReadAloud {
    fun speak(text: String)
    fun shutdown()
}

/** The Activity currently hosting the reader, held weakly; set by [ReaderNavigatorHost]. */
internal object ReaderHostActivity {
    private var current: WeakReference<Activity>? = null

    val activity: Activity? get() = current?.get()

    fun attach(activity: Activity) {
        current = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (current?.get() === activity) current = null
    }
}

class AndroidSelectionOutlet @Inject constructor(
    @ApplicationContext private val context: Context,
) : SelectionOutlet {
    override fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Selection", text))
    }

    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    override fun searchWeb(query: String) {
        // Prefer the bottom-sheet tab, which needs the hosting Activity; fall back to full screen.
        ReaderHostActivity.activity?.let {
            it.openWebSearchSheet(query)
            return
        }
        val uri = Uri.parse("https://www.google.com/search").buildUpon()
            .appendQueryParameter("q", query)
            .build()
        val customTab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .also { it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            customTab.launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            // The device has no activity capable of handling web links.
        }
    }
}

/** Text-to-speech, created on first use and reused until [shutdown]. */
class AndroidReadAloud(private val context: Context) : ReadAloud {
    private var tts: TextToSpeech? = null
    private var pending: String? = null

    override fun speak(text: String) {
        val existing = tts
        if (existing != null) {
            existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            return
        }
        // First use: TTS init is async, so remember the text and speak once the engine is ready.
        pending = text
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                pending?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) }
            }
            pending = null
        }
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val UTTERANCE_ID = "yomu-reader"
    }
}
