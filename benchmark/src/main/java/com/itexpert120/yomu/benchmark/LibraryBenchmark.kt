package com.itexpert120.yomu.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import com.itexpert120.yomu.benchmark.LibraryJourneys.importFixture
import com.itexpert120.yomu.benchmark.LibraryJourneys.readJourney
import com.itexpert120.yomu.benchmark.LibraryJourneys.swipeTabs
import com.itexpert120.yomu.benchmark.LibraryJourneys.waitForLibrary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Startup and library interaction timings, each with and without the Baseline Profile so its effect
 * is measured rather than assumed. Run on the `benchmark` build type.
 */
@RunWith(Parameterized::class)
class LibraryBenchmark(private val compilation: CompilationMode) {
    @get:Rule val rule = MacrobenchmarkRule()

    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = APP_ID,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.COLD,
        iterations = 8,
        setupBlock = {
            // A fresh install has an empty library; startup is measured with a book in it.
            startActivityAndWait()
            if (device.findObject(androidx.test.uiautomator.By.text(NORMAL_TITLE)) == null) importFixture(context)
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForLibrary()
    }

    @Test
    fun tabSwipes() = rule.measureRepeated(
        packageName = APP_ID,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            if (device.findObject(androidx.test.uiautomator.By.text(NORMAL_TITLE)) == null) importFixture(context)
            waitForLibrary()
        },
    ) {
        swipeTabs()
    }

    @Test
    fun openBookAndRead() = rule.measureRepeated(
        packageName = APP_ID,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = {
            startActivityAndWait()
            if (device.findObject(androidx.test.uiautomator.By.text(NORMAL_TITLE)) == null) importFixture(context)
            waitForLibrary()
        },
    ) {
        readJourney()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes() = listOf(CompilationMode.None(), CompilationMode.Partial(BaselineProfileMode.Require))
    }
}
