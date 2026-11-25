package com.itexpert120.yomu.data.readaloud

import kotlinx.coroutines.flow.Flow
import java.util.Locale
import javax.inject.Qualifier

/** The scope [ReadAloudPlayer] runs on: the main thread, outliving any screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReadAloudScope

/** A text-to-speech engine, one utterance at a time in a FIFO queue (Android's `TextToSpeech`). */
interface SpeechEngine {
    /** Starts the engine; false when no engine is available. */
    suspend fun initialize(): Boolean

    /** Selects the language (null = device default) and, when installed, the named voice for it. */
    fun setLanguage(languageTag: String?, voiceName: String?): SpeechLanguage

    fun setRate(rate: Float)

    fun setPitch(pitch: Float)

    /** Voices for [languageTag]'s language (all voices when null). */
    fun voices(languageTag: String?): List<ReadAloudVoice>

    /** Queues [text] after anything already queued; false when the engine refused it. */
    fun speak(utteranceId: String, text: String): Boolean

    /** Drops everything queued and stops speaking. */
    fun stop()

    fun shutdown()

    /** Callbacks may arrive on any thread. */
    interface Listener {
        fun onStart(utteranceId: String)

        /** The word [start] until [end] (UTF-16 offsets into the utterance's text) is being spoken. */
        fun onRange(utteranceId: String, start: Int, end: Int)

        fun onDone(utteranceId: String)

        fun onError(utteranceId: String)
    }

    fun interface Factory {
        fun create(listener: Listener): SpeechEngine
    }
}

enum class SpeechLanguage { Available, MissingData, NotSupported }

/** Audio focus and output routing for read-aloud. */
interface ReadAloudAudioOutput {
    /** Asks for audio focus; false when another app keeps it (a call, for example). */
    fun requestFocus(): Boolean

    fun abandonFocus()

    /** What happened to the output while focus was held. */
    val interruptions: Flow<AudioInterruption>
}

enum class AudioInterruption {
    /** Another app took the audio for good. */
    Loss,

    /** Another app took the audio for a while (a call, a navigation prompt). */
    TransientLoss,

    /** The audio came back after a [TransientLoss]. */
    Gain,

    /** Headphones were unplugged or a Bluetooth headset disconnected. */
    BecomingNoisy,
}

/** Keeps playback alive outside the app (the media session service). */
fun interface ReadAloudHost {
    fun ensureRunning()
}

/** Persisted read-aloud settings. */
data class ReadAloudSettings(
    val rate: Float = 1f,
    val pitch: Float = 1f,
    /** Chosen voice name per primary language subtag ("en", "fr"). */
    val voices: Map<String, String> = emptyMap(),
) {
    companion object {
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 3f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2f
    }
}

interface ReadAloudSettingsStore {
    val settings: Flow<ReadAloudSettings>

    suspend fun setRate(rate: Float)

    suspend fun setPitch(pitch: Float)

    /** [voiceName] null returns the language to the engine's default voice. */
    suspend fun setVoice(language: String, voiceName: String?)
}

/** The key [ReadAloudSettings.voices] uses for [languageTag] (null = the device language). */
fun readAloudVoiceKey(languageTag: String?): String {
    val locale = languageTag?.let(Locale::forLanguageTag)?.takeIf { it.language.isNotEmpty() }
    return (locale ?: Locale.getDefault()).language.lowercase(Locale.ROOT)
}
