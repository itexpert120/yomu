package com.itexpert120.yomu.data.readaloud

import android.os.SystemClock
import com.itexpert120.yomu.core.reader.ReadAloudChapter
import com.itexpert120.yomu.core.reader.ReadAloudSegment
import com.itexpert120.yomu.core.reader.ReadAloudSource
import com.itexpert120.yomu.core.reader.ReaderLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a book aloud continuously, sentence by sentence, across chapters.
 *
 * One player for the app. Commands may be called from any thread and run in order on the player's
 * scope; everything that changes playback runs there one step at a time, so engine callbacks,
 * commands, and loaded chapters never interleave. The TTS engine lives only while a book is being
 * read (or briefly after [voices]); [stop], the end of the book, an error, or a long pause release
 * it, along with audio focus.
 */
@Singleton
class ReadAloudPlayer internal constructor(
    private val scope: CoroutineScope,
    private val engineFactory: SpeechEngine.Factory,
    private val audio: ReadAloudAudioOutput,
    private val settingsStore: ReadAloudSettingsStore,
    private val host: ReadAloudHost,
    private val clock: () -> Long,
) {
    @Inject
    constructor(
        @ReadAloudScope scope: CoroutineScope,
        engineFactory: SpeechEngine.Factory,
        audio: ReadAloudAudioOutput,
        settingsStore: ReadAloudSettingsStore,
        host: ReadAloudHost,
    ) : this(scope, engineFactory, audio, settingsStore, host, SystemClock::elapsedRealtime)

    private val _state = MutableStateFlow<ReadAloudState>(ReadAloudState.Idle)
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()

    private val _sleepTimer = MutableStateFlow<ReadAloudSleepTimer>(ReadAloudSleepTimer.Off)
    val sleepTimer: StateFlow<ReadAloudSleepTimer> = _sleepTimer.asStateFlow()

    val settings: Flow<ReadAloudSettings> = settingsStore.settings

    private val ops = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val marks = Channel<Mark>(Channel.CONFLATED)

    private var engine: SpeechEngine? = null
    private var source: ReadAloudSource? = null
    private var book: ReadAloudBook? = null
    private var settingsValue = ReadAloudSettings()

    private var cursor: Pos? = null
    private var next: ReadAloudChapter? = null
    private var nextStatus = NextStatus.Unknown

    // The last segment of the chapter finished but the next chapter isn't known yet.
    private var pastChapterEnd = false

    // Where speaking resumes within the cursor's text: the start of the last word reported.
    private var wordOffset = 0
    private var playing = false
    private val queued = ArrayDeque<Queued>()
    private var serial = 0L
    private var consecutiveErrors = 0
    private var resumeOnFocusGain = false
    private var pauseAtSentenceEnd = false

    private var appliedLanguage: String? = null
    private var languageApplied = false
    private val unavailableLanguages = mutableSetOf<String>()

    private var prefetchJob: Job? = null
    private var sleepJob: Job? = null
    private var idleJob: Job? = null

    private val listener = object : SpeechEngine.Listener {
        override fun onStart(utteranceId: String) = post { onUtteranceStart(utteranceId) }

        override fun onRange(utteranceId: String, start: Int, end: Int) = post { onUtteranceRange(utteranceId, start, end) }

        override fun onDone(utteranceId: String) = post { onUtteranceDone(utteranceId, failed = false) }

        override fun onError(utteranceId: String) = post { onUtteranceDone(utteranceId, failed = true) }
    }

    init {
        scope.launch {
            for (op in ops) {
                try {
                    op()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fail(ReadAloudError.SourceFailed)
                }
            }
        }
        scope.launch {
            for (mark in marks) {
                try {
                    if (mark.source === source) mark.source.highlight(mark.segment, mark.range)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A page that can't draw the mark doesn't stop the reading.
                }
            }
        }
        scope.launch { settingsStore.settings.collect { post { onSettings(it) } } }
        scope.launch { audio.interruptions.collect { post { onInterruption(it) } } }
    }

    /**
     * Starts reading [book] from [from] (the source's current position when null), replacing
     * whatever was being read.
     */
    fun start(source: ReadAloudSource, book: ReadAloudBook, from: ReaderLocator?) = post {
        if (this.source != null) release()
        idleJob?.cancel()
        this.source = source
        this.book = book
        _state.value = ReadAloudState.Preparing(book)
        host.ensureRunning()
        val engine = ensureEngine() ?: return@post fail(ReadAloudError.EngineUnavailable)
        val first = try {
            loadFrom(source, from)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@post fail(ReadAloudError.SourceFailed)
        }
        if (first == null) return@post fail(ReadAloudError.NothingToRead)
        moveTo(Pos(first, first.startIndex.coerceIn(0, first.segments.lastIndex)))
        // Check the language before taking audio focus, so a missing voice is reported up front.
        if (!applyLanguage(engine, cursor!!)) return@post
        if (!audio.requestFocus()) return@post publishPaused()
        playing = true
        speakFromCursor()
    }

    fun pause() = post {
        resumeOnFocusGain = false
        pauseInternal(abandonFocus = true)
    }

    fun resume() = post { resumeInternal() }

    fun togglePlayPause() = post { if (playing) pauseInternal(abandonFocus = true) else resumeInternal() }

    /** The next sentence, into the next chapter at a chapter's end. */
    fun next() = post {
        val src = source ?: return@post
        val at = cursor ?: return@post
        val target = after(at) ?: run {
            val chapter = load { loadAfter(src, at.chapter) } ?: return@post
            Pos(chapter, 0)
        }
        moveTo(target)
        jumped()
    }

    /** The previous sentence, into the previous chapter at a chapter's start. */
    fun previous() = post {
        val src = source ?: return@post
        val at = cursor ?: return@post
        when {
            pastChapterEnd -> moveTo(at)
            at.index > 0 -> moveTo(Pos(at.chapter, at.index - 1))
            else -> {
                val chapter = load { loadBefore(src, at.chapter) }
                if (chapter == null) {
                    moveTo(at)
                } else {
                    moveTo(Pos(chapter, chapter.segments.lastIndex), knownNext = at.chapter)
                }
            }
        }
        jumped()
    }

    /** Continues from [locator] (a TOC entry, a bookmark, the page the user turned to). */
    fun seekTo(locator: ReaderLocator) = post {
        val src = source ?: return@post
        val chapter = load { loadFrom(src, locator) } ?: return@post
        moveTo(Pos(chapter, chapter.startIndex.coerceIn(0, chapter.segments.lastIndex)), forceNewChapter = true)
        jumped()
    }

    fun stop() = post { finish() }

    /** Stops if [source] is what is being read: call when its reading page closes. */
    fun detach(source: ReadAloudSource) = post { if (this.source === source) finish() }

    /** Clears an [ReadAloudState.Error] back to [ReadAloudState.Idle]. */
    fun dismissError() = post { if (_state.value is ReadAloudState.Error) _state.value = ReadAloudState.Idle }

    fun setSleepTimer(request: SleepTimerRequest) = post {
        sleepJob?.cancel()
        pauseAtSentenceEnd = false
        if (source == null) {
            _sleepTimer.value = ReadAloudSleepTimer.Off
            return@post
        }
        _sleepTimer.value = when (request) {
            SleepTimerRequest.Off -> ReadAloudSleepTimer.Off
            SleepTimerRequest.EndOfChapter -> ReadAloudSleepTimer.EndOfChapter
            is SleepTimerRequest.Minutes -> {
                val millis = request.minutes.coerceAtLeast(1) * 60_000L
                sleepJob = scope.launch {
                    delay(millis)
                    post { onSleepTimerElapsed() }
                }
                ReadAloudSleepTimer.Minutes(request.minutes, clock() + millis)
            }
        }
    }

    fun setRate(rate: Float) {
        scope.launch { settingsStore.setRate(rate.coerceIn(ReadAloudSettings.MIN_RATE, ReadAloudSettings.MAX_RATE)) }
    }

    fun setPitch(pitch: Float) {
        scope.launch { settingsStore.setPitch(pitch.coerceIn(ReadAloudSettings.MIN_PITCH, ReadAloudSettings.MAX_PITCH)) }
    }

    /** Picks [voiceName] for [languageTag]'s language (null = the engine's default voice). */
    fun setVoice(languageTag: String?, voiceName: String?) {
        scope.launch { settingsStore.setVoice(readAloudVoiceKey(languageTag), voiceName) }
    }

    /** The voices the engine offers for [languageTag]'s language; empty without an engine. */
    suspend fun voices(languageTag: String?): List<ReadAloudVoice> {
        val result = CompletableDeferred<List<ReadAloudVoice>>()
        post {
            val engine = ensureEngine()
            result.complete(engine?.voices(languageTag).orEmpty())
            if (source == null) scheduleRelease(UNUSED_ENGINE_RELEASE_MS)
        }
        return result.await()
    }

    private fun post(op: suspend () -> Unit) {
        ops.trySend(op)
    }

    private suspend fun ensureEngine(): SpeechEngine? {
        engine?.let { return it }
        settingsValue = settingsStore.settings.first()
        val created = engineFactory.create(listener)
        if (!created.initialize()) {
            created.shutdown()
            return null
        }
        created.setRate(settingsValue.rate)
        created.setPitch(settingsValue.pitch)
        languageApplied = false
        unavailableLanguages.clear()
        engine = created
        return created
    }

    private fun resumeInternal() {
        if (playing || cursor == null || source == null || engine == null) return
        if (!audio.requestFocus()) return
        resumeOnFocusGain = false
        playing = true
        idleJob?.cancel()
        speakFromCursor()
    }

    private fun pauseInternal(abandonFocus: Boolean) {
        if (!playing) return
        playing = false
        flush()
        if (abandonFocus) audio.abandonFocus()
        publishPaused()
    }

    /** Restarts speaking at the cursor (at [wordOffset] within it). */
    private fun speakFromCursor() {
        flush()
        if (pastChapterEnd) {
            when (nextStatus) {
                NextStatus.Loaded -> moveTo(Pos(next!!, 0))
                NextStatus.EndOfBook -> return finish()
                NextStatus.Loading -> Unit
                NextStatus.Unknown, NextStatus.Failed -> prefetch()
            }
        }
        publishPlaying(range = null)
        // While past a chapter's end this queues nothing; the loaded chapter restarts speaking.
        fillQueue()
    }

    private fun jumped() {
        if (playing) speakFromCursor() else publishPaused()
    }

    private fun flush() {
        queued.clear()
        engine?.stop()
    }

    /** Keeps [QUEUE_DEPTH] utterances queued so the engine never waits between sentences. */
    private fun fillQueue() {
        val engine = engine ?: return
        while (playing && queued.size < QUEUE_DEPTH) {
            val last = queued.lastOrNull()
            val pos = if (last == null) {
                if (pastChapterEnd) return
                cursor ?: return
            } else {
                if (pauseAtSentenceEnd) return
                after(last.pos) ?: return
            }
            if (last != null && _sleepTimer.value == ReadAloudSleepTimer.EndOfChapter && pos.chapter !== last.pos.chapter) return
            if (!applyLanguage(engine, pos)) return
            val offset = if (last == null) wordOffset.coerceIn(0, pos.segment.text.length) else 0
            val text = pos.segment.text.substring(offset).ifBlank { pos.segment.text }
            val item = Queued(id = "ra-${serial++}", pos = pos, offset = if (text.length == pos.segment.text.length) 0 else offset)
            queued.addLast(item)
            if (!engine.speak(item.id, text)) post { onUtteranceDone(item.id, failed = true) }
        }
    }

    /** False after failing playback, when the very first language can't be spoken. */
    private fun applyLanguage(engine: SpeechEngine, pos: Pos): Boolean {
        val tag = pos.segment.language ?: pos.chapter.language ?: book?.language
        val key = tag?.lowercase() ?: DEVICE_LANGUAGE
        if (languageApplied && key == appliedLanguage) return true
        if (key in unavailableLanguages) return true
        val voice = settingsValue.voices[readAloudVoiceKey(tag)]
        var status = engine.setLanguage(tag, voice)
        // A chosen voice that was uninstalled since: fall back to the language's default voice.
        if (status != SpeechLanguage.Available && voice != null) status = engine.setLanguage(tag, null)
        if (status == SpeechLanguage.Available) {
            appliedLanguage = key
            languageApplied = true
            return true
        }
        if (languageApplied) {
            // A quotation in a language the engine can't speak: read it in the current voice.
            unavailableLanguages += key
            val current = appliedLanguage.takeUnless { it == DEVICE_LANGUAGE }
            engine.setLanguage(current, settingsValue.voices[readAloudVoiceKey(current)])
            return true
        }
        fail(
            if (status == SpeechLanguage.MissingData) {
                ReadAloudError.MissingVoiceData(tag)
            } else {
                ReadAloudError.LanguageNotSupported(tag)
            },
        )
        return false
    }

    private fun onUtteranceStart(id: String) {
        val item = queued.firstOrNull { it.id == id } ?: return
        while (queued.first() !== item) queued.removeFirst()
        if (!isAt(item.pos)) moveTo(item.pos)
        wordOffset = item.offset
        publishPlaying(range = null)
    }

    private fun onUtteranceRange(id: String, start: Int, end: Int) {
        val item = queued.firstOrNull() ?: return
        if (item.id != id || !isAt(item.pos)) return
        val length = item.pos.segment.text.length
        val from = (item.offset + start).coerceIn(0, length)
        val to = (item.offset + end).coerceIn(from, length)
        wordOffset = from
        publishPlaying(range = if (to > from) from until to else null)
    }

    private fun onUtteranceDone(id: String, failed: Boolean) {
        val item = queued.firstOrNull { it.id == id } ?: return
        while (queued.isNotEmpty()) {
            if (queued.removeFirst() === item) break
        }
        if (failed) {
            if (++consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) return fail(ReadAloudError.SpeechFailed)
        } else {
            consecutiveErrors = 0
        }
        val endOfChapter = item.pos.index == item.pos.chapter.segments.lastIndex
        val following = after(item.pos)
        if (following != null) {
            if (!isAt(following)) moveTo(following)
        } else {
            when (nextStatus) {
                NextStatus.EndOfBook -> return finish()
                NextStatus.Failed -> return fail(ReadAloudError.SourceFailed)
                NextStatus.Loaded -> moveTo(Pos(next!!, 0))
                NextStatus.Unknown, NextStatus.Loading -> {
                    if (!isAt(item.pos)) moveTo(item.pos)
                    pastChapterEnd = true
                    wordOffset = 0
                    if (nextStatus == NextStatus.Unknown) prefetch()
                }
            }
        }
        if (pauseAtSentenceEnd || (endOfChapter && _sleepTimer.value == ReadAloudSleepTimer.EndOfChapter)) {
            pauseAtSentenceEnd = false
            sleepJob?.cancel()
            _sleepTimer.value = ReadAloudSleepTimer.Off
            return pauseInternal(abandonFocus = true)
        }
        fillQueue()
    }

    private fun onSettings(settings: ReadAloudSettings) {
        val old = settingsValue
        settingsValue = settings
        val engine = engine ?: return
        if (settings == old) return
        if (settings.rate != old.rate) engine.setRate(settings.rate)
        if (settings.pitch != old.pitch) engine.setPitch(settings.pitch)
        if (settings.voices != old.voices) {
            languageApplied = false
            unavailableLanguages.clear()
        }
        // Queued sentences keep the old voice; restart at the current word so the change is heard now.
        if (playing && !pastChapterEnd) speakFromCursor()
    }

    private fun onInterruption(interruption: AudioInterruption) {
        when (interruption) {
            AudioInterruption.Loss, AudioInterruption.BecomingNoisy -> {
                resumeOnFocusGain = false
                pauseInternal(abandonFocus = true)
            }
            AudioInterruption.TransientLoss -> if (playing) {
                resumeOnFocusGain = true
                pauseInternal(abandonFocus = false)
            }
            AudioInterruption.Gain -> if (resumeOnFocusGain) resumeInternal()
        }
    }

    private fun onSleepTimerElapsed() {
        if (playing) {
            // Finish the sentence being read, then pause (onUtteranceDone); drop the look-ahead now.
            pauseAtSentenceEnd = true
        } else {
            _sleepTimer.value = ReadAloudSleepTimer.Off
        }
    }

    private fun moveTo(pos: Pos, knownNext: ReadAloudChapter? = null, forceNewChapter: Boolean = false) {
        val old = cursor?.chapter
        cursor = pos
        pastChapterEnd = false
        wordOffset = 0
        if (pos.chapter === old && !forceNewChapter) return
        prefetchJob?.cancel()
        if (knownNext != null) {
            next = knownNext
            nextStatus = NextStatus.Loaded
        } else {
            next = null
            nextStatus = NextStatus.Unknown
            prefetch()
        }
    }

    private fun after(pos: Pos): Pos? {
        val chapter = pos.chapter
        return when {
            pos.index + 1 < chapter.segments.size -> Pos(chapter, pos.index + 1)
            chapter === cursor?.chapter && nextStatus == NextStatus.Loaded -> next?.let { Pos(it, 0) }
            else -> null
        }
    }

    private fun isAt(pos: Pos): Boolean {
        val at = cursor ?: return false
        return at.chapter === pos.chapter && at.index == pos.index
    }

    /** Loads the chapter after the cursor's in the background, so reading flows into it. */
    private fun prefetch() {
        val src = source ?: return
        val from = cursor?.chapter ?: return
        nextStatus = NextStatus.Loading
        prefetchJob = scope.launch {
            val result = try {
                Result.success(loadAfter(src, from))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            post {
                if (source !== src || cursor?.chapter !== from) return@post
                val chapter = result.getOrNull()
                nextStatus = when {
                    result.isFailure -> NextStatus.Failed
                    chapter == null -> NextStatus.EndOfBook
                    else -> NextStatus.Loaded
                }
                next = chapter
                if (!playing) return@post
                when {
                    !pastChapterEnd -> fillQueue()
                    nextStatus == NextStatus.Failed -> fail(ReadAloudError.SourceFailed)
                    else -> speakFromCursor()
                }
            }
        }
    }

    private suspend fun loadAfter(source: ReadAloudSource, chapter: ReadAloudChapter): ReadAloudChapter? {
        var from = chapter
        while (true) {
            val loaded = source.nextChapter(from) ?: return null
            val readable = loaded.readable()
            if (readable.segments.isNotEmpty()) return readable.copy(startIndex = 0)
            from = loaded
        }
    }

    private suspend fun loadBefore(source: ReadAloudSource, chapter: ReadAloudChapter): ReadAloudChapter? {
        var from = chapter
        while (true) {
            val loaded = source.previousChapter(from) ?: return null
            val readable = loaded.readable()
            if (readable.segments.isNotEmpty()) return readable.copy(startIndex = 0)
            from = loaded
        }
    }

    /** The chapter at [locator], or the first readable one after it when it has no text. */
    private suspend fun loadFrom(source: ReadAloudSource, locator: ReaderLocator?): ReadAloudChapter? {
        val here = source.segmentsFrom(locator) ?: return null
        val readable = here.readable()
        return if (readable.segments.isNotEmpty()) readable else loadAfter(source, here)
    }

    /** Runs a source load inside a command; a failing source ends read-aloud. */
    private suspend fun <T> load(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        fail(ReadAloudError.SourceFailed)
        null
    }

    private fun publishPlaying(range: IntRange?) {
        val book = book ?: return
        val at = cursor ?: return
        _state.value = ReadAloudState.Playing(book, at.segment, at.chapter.title, range)
        show(at.segment, range)
    }

    private fun publishPaused() {
        val book = book ?: return
        val at = cursor ?: return
        _state.value = ReadAloudState.Paused(book, at.segment, at.chapter.title)
        show(at.segment, null)
        scheduleRelease(PAUSED_RELEASE_MS)
    }

    private fun show(segment: ReadAloudSegment, range: IntRange?) {
        val src = source ?: return
        marks.trySend(Mark(src, segment, range))
    }

    /** Stops after [delayMillis] unless playback resumed: a forgotten pause must not hold the engine. */
    private fun scheduleRelease(delayMillis: Long) {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(delayMillis)
            post {
                if (playing) return@post
                if (source != null) finish() else releaseEngine()
            }
        }
    }

    private fun finish() {
        release()
        _state.value = ReadAloudState.Idle
    }

    private fun fail(error: ReadAloudError) {
        val book = book
        release()
        _state.value = ReadAloudState.Error(book, error)
    }

    private fun release() {
        playing = false
        flush()
        releaseEngine()
        prefetchJob?.cancel()
        idleJob?.cancel()
        sleepJob?.cancel()
        _sleepTimer.value = ReadAloudSleepTimer.Off
        pauseAtSentenceEnd = false
        resumeOnFocusGain = false
        audio.abandonFocus()
        source?.let { src -> scope.launch { runCatching { src.clearHighlight() } } }
        source = null
        book = null
        cursor = null
        next = null
        nextStatus = NextStatus.Unknown
        pastChapterEnd = false
        wordOffset = 0
        consecutiveErrors = 0
    }

    private fun releaseEngine() {
        engine?.shutdown()
        engine = null
        languageApplied = false
    }

    private class Pos(val chapter: ReadAloudChapter, val index: Int) {
        val segment: ReadAloudSegment get() = chapter.segments[index]
    }

    private class Queued(val id: String, val pos: Pos, val offset: Int)

    private enum class NextStatus { Unknown, Loading, Loaded, EndOfBook, Failed }

    private class Mark(val source: ReadAloudSource, val segment: ReadAloudSegment, val range: IntRange?)

    internal companion object {
        /** The sentence being spoken plus one, so there's no gap while the next is synthesised. */
        const val QUEUE_DEPTH = 2
        const val MAX_CONSECUTIVE_ERRORS = 3
        const val PAUSED_RELEASE_MS = 10 * 60_000L
        const val UNUSED_ENGINE_RELEASE_MS = 30_000L
        private const val DEVICE_LANGUAGE = ""
    }
}

/** Drops blank segments (the contract forbids them, but a stray one must not stall the queue). */
private fun ReadAloudChapter.readable(): ReadAloudChapter {
    if (segments.none { it.text.isBlank() }) return this
    val kept = segments.filter { it.text.isNotBlank() }
    val start = segments.take(startIndex).count { it.text.isNotBlank() }
    return copy(segments = kept, startIndex = start.coerceAtMost((kept.size - 1).coerceAtLeast(0)))
}
