package com.itexpert120.yomu.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import com.itexpert120.yomu.benchmark.LibraryJourneys.importFixture
import com.itexpert120.yomu.benchmark.LibraryJourneys.readJourney
import com.itexpert120.yomu.benchmark.LibraryJourneys.swipeTabs
import com.itexpert120.yomu.benchmark.LibraryJourneys.waitForLibrary
import org.junit.Rule
import org.junit.Test

/**
 * Generates the app's Baseline Profile: `./gradlew :app:generateBaselineProfile` with a device or
 * emulator connected. Both tests feed the Baseline Profile; only cold start to the library feeds the
 * startup profile, which R8 uses to lay out the primary dex — putting the reader and book details in
 * it would spread the real start-up classes across a larger dex and blunt the gain.
 */
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun startup() = rule.collect(packageName = APP_ID, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        if (device.findObject(By.text(NORMAL_TITLE)) == null) importFixture(context)
        waitForLibrary()
    }

    @Test
    fun journeys() = rule.collect(packageName = APP_ID, includeInStartupProfile = false) {
        pressHome()
        startActivityAndWait()
        if (device.findObject(By.text(NORMAL_TITLE)) == null) importFixture(context)
        waitForLibrary()
        swipeTabs()
        readJourney()
    }
}
