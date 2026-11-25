package com.itexpert120.yomu.feature.reader

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

    /** Whether an app on the device can translate text ([translate] would go somewhere). */
    val canTranslate: Boolean get() = false

    /** Hands [text] to the device's translation app; the text goes only to that app. */
    fun translate(text: String) = Unit
}

/**
 * A quotation with its source: `“text” — Author, Title`, plus `, p. N` when the print page is
 * known. Quote marks the selection already has aren't doubled.
 */
internal fun formatCitation(text: String, author: String?, title: String?, page: String? = null): String {
    val quote = text.trim().trim('"', '“', '”', '‘', '’', '\'').trim()
    val source = listOfNotNull(
        author?.trim()?.takeIf { it.isNotEmpty() },
        title?.trim()?.takeIf { it.isNotEmpty() },
        page?.trim()?.takeIf { it.isNotEmpty() }?.let { "p. $it" },
    ).joinToString(", ")
    return if (source.isEmpty()) "“$quote”" else "“$quote” — $source"
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

    override val canTranslate: Boolean get() = translateIntent("") != null

    override fun translate(text: String) {
        val intent = translateIntent(text) ?: return
        val activity = ReaderHostActivity.activity
        if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            (activity ?: context).startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Uninstalled since it was found.
        } catch (_: SecurityException) {
            // Not launchable after all.
        }
    }

    // ACTION_TRANSLATE (Android 10+) where an app takes it, else Google Translate's "Translate"
    // text action. Both need the manifest's <queries> entries on Android 11+.
    private fun translateIntent(text: String): Intent? {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val translate = Intent(Intent.ACTION_TRANSLATE).putExtra(Intent.EXTRA_TEXT, text)
            if (pm.queryIntentActivities(translate, 0).isNotEmpty()) return translate
        }
        val processText = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setPackage(GOOGLE_TRANSLATE)
        val target = pm.queryIntentActivities(processText, 0).firstOrNull()?.activityInfo ?: return null
        return Intent(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .setClassName(target.packageName, target.name)
            .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }

    private companion object {
        const val GOOGLE_TRANSLATE = "com.google.android.apps.translate"
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
