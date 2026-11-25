package com.itexpert120.yomu.data.readaloud

import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReadAloudChapter
import com.itexpert120.yomu.core.reader.ReadAloudSegment
import com.itexpert120.yomu.core.reader.ReadAloudSource
import com.itexpert120.yomu.core.reader.ReaderLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal val testBook = ReadAloudBook(BookId("book"), "A Book", "An Author", coverPath = null, language = "en")

internal fun locatorAt(chapter: Int, index: Int) = ReaderLocator(
    locatorJson = "$chapter:$index",
    totalProgression = null,
    chapterTitle = "Chapter $chapter",
    href = "c$chapter",
)

/** A book as chapters of sentences; positions are "chapter:index" locators. */
internal class FakeSource(private val chapters: List<List<String>>, private val languages: Map<Int, String> = emptyMap()) : ReadAloudSource {
    var failing = false
    var failingNext = false
    val highlights = mutableListOf<Pair<String, IntRange?>>()
    var cleared = 0

    private fun chapter(index: Int, start: Int = 0) = ReadAloudChapter(
        key = index.toString(),
        title = "Chapter $index",
        language = languages[index],
        segments = chapters[index].mapIndexed { i, text -> ReadAloudSegment("$index:$i", text, null, locatorAt(index, i)) },
        startIndex = start,
    )

    override suspend fun segmentsFrom(start: ReaderLocator?): ReadAloudChapter? {
        check(!failing) { "page gone" }
        if (start == null) return chapter(0)
        val (c, i) = start.locatorJson.split(':').map(String::toInt)
        return chapter(c, i)
    }

    override suspend fun nextChapter(chapter: ReadAloudChapter): ReadAloudChapter? {
        check(!failing && !failingNext) { "page gone" }
        val index = chapter.key.toInt() + 1
        return if (index < chapters.size) chapter(index) else null
    }

    override suspend fun previousChapter(chapter: ReadAloudChapter): ReadAloudChapter? {
        check(!failing) { "page gone" }
        val index = chapter.key.toInt() - 1
        return if (index >= 0) chapter(index) else null
    }

    override suspend fun highlight(segment: ReadAloudSegment, range: IntRange?) {
        highlights += segment.id to range
    }

    override suspend fun clearHighlight() {
        cleared++
    }
}

/** A TTS engine whose utterances start and finish only when the test says so. */
internal class FakeSpeechEngine(private val listener: SpeechEngine.Listener, private val world: FakeSpeechWorld) : SpeechEngine {
    val queue = ArrayDeque<Pair<String, String>>()
    var spokenRate = 1f
    var spokenPitch = 1f
    var language: String? = null
    var shutDown = false

    override suspend fun initialize(): Boolean = world.initSucceeds

    override fun setLanguage(languageTag: String?, voiceName: String?): SpeechLanguage {
        val status = world.languages[languageTag] ?: SpeechLanguage.Available
        if (status == SpeechLanguage.Available) language = languageTag
        return status
    }

    override fun setRate(rate: Float) {
        spokenRate = rate
    }

    override fun setPitch(pitch: Float) {
        spokenPitch = pitch
    }

    override fun voices(languageTag: String?): List<ReadAloudVoice> = listOf(ReadAloudVoice("voice-$languageTag", languageTag.orEmpty(), 400, false, true))

    override fun speak(utteranceId: String, text: String): Boolean {
        queue.addLast(utteranceId to text)
        return true
    }

    override fun stop() {
        queue.clear()
    }

    override fun shutdown() {
        shutDown = true
        queue.clear()
    }

    /** Starts the head utterance; returns its text. */
    fun start(): String {
        val (id, text) = queue.first()
        listener.onStart(id)
        return text
    }

    fun range(start: Int, end: Int) = listener.onRange(queue.first().first, start, end)

    fun finish() = listener.onDone(queue.removeFirst().first)

    fun error() = listener.onError(queue.removeFirst().first)
}

internal class FakeSpeechWorld : SpeechEngine.Factory {
    var initSucceeds = true
    val languages = mutableMapOf<String?, SpeechLanguage>()
    val engines = mutableListOf<FakeSpeechEngine>()
    val engine: FakeSpeechEngine get() = engines.last()

    override fun create(listener: SpeechEngine.Listener): SpeechEngine = FakeSpeechEngine(listener, this).also { engines += it }
}

internal class FakeAudioOutput : ReadAloudAudioOutput {
    var grant = true
    var holding = false
    override val interruptions = MutableSharedFlow<AudioInterruption>(extraBufferCapacity = 8)

    override fun requestFocus(): Boolean {
        if (grant) holding = true
        return grant
    }

    override fun abandonFocus() {
        holding = false
    }
}

internal class FakeSettingsStore : ReadAloudSettingsStore {
    override val settings = MutableStateFlow(ReadAloudSettings())

    override suspend fun setRate(rate: Float) = settings.update { it.copy(rate = rate) }

    override suspend fun setPitch(pitch: Float) = settings.update { it.copy(pitch = pitch) }

    override suspend fun setVoice(language: String, voiceName: String?) = settings.update {
        it.copy(voices = if (voiceName == null) it.voices - language else it.voices + (language to voiceName))
    }
}
