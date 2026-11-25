package com.itexpert120.yomu.data.readaloud

import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReadAloudSegment

/** The book being read aloud, as the media notification and lock screen show it. */
data class ReadAloudBook(
    val id: BookId,
    val title: String,
    val author: String?,
    val coverPath: String?,
    /** The book's language (BCP-47), used for segments and chapters that don't name one. */
    val language: String?,
)

sealed interface ReadAloudState {
    data object Idle : ReadAloudState

    /** Starting: the TTS engine is initialising or the first chapter is being split. */
    data class Preparing(val book: ReadAloudBook) : ReadAloudState

    /** [range] is the word being spoken in [segment]'s text, when the engine reports words. */
    data class Playing(
        val book: ReadAloudBook,
        val segment: ReadAloudSegment,
        val chapterTitle: String?,
        val range: IntRange?,
    ) : ReadAloudState

    /** [segment] is where [ReadAloudPlayer.resume] continues from. */
    data class Paused(
        val book: ReadAloudBook,
        val segment: ReadAloudSegment,
        val chapterTitle: String?,
    ) : ReadAloudState

    data class Error(val book: ReadAloudBook?, val error: ReadAloudError) : ReadAloudState
}

sealed interface ReadAloudError {
    /** No TTS engine is installed or it failed to start. */
    data object EngineUnavailable : ReadAloudError

    /** The engine knows the language but its voice data isn't downloaded. */
    data class MissingVoiceData(val languageTag: String?) : ReadAloudError

    data class LanguageNotSupported(val languageTag: String?) : ReadAloudError

    /** The book has no readable text from the starting point on. */
    data object NothingToRead : ReadAloudError

    /** The reading page could not provide text (it closed or failed). */
    data object SourceFailed : ReadAloudError

    /** The engine kept failing to speak. */
    data object SpeechFailed : ReadAloudError
}

/** Whether installing TTS voice data (see [readAloudInstallVoiceDataIntent]) can fix [this]. */
val ReadAloudError.canInstallVoiceData: Boolean
    get() = this is ReadAloudError.EngineUnavailable ||
        this is ReadAloudError.MissingVoiceData ||
        this is ReadAloudError.LanguageNotSupported

sealed interface ReadAloudSleepTimer {
    data object Off : ReadAloudSleepTimer

    /** Pauses at the end of the sentence being read at [endsAtElapsedRealtime] (`SystemClock`). */
    data class Minutes(val minutes: Int, val endsAtElapsedRealtime: Long) : ReadAloudSleepTimer

    /** Pauses after the last sentence of the current chapter. */
    data object EndOfChapter : ReadAloudSleepTimer
}

sealed interface SleepTimerRequest {
    data object Off : SleepTimerRequest

    data class Minutes(val minutes: Int) : SleepTimerRequest

    data object EndOfChapter : SleepTimerRequest
}

/** A TTS voice the user can pick for a language. */
data class ReadAloudVoice(
    val name: String,
    val languageTag: String,
    /** `android.speech.tts.Voice.QUALITY_*`: higher is better. */
    val quality: Int,
    val requiresNetwork: Boolean,
    /** False when the voice's data still has to be downloaded by the engine. */
    val installed: Boolean,
)
