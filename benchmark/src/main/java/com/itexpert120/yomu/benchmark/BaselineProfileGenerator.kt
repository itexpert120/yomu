package com.itexpert120.yomu.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import com.itexpert120.yomu.benchmark.LibraryJourneys.importFixture
import com.itexpert120.yomu.benchmark.LibraryJourneys.readJourney
import com.itexpert120.yomu.benchmark.LibraryJourneys.swipeTabs
import com.itexpert120.yomu.benchmark.LibraryJourneys.waitForLibrary
import org.junit.Rule
import org.junit.Test

/**
 * Generates the app's Baseline Profile: `./gradlew :app:generateBaselineProfile` with a device or
 * emulator connected. Covers startup (also emitted as the startup profile), the library tabs, book
 * details with its contents list, and the reader.
 */
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun generate() = rule.collect(packageName = APP_ID, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        if (device.findObject(androidx.test.uiautomator.By.text(NORMAL_TITLE)) == null) importFixture(context)
        waitForLibrary()
        swipeTabs()
        readJourney()
    }
}
