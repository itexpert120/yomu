package com.itexpert120.yomu.app

import android.app.Activity
import android.os.Build
import android.view.Display

/**
 * Asks the system to run this window at the display's highest refresh rate (90/120/144 Hz where the
 * panel supports it) at the current resolution. Compose animations and scrolling tick on vsync, so
 * once the display runs at its native rate every transition does too. Devices are otherwise free to
 * hold apps at 60 Hz. The system still wins when it must (battery saver, thermal limits, or the
 * user's own "smooth display" setting).
 */
fun Activity.preferNativeRefreshRate() {
    runCatching {
        @Suppress("DEPRECATION")
        val display: Display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay)
            ?: return
        val current = display.mode
        val fastest = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = fastest.modeId
            preferredRefreshRate = fastest.refreshRate
        }
    }
}
