package com.itexpert120.yomu.data.readaloud

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

/** [SpeechEngine] over the device's default `TextToSpeech` engine, speaking plain text. */
class AndroidSpeechEngine(
    private val context: Context,
    private val listener: SpeechEngine.Listener,
) : SpeechEngine {
    private var tts: TextToSpeech? = null

    override suspend fun initialize(): Boolean = suspendCancellableCoroutine { continuation ->
        // The init callback can run inside the constructor (no engine installed), before `tts` is set.
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (continuation.isActive) continuation.resume(status == TextToSpeech.SUCCESS)
        }
        tts = engine
        engine.setAudioAttributes(readAloudAudioAttributes)
        engine.setOnUtteranceProgressListener(progress)
        continuation.invokeOnCancellation { engine.shutdown() }
    }

    override fun setLanguage(languageTag: String?, voiceName: String?): SpeechLanguage {
        val engine = tts ?: return SpeechLanguage.NotSupported
        val locale = languageTag?.let(Locale::forLanguageTag)?.takeIf { it.language.isNotEmpty() } ?: Locale.getDefault()
        if (voiceName != null) {
            val voice = engine.safeVoices().firstOrNull { it.name == voiceName }
            if (voice != null && voice.isInstalled && engine.setVoice(voice) == TextToSpeech.SUCCESS) return SpeechLanguage.Available
        }
        return when (runCatching { engine.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)) {
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> SpeechLanguage.Available
            TextToSpeech.LANG_MISSING_DATA -> SpeechLanguage.MissingData
            else -> SpeechLanguage.NotSupported
        }
    }

    override fun setRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    override fun voices(languageTag: String?): List<ReadAloudVoice> {
        val engine = tts ?: return emptyList()
        val wanted = languageTag?.let(Locale::forLanguageTag)?.takeIf { it.language.isNotEmpty() }
        return engine.safeVoices()
            .filter { wanted == null || it.locale.sameLanguageAs(wanted) }
            .map {
                ReadAloudVoice(
                    name = it.name,
                    languageTag = it.locale.toLanguageTag(),
                    quality = it.quality,
                    requiresNetwork = it.isNetworkConnectionRequired,
                    installed = it.isInstalled,
                )
            }
            .sortedWith(compareBy<ReadAloudVoice>({ !it.installed }, { -it.quality }, { it.languageTag }, { it.name }))
    }

    override fun speak(utteranceId: String, text: String): Boolean {
        val engine = tts ?: return false
        // Segments are short by contract; this only guards the engine's hard limit.
        val spoken = text.take(TextToSpeech.getMaxSpeechInputLength())
        return engine.speak(spoken, TextToSpeech.QUEUE_ADD, null, utteranceId) == TextToSpeech.SUCCESS
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.setOnUtteranceProgressListener(null)
        tts?.shutdown()
        tts = null
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = listener.onStart(utteranceId)

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) = listener.onRange(utteranceId, start, end)

        override fun onDone(utteranceId: String) = listener.onDone(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = listener.onError(utteranceId)

        override fun onError(utteranceId: String, errorCode: Int) = listener.onError(utteranceId)

        // Interrupted by stop(): the player already dropped these utterances.
        override fun onStop(utteranceId: String, interrupted: Boolean) = Unit
    }

    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
    ) : SpeechEngine.Factory {
        override fun create(listener: SpeechEngine.Listener): SpeechEngine = AndroidSpeechEngine(context, listener)
    }
}

// Some engines throw from getVoices() while (re)binding; treat that as no voices.
private fun TextToSpeech.safeVoices(): Set<Voice> = runCatching { voices }.getOrNull().orEmpty()

private val Voice.isInstalled: Boolean
    get() = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty()

// Engines report either two- or three-letter language codes.
private fun Locale.sameLanguageAs(other: Locale): Boolean {
    if (language.equals(other.language, ignoreCase = true)) return true
    val mine = runCatching { isO3Language }.getOrNull() ?: return false
    val theirs = runCatching { other.isO3Language }.getOrNull() ?: return false
    return mine.equals(theirs, ignoreCase = true)
}
