package com.itexpert120.yomu.data.readaloud

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadAloudPlayerTest {
    private val world = FakeSpeechWorld()
    private val audio = FakeAudioOutput()
    private val settings = FakeSettingsStore()
    private var hostStarts = 0

    private fun TestScope.newPlayer(): ReadAloudPlayer = ReadAloudPlayer(
        scope = backgroundScope,
        engineFactory = world,
        audio = audio,
        settingsStore = settings,
        host = { hostStarts++ },
        clock = { testScheduler.currentTime },
    ).also { runCurrent() }

    private fun TestScope.started(source: FakeSource, book: ReadAloudBook = testBook): ReadAloudPlayer = newPlayer().also {
        it.start(source, book, null)
        runCurrent()
    }

    /** Lets the engine speak its next utterance to the end; returns what it said. */
    private fun TestScope.speak(): String {
        val text = world.engine.start()
        runCurrent()
        world.engine.finish()
        runCurrent()
        return text
    }

    private val ReadAloudPlayer.segmentId: String?
        get() = when (val s = state.value) {
            is ReadAloudState.Playing -> s.segment.id
            is ReadAloudState.Paused -> s.segment.id
            else -> null
        }

    private val head: String? get() = world.engine.queue.firstOrNull()?.second

    @Test
    fun readsEverySentenceAcrossChaptersSkippingEmptyOnesThenStops() = runTest {
        val source = FakeSource(listOf(listOf("One.", "Two."), emptyList(), listOf("Three.")))
        val player = started(source)

        assertEquals(1, hostStarts)
        assertTrue(audio.holding)
        assertEquals("0:0", player.segmentId)
        assertEquals("0:0" to null, source.highlights.last())

        assertEquals(listOf("One.", "Two.", "Three."), List(3) { speak() })

        assertEquals(ReadAloudState.Idle, player.state.value)
        assertTrue(world.engine.shutDown)
        assertFalse(audio.holding)
        assertTrue(source.cleared > 0)
    }

    @Test
    fun queuesALookAheadIntoTheNextChapter() = runTest {
        val source = FakeSource(listOf(listOf("One."), listOf("Two.")))
        started(source)

        assertEquals(listOf("One.", "Two."), world.engine.queue.map { it.second })
    }

    @Test
    fun wordRangesAreReportedAndPauseResumesFromTheWord() = runTest {
        val source = FakeSource(listOf(listOf("The quick brown fox.", "Next.")))
        val player = started(source)
        world.engine.start()
        world.engine.range(4, 9)
        runCurrent()

        assertEquals(4 until 9, (player.state.value as ReadAloudState.Playing).range)
        assertEquals("0:0" to (4 until 9), source.highlights.last())

        player.pause()
        runCurrent()
        assertTrue(player.state.value is ReadAloudState.Paused)
        assertTrue(world.engine.queue.isEmpty())
        assertFalse(audio.holding)

        player.resume()
        runCurrent()
        assertEquals("quick brown fox.", head)
        world.engine.start()
        world.engine.range(6, 11)
        runCurrent()
        assertEquals(10 until 15, (player.state.value as ReadAloudState.Playing).range)
    }

    @Test
    fun nextAndPreviousMoveBySentenceAcrossChapters() = runTest {
        val source = FakeSource(listOf(listOf("A1.", "A2."), listOf("B1.", "B2.")))
        val player = started(source)

        player.next()
        runCurrent()
        assertEquals("0:1", player.segmentId)
        assertEquals("A2.", head)

        player.next()
        runCurrent()
        assertEquals("1:0", player.segmentId)
        assertEquals("B1.", head)

        player.previous()
        runCurrent()
        assertEquals("0:1", player.segmentId)
        assertEquals("A2.", head)

        player.previous()
        runCurrent()
        player.previous()
        runCurrent()
        assertEquals("0:0", player.segmentId)
        assertEquals("A1.", head)

        // Reading on from a chapter reached backwards still flows into the following chapter.
        assertEquals(listOf("A1.", "A2.", "B1.", "B2."), List(4) { speak() })
    }

    @Test
    fun nextWhilePausedMovesWithoutSpeaking() = runTest {
        val source = FakeSource(listOf(listOf("A1.", "A2.")))
        val player = started(source)
        player.pause()
        player.next()
        runCurrent()

        assertTrue(player.state.value is ReadAloudState.Paused)
        assertEquals("0:1", player.segmentId)
        assertTrue(world.engine.queue.isEmpty())
        assertEquals("0:1" to null, source.highlights.last())
    }

    @Test
    fun seekToContinuesFromTheLocator() = runTest {
        val source = FakeSource(listOf(listOf("A1."), listOf("B1.", "B2.", "B3.")))
        val player = started(source)

        player.seekTo(locatorAt(1, 1))
        runCurrent()

        assertEquals("1:1", player.segmentId)
        assertEquals(listOf("B2.", "B3."), List(2) { speak() })
        assertEquals(ReadAloudState.Idle, player.state.value)
    }

    @Test
    fun sleepTimerFinishesTheSentenceThenPauses() = runTest {
        val source = FakeSource(listOf(listOf("A1.", "A2.", "A3.")))
        val player = started(source)
        world.engine.start()
        player.setSleepTimer(SleepTimerRequest.Minutes(5))
        runCurrent()
        assertEquals(ReadAloudSleepTimer.Minutes(5, testScheduler.currentTime + 5 * 60_000L), player.sleepTimer.value)

        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertTrue(player.state.value is ReadAloudState.Playing)

        world.engine.finish()
        runCurrent()
        assertTrue(player.state.value is ReadAloudState.Paused)
        assertEquals("0:1", player.segmentId)
        assertTrue(world.engine.queue.isEmpty())
        assertEquals(ReadAloudSleepTimer.Off, player.sleepTimer.value)
        assertFalse(audio.holding)
    }

    @Test
    fun sleepTimerAtEndOfChapterPausesBeforeTheNextChapter() = runTest {
        val source = FakeSource(listOf(listOf("A1.", "A2."), listOf("B1.")))
        val player = started(source)
        player.setSleepTimer(SleepTimerRequest.EndOfChapter)
        runCurrent()

        assertEquals("A1.", speak())
        assertEquals(listOf("A2."), world.engine.queue.map { it.second })
        assertEquals("A2.", speak())

        assertTrue(player.state.value is ReadAloudState.Paused)
        assertEquals("1:0", player.segmentId)
        assertEquals(ReadAloudSleepTimer.Off, player.sleepTimer.value)

        player.resume()
        runCurrent()
        assertEquals("B1.", head)
    }

    @Test
    fun aLongPauseReleasesEverything() = runTest {
        val source = FakeSource(listOf(listOf("A1.")))
        val player = started(source)
        player.pause()
        runCurrent()

        advanceTimeBy(ReadAloudPlayer.PAUSED_RELEASE_MS)
        runCurrent()

        assertEquals(ReadAloudState.Idle, player.state.value)
        assertTrue(world.engine.shutDown)
    }

    @Test
    fun transientFocusLossPausesAndGainResumes() = runTest {
        val source = FakeSource(listOf(listOf("A1.")))
        val player = started(source)

        audio.interruptions.emit(AudioInterruption.TransientLoss)
        runCurrent()
        assertTrue(player.state.value is ReadAloudState.Paused)
        assertTrue(audio.holding)

        audio.interruptions.emit(AudioInterruption.Gain)
        runCurrent()
        assertTrue(player.state.value is ReadAloudState.Playing)
        assertEquals("A1.", head)
    }

    @Test
    fun headphonesUnpluggedPausesForGood() = runTest {
        val source = FakeSource(listOf(listOf("A1.")))
        val player = started(source)

        audio.interruptions.emit(AudioInterruption.BecomingNoisy)
        runCurrent()
        audio.interruptions.emit(AudioInterruption.Gain)
        runCurrent()

        assertTrue(player.state.value is ReadAloudState.Paused)
        assertFalse(audio.holding)
    }

    @Test
    fun refusedAudioFocusStartsPaused() = runTest {
        audio.grant = false
        val player = started(FakeSource(listOf(listOf("A1."))))

        assertTrue(player.state.value is ReadAloudState.Paused)
        assertTrue(world.engine.queue.isEmpty())
    }

    @Test
    fun aSettingsChangeIsHeardFromTheCurrentWord() = runTest {
        val source = FakeSource(listOf(listOf("The quick brown fox.")))
        started(source)
        world.engine.start()
        world.engine.range(4, 9)
        runCurrent()

        settings.setRate(1.5f)
        runCurrent()

        assertEquals(1.5f, world.engine.spokenRate)
        assertEquals("quick brown fox.", head)
    }

    @Test
    fun missingTtsEngineIsAnError() = runTest {
        world.initSucceeds = false
        val player = started(FakeSource(listOf(listOf("A1."))))

        assertEquals(ReadAloudState.Error(testBook, ReadAloudError.EngineUnavailable), player.state.value)
    }

    @Test
    fun missingVoiceDataForTheBookLanguageIsAnError() = runTest {
        world.languages["fr"] = SpeechLanguage.MissingData
        val book = testBook.copy(language = "fr")
        val player = started(FakeSource(listOf(listOf("Bonjour."))), book)

        val state = player.state.value as ReadAloudState.Error
        assertEquals(ReadAloudError.MissingVoiceData("fr"), state.error)
        assertTrue(state.error.canInstallVoiceData)
        assertFalse(audio.holding)
    }

    @Test
    fun aQuotationInAnUnavailableLanguageIsReadInTheBookVoice() = runTest {
        world.languages["de"] = SpeechLanguage.NotSupported
        val source = FakeSource(listOf(listOf("A1."), listOf("B1.")), languages = mapOf(1 to "de"))
        val player = started(source)

        assertEquals(listOf("A1.", "B1."), List(2) { speak() })
        assertEquals(ReadAloudState.Idle, player.state.value)
    }

    @Test
    fun nothingToReadIsAnError() = runTest {
        val player = started(FakeSource(listOf(emptyList(), listOf(" "))))

        assertEquals(ReadAloudState.Error(testBook, ReadAloudError.NothingToRead), player.state.value)
    }

    @Test
    fun aFailingPageIsAnError() = runTest {
        val source = FakeSource(listOf(listOf("A1."))).apply { failing = true }
        val player = started(source)

        assertEquals(ReadAloudState.Error(testBook, ReadAloudError.SourceFailed), player.state.value)
    }

    @Test
    fun aNextChapterThatFailsToLoadEndsWithAnErrorAtTheChapterEnd() = runTest {
        val source = FakeSource(listOf(listOf("A1."), listOf("B1."))).apply { failingNext = true }
        val player = started(source)

        assertEquals("A1.", speak())

        assertEquals(ReadAloudState.Error(testBook, ReadAloudError.SourceFailed), player.state.value)
    }

    @Test
    fun repeatedSpeechErrorsAreAnError() = runTest {
        val player = started(FakeSource(listOf(listOf("A1.", "A2.", "A3.", "A4."))))

        repeat(ReadAloudPlayer.MAX_CONSECUTIVE_ERRORS) {
            world.engine.error()
            runCurrent()
        }

        assertEquals(ReadAloudState.Error(testBook, ReadAloudError.SpeechFailed), player.state.value)
        assertTrue(world.engine.shutDown)
    }

    @Test
    fun aSingleSpeechErrorSkipsTheSentence() = runTest {
        val player = started(FakeSource(listOf(listOf("A1.", "A2."))))

        world.engine.error()
        runCurrent()

        assertEquals("A2.", speak())
        assertEquals(ReadAloudState.Idle, player.state.value)
    }

    @Test
    fun detachStopsOnlyForTheSourceBeingRead() = runTest {
        val source = FakeSource(listOf(listOf("A1.")))
        val player = started(source)

        player.detach(FakeSource(listOf(listOf("Other."))))
        runCurrent()
        assertTrue(player.state.value is ReadAloudState.Playing)

        player.detach(source)
        runCurrent()
        assertEquals(ReadAloudState.Idle, player.state.value)
    }

    @Test
    fun startingAnotherBookReplacesTheFirst() = runTest {
        val first = FakeSource(listOf(listOf("A1.")))
        val player = started(first)
        val second = FakeSource(listOf(listOf("Z1.")))

        player.start(second, testBook, null)
        runCurrent()

        assertTrue(first.cleared > 0)
        assertEquals("Z1.", head)
        assertEquals("0:0" to null, second.highlights.last())
    }

    @Test
    fun voicesAreListedAndTheIdleEngineIsReleased() = runTest {
        val player = newPlayer()

        assertEquals(listOf("voice-en"), player.voices("en").map { it.name })
        advanceTimeBy(ReadAloudPlayer.UNUSED_ENGINE_RELEASE_MS)
        runCurrent()
        assertTrue(world.engine.shutDown)
    }
}
