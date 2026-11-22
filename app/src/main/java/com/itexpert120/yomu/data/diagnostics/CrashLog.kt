package com.itexpert120.yomu.data.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.itexpert120.yomu.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A local, user-shareable diagnostics log, so crashes on a real device can be reported without any
 * analytics SDK. Nothing leaves the device unless the user shares the file.
 *
 * It records three things:
 * - Java/Kotlin crashes, from an uncaught-exception handler (written synchronously before dying).
 * - Abnormal process exits Android recorded since the last launch (API 30+): native crashes such
 *   as a WebView renderer abort, ANRs, low-memory kills. A Java handler never sees these.
 * - Short breadcrumbs (activity started/stopped, notable recoveries) for context.
 */
@Singleton
class CrashLog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir = File(context.filesDir, "diagnostics")
    private val file = File(dir, "diagnostics.log")
    private val lock = Any()

    // Breadcrumbs come from the main thread (activity callbacks, WebView recovery); the file I/O
    // (and the occasional 512 KB trim) runs here in order instead. Crashes still write synchronously.
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "crash-log").apply { isDaemon = true } }

    private val _hasEntries = MutableStateFlow(false)
    val hasEntries: StateFlow<Boolean> = _hasEntries.asStateFlow()

    /** Call once from `Application.onCreate`. */
    fun install(application: Application) {
        writer.execute { _hasEntries.value = file.length() > 0 }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { append("CRASH on thread '${thread.name}'\n${error.stackTraceText()}") }
            previous?.uncaughtException(thread, error)
        }
        application.registerActivityLifecycleCallbacks(Breadcrumbs())
        // Reading exit records touches binder + disk; keep it off the startup path.
        writer.execute { runCatching { recordPastExits() } }
    }

    /** Appends a one-line breadcrumb. */
    fun note(message: String) {
        val at = System.currentTimeMillis()
        writer.execute { runCatching { append(message, at) } }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) { file.delete() }
        _hasEntries.value = false
    }

    /** A share-sheet intent carrying the log as a text attachment, or null if it's empty. */
    suspend fun shareIntent(): Intent? = withContext(Dispatchers.IO) {
        if (file.length() == 0L) return@withContext null
        val export = File(context.cacheDir, "shared/open-reader-diagnostics.txt").apply { parentFile?.mkdirs() }
        synchronized(lock) { export.writeText(header() + file.readText()) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", export)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Open Reader diagnostics (v${BuildConfig.VERSION_NAME})")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant through the chooser (for its preview and the target).
        send.clipData = ClipData.newRawUri("Open Reader diagnostics", uri)
        Intent.createChooser(send, "Share diagnostics log")
    }

    private fun header() = buildString {
        appendLine("Open Reader ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Exported ${timestamp(System.currentTimeMillis())}")
        appendLine()
    }

    private fun append(entry: String, at: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            dir.mkdirs()
            file.appendText("[${timestamp(at)}] $entry\n")
            trimIfNeeded()
        }
        _hasEntries.value = true
    }

    private fun trimIfNeeded() {
        if (file.length() <= MAX_BYTES) return
        val text = file.readText()
        file.writeText("…(older entries trimmed)\n" + text.takeLast(KEEP_BYTES))
    }

    private fun recordPastExits() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val marker = File(dir, "last-exit-seen")
        val lastSeen = marker.takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0L
        val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
            .filter { it.timestamp > lastSeen && it.isWorthLogging() }
            .sortedBy { it.timestamp }
        exits.forEach { append(it.describe(context.packageName), at = it.timestamp) }
        val newest = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()?.timestamp
        if (newest != null) {
            dir.mkdirs()
            marker.writeText(newest.toString())
        }
    }

    private inner class Breadcrumbs : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) = note("${activity.javaClass.simpleName} started")
        override fun onActivityStopped(activity: Activity) = note("${activity.javaClass.simpleName} stopped")
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (savedInstanceState != null) note("${activity.javaClass.simpleName} restored from saved state")
        }
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        const val MAX_BYTES = 512 * 1024L
        const val KEEP_BYTES = 256 * 1024
        const val MAX_EXITS = 16

        fun timestamp(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(millis))

        fun Throwable.stackTraceText(): String = StringWriter().also { printStackTrace(PrintWriter(it)) }.toString()
    }
}

// Normal exits (user swipe-away, app update, our own exit) aren't bugs.
@RequiresApi(Build.VERSION_CODES.R)
private fun ApplicationExitInfo.isWorthLogging(): Boolean = reason in setOf(
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_CRASH_NATIVE,
    ApplicationExitInfo.REASON_ANR,
    ApplicationExitInfo.REASON_LOW_MEMORY,
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
    ApplicationExitInfo.REASON_DEPENDENCY_DIED,
    ApplicationExitInfo.REASON_SIGNALED,
)

@RequiresApi(Build.VERSION_CODES.R)
private fun ApplicationExitInfo.describe(appProcess: String): String = buildString {
    append("PROCESS EXIT: ${reasonName()}")
    // The WebView renderer runs in its own process and is reported here too.
    if (processName != appProcess) append(" · process=$processName")
    append(" · importance=$importance")
    append(" · pss=${pss / 1024}MB rss=${rss / 1024}MB")
    description?.let { append(" · $it") }
    // ANR traces are text; native-crash traces are a binary tombstone, so keep its readable strings
    // (abort message, symbolised frames).
    val trace = runCatching { traceInputStream?.use { it.readBytes() } }.getOrNull() ?: return@buildString
    val text = when (reason) {
        ApplicationExitInfo.REASON_ANR -> String(trace)
        ApplicationExitInfo.REASON_CRASH_NATIVE -> trace.printableRuns()
        else -> return@buildString
    }
    append("\n").append(text.take(24_000))
}

@RequiresApi(Build.VERSION_CODES.R)
private fun ApplicationExitInfo.reasonName(): String = when (reason) {
    ApplicationExitInfo.REASON_CRASH -> "crash (Java)"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash (native)"
    ApplicationExitInfo.REASON_ANR -> "ANR (app not responding)"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "killed for low memory"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed for excessive resource use"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialisation failure"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
    ApplicationExitInfo.REASON_SIGNALED -> "killed by signal $status"
    else -> "reason $reason"
}

/** Printable ASCII runs of 6+ characters, one per line, like `strings`. */
private fun ByteArray.printableRuns(): String {
    val out = StringBuilder()
    val run = StringBuilder()
    for (b in this) {
        val c = b.toInt() and 0xFF
        if (c in 0x20..0x7E) {
            run.append(c.toChar())
        } else {
            if (run.length >= 6) out.append(run).append('\n')
            run.setLength(0)
        }
    }
    if (run.length >= 6) out.append(run)
    return out.toString()
}
