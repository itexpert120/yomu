package com.itexpert120.yomu.core.reader

import android.os.Build
import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

/** Lightweight, release-safe trace points for the tap-to-ready reader path. */
object ReaderOpenTrace {
    private val nextCookie = AtomicInteger(1)

    fun mark(name: String) {
        // v2 prototype measurement: timestamps for comparing against the native engine.
        if (com.itexpert120.yomu.BuildConfig.DEBUG) {
            android.util.Log.i("ReaderOpenTrace", "$name at ${android.os.SystemClock.elapsedRealtime()}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cookie = nextCookie.getAndIncrement()
            Trace.beginAsyncSection(name, cookie)
            Trace.endAsyncSection(name, cookie)
        }
    }

    fun beginAsync(name: String): Int {
        val cookie = nextCookie.getAndIncrement()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.beginAsyncSection(name, cookie)
        }
        return cookie
    }

    fun endAsync(name: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.endAsyncSection(name, cookie)
        }
    }

    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection(name)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }
}
