package com.itexpert120.yomu.data.readaloud

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.annotation.StringRes
import com.itexpert120.yomu.R

/** What to tell the reader when read-aloud stops with [this]. */
@get:StringRes
val ReadAloudError.messageRes: Int
    get() = when (this) {
        ReadAloudError.EngineUnavailable -> R.string.read_aloud_error_engine_unavailable
        is ReadAloudError.MissingVoiceData -> R.string.read_aloud_error_missing_voice
        is ReadAloudError.LanguageNotSupported -> R.string.read_aloud_error_language_not_supported
        ReadAloudError.NothingToRead -> R.string.read_aloud_error_nothing_to_read
        ReadAloudError.SourceFailed -> R.string.read_aloud_error_source_failed
        ReadAloudError.SpeechFailed -> R.string.read_aloud_error_speech_failed
    }

/**
 * Opens the TTS engine's voice-data installer (when [canInstallVoiceData]); label the button with
 * `R.string.read_aloud_install_voice`. Start it in a try/catch and fall back to
 * [readAloudTtsSettingsIntent]: a device without an engine has no installer.
 */
fun readAloudInstallVoiceDataIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The system text-to-speech settings (engine, default voice). */
fun readAloudTtsSettingsIntent(): Intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
