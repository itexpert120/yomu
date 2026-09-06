package com.itexpert120.yomu

import androidx.test.core.app.ActivityScenario
import com.itexpert120.yomu.feature.reader.captureReaderWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderWindowTest {
    @Test fun releaseRestoresBrightnessAndLeavesUnownedPropertiesAlone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val window = activity.window
                val previous = window.attributes.screenBrightness
                val restore = captureReaderWindow(window, window.decorView)
                window.attributes = window.attributes.apply { screenBrightness = 0.2f }
                restore()
                assertEquals(previous, window.attributes.screenBrightness, 0.001f)
            }
        }
    }
}
