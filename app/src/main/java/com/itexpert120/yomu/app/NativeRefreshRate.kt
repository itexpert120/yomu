package com.itexpert120.yomu.app

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Asks the system to run this window at the display's highest refresh rate (90/120/144 Hz where the
 * panel supports it) at the current resolution. Compose animations and scrolling tick on vsync, so
 * once the display runs at its native rate every transition does too. Devices are otherwise free to
 * hold apps at 60 Hz. The system still wins when it must (battery saver, thermal limits, or the
 * user's own "smooth display" setting).
 *
 * Re-evaluated whenever the display changes while the activity is started: some devices (Samsung)
 * expose only their 60 Hz modes while battery saver is on, so a mode picked then would otherwise
 * pin the window to 60 Hz after the saver is switched off.
 */
fun ComponentActivity.preferNativeRefreshRate() {
    applyFastestMode()
    val displays = getSystemService(DisplayManager::class.java) ?: return
    val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == currentDisplay()?.displayId) applyFastestMode()
        }

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Modes may have changed while stopped (saver toggled in the background).
                applyFastestMode()
                displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
            }

            override fun onStop(owner: LifecycleOwner) {
                displays.unregisterDisplayListener(listener)
            }
        },
    )
}

@Suppress("DEPRECATION")
private fun Activity.currentDisplay(): Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay

private fun Activity.applyFastestMode() {
    runCatching {
        val display = currentDisplay() ?: return
        val current = display.mode
        val fastest = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
            ?: return
        val attributes = window.attributes
        // onDisplayChanged also fires for our own mode switch; don't relayout the window for nothing.
        if (attributes.preferredDisplayModeId == fastest.modeId) return
        window.attributes = attributes.apply {
            preferredDisplayModeId = fastest.modeId
            preferredRefreshRate = fastest.refreshRate
        }
    }
}
