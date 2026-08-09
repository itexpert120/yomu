package com.itexpert120.yomu.benchmark

import android.content.Intent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

/**
 * Macrobenchmark harness for the three reader entry points. A seeded device should contain the
 * generated normal and stress fixtures from [GeneratedEpubFixtures]; absent controls are treated as
 * a no-op so the module can also be installed against an empty library while developing it.
 */
class ReaderLaunchBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = instrumentation.targetContext.packageName

    @Test
    fun libraryResume() = measureTap("Resume")

    @Test
    fun bookDetailsResume() = measureTap("Read")

    @Test
    fun nonCurrentChapterTap() = measureTap("Chapter 2")

    private fun measureTap(label: String) {
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            iterations = 3,
            startupMode = StartupMode.WARM,
            setupBlock = {
                device.pressHome()
                startActivityAndWait()
                device.wait(Until.hasObject(By.pkg(packageName)), 5_000)
            },
            measureBlock = {
                device.findObject(By.text(label))?.click()
                device.wait(Until.hasObject(By.pkg(packageName)), 8_000)
            },
        )
    }

    private fun startActivityAndWait() {
        val intent = instrumentation.targetContext.packageManager
            .getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return
        instrumentation.startActivitySync(intent)
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun generateFixtures() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            GeneratedEpubFixtures.generateAll(instrumentation.targetContext)
        }
    }
}
