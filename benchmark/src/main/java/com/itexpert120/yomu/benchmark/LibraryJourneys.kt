package com.itexpert120.yomu.benchmark

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.core.content.FileProvider
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val APP_ID = "com.itexpert120.yomu"
internal const val NORMAL_TITLE = "Benchmark 24"

/** Shared user journeys for the Baseline Profile generator and the library macrobenchmarks. */
internal object LibraryJourneys {
    /** Imports the 24-chapter fixture through the external-open path; the reader opens on it. */
    fun MacrobenchmarkScope.importFixture(context: Context) {
        val file = GeneratedEpubFixtures.generateAll(context).getValue("normal")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fixtures", file)
        val import = Intent(Intent.ACTION_VIEW).apply {
            setClassName(APP_ID, "$APP_ID.MainActivity")
            setDataAndType(uri, "application/epub+zip")
            clipData = ClipData.newRawUri("Benchmark EPUB", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(import)
        required(By.res(Pattern.compile("reader-ready:.*")), 60_000L)
        device.pressBack()
        device.waitForIdle()
    }

    fun MacrobenchmarkScope.waitForLibrary(): UiObject2 = required(By.res("library-pager"), 15_000L)

    /** Swipes across the library tabs and back, the pager driving the pill indicator. */
    fun MacrobenchmarkScope.swipeTabs() {
        val pager = waitForLibrary()
        pager.setGestureMargin(device.displayWidth / 8)
        repeat(3) {
            pager.swipe(Direction.LEFT, 0.8f)
            device.waitForIdle()
        }
        repeat(3) {
            pager.swipe(Direction.RIGHT, 0.8f)
            device.waitForIdle()
        }
    }

    /** Library → book details → scroll the contents → reader → a few page turns → back out. */
    fun MacrobenchmarkScope.readJourney() {
        tap(By.text(NORMAL_TITLE).hasAncestor(By.res("library-pager")))
        required(By.res("book-details-read"))
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayHeight / 6)
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
        tap(By.res("book-details-read"))
        required(By.res(Pattern.compile("reader-ready:.*")), 30_000L)
        repeat(3) {
            device.swipe(device.displayWidth * 4 / 5, device.displayHeight / 2, device.displayWidth / 5, device.displayHeight / 2, 12)
            device.waitForIdle()
        }
        // The reader may spend the first Back revealing its chrome; press until details is back.
        backUntil(By.res("book-details-read"))
        backUntil(By.res("library-pager"))
    }

    /**
     * Taps [selector] at its on-screen centre. Compose replaces accessibility nodes as content
     * animates (e.g. the import notice), so the node is re-found until its position can be read.
     */
    private fun MacrobenchmarkScope.tap(selector: BySelector) {
        repeat(10) {
            try {
                val center = required(selector).visibleCenter
                device.click(center.x, center.y)
                return
            } catch (_: StaleObjectException) {
                Thread.sleep(300)
            }
        }
        error("Couldn't tap $selector: it kept changing")
    }

    private fun MacrobenchmarkScope.backUntil(selector: BySelector) {
        repeat(3) {
            if (device.wait(Until.hasObject(selector), 1_500L)) return
            device.pressBack()
        }
        required(selector)
    }

    fun MacrobenchmarkScope.required(selector: BySelector, timeout: Long = 10_000L): UiObject2 = device.wait(Until.findObject(selector), timeout) ?: run {
        // Leave evidence for a failing journey: adb pull /sdcard/journey-failure.png
        device.executeShellCommand("screencap -p /sdcard/journey-failure.png")
        error("Required control/state missing: $selector")
    }
}
