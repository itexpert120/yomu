package com.itexpert120.yomu.benchmark

import android.content.ClipData
import android.content.Intent
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.regex.Pattern

/** Real fixture import and entry-point preparation happen outside the measured interval. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalMetricApi::class)
class ReaderLaunchBenchmark(private val fixture: String, private val title: String) {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun libraryResume() = measureEntry("library")

    @Test fun bookDetailsResume() = measureEntry("details")

    @Test fun nonCurrentChapterTap() = measureEntry("chapter")

    @Test fun intentionallyBrokenEntryFailsTheHarness() {
        assertTrue(runCatching { required(By.res("intentionally-missing-reader-entry"), 100L).click() }.isFailure)
    }

    private fun measureEntry(entry: String) {
        val file = GeneratedEpubFixtures.generateAll(context).getValue(fixture)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fixtures", file)
        benchmarkRule.measureRepeated(
            packageName = APP_ID,
            metrics = listOf(FrameTimingMetric(), TraceSectionMetric("reader.open")),
            iterations = 3,
            setupBlock = {
                val import = Intent(Intent.ACTION_VIEW).apply {
                    setClassName(APP_ID, "$APP_ID.MainActivity")
                    setDataAndType(uri, "application/epub+zip")
                    clipData = ClipData.newRawUri("Benchmark EPUB", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(import)
                required(By.res(Pattern.compile("reader-ready:.*")), 60_000L)
                device.pressBack()
                required(By.text(title)).click()
                required(By.text("Chapter 1")).click()
                readyChapter(1)
                device.pressBack()
                required(By.res("book-details-read"))
                if (entry == "library") {
                    device.pressBack()
                    required(By.desc("Resume $title"))
                } else if (entry == "chapter") {
                    required(By.text("Chapter 2"))
                }
            },
            measureBlock = {
                val selector = when (entry) {
                    "library" -> By.desc("Resume $title")
                    "details" -> By.res("book-details-read")
                    else -> By.text("Chapter 2")
                }
                required(selector).click()
                readyChapter(if (entry == "chapter") 2 else 1)
            },
        )
    }

    private fun readyChapter(number: Int) {
        required(By.res(Pattern.compile("reader-ready:.*chapter$number\\.xhtml.*")), 30_000L)
    }

    private fun required(selector: BySelector, timeout: Long = 10_000L): UiObject2 = checkNotNull(device.wait(Until.findObject(selector), timeout)) { "Required reader control/state missing: $selector" }

    companion object {
        private const val APP_ID = "com.itexpert120.yomu"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures() = listOf(arrayOf("normal", "Benchmark 24"), arrayOf("stress-1500", "Benchmark 1500"))
    }
}
