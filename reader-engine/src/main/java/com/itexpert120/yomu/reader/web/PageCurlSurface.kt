package com.itexpert120.yomu.reader.web

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresApi

/**
 * Where the curl is drawn: a SurfaceView kept above the page (`setZOrderOnTop`), transparent while
 * no turn is running. Being its own surface is what lets the page under it be snapshotted mid-turn
 * (PixelCopy of the window never includes it), so the page the turn goes to can be captured the
 * moment it is laid out, under the curl.
 *
 * Frames are drawn on a render thread of their own, paced by its Choreographer: the main thread
 * only hands over the latest [CurlFrame] ([show]), so a busy page never holds the curl back.
 */
@RequiresApi(33)
internal class PageCurlSurface(context: Context) :
    SurfaceView(context),
    SurfaceHolder.Callback {
    private val thread = HandlerThread("PageCurl", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)

    // Render thread only.
    private var choreographer: Choreographer? = null
    private var curl: PageCurlShader? = null
    private val paint = Paint()
    private val clear = Paint().apply { blendMode = BlendMode.CLEAR }
    private var scheduled = false
    private var shown: CurlFrame? = null
    private var hasSurface = false

    /** Called (main thread) if the curl can't be drawn here: the page should turn its own way. */
    var onUnavailable: () -> Unit = {}

    @Volatile
    private var latest: CurlFrame? = null

    // Frames handed over so far; a listener fires once a frame at least as new as its own is drawn.
    @Volatile
    private var latestSeq = 0L

    @Volatile
    private var dirty = false

    // Main thread: called once a posted frame (or the clearing frame) is on screen.
    private val drawnListeners = ArrayList<Pair<Long, () -> Unit>>()

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        handler.post {
            choreographer = Choreographer.getInstance()
            curl = runCatching { PageCurlShader() }.onFailure {
                Log.w(TAG, "Page curl shader unavailable", it)
                post { onUnavailable() }
            }.getOrNull()
        }
    }

    /** Draws [frame] on the next vsync (the newest one wins); null clears the curl away. */
    fun show(frame: CurlFrame?, onDrawn: (() -> Unit)? = null) {
        synchronized(drawnListeners) {
            latest = frame
            latestSeq += 1
            if (onDrawn != null) drawnListeners += latestSeq to onDrawn
        }
        dirty = true
        handler.post { schedule() }
    }

    /** Recycles [bitmaps] once no frame on the render thread can still be drawing them. */
    fun releaseAfterDrawing(bitmaps: List<Bitmap>) {
        if (bitmaps.isEmpty()) return
        handler.post {
            if (shown == null || bitmaps.none { it === shown?.front || it === shown?.back || it === shown?.still || it === shown?.under }) {
                bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            } else {
                releaseAfterDrawing(bitmaps)
            }
        }
    }

    /** Copies [source] to an immutable GPU bitmap on the render thread, then hands it to [then] (main thread). */
    fun upload(source: Bitmap, then: (Bitmap?) -> Unit) {
        handler.post {
            val copy = runCatching { source.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
            post { then(copy) }
        }
    }

    private fun schedule() {
        if (scheduled) return
        val c = choreographer ?: return
        scheduled = true
        c.postFrameCallback { draw() }
    }

    private fun draw() {
        scheduled = false
        if (!dirty) return
        dirty = false
        val (frame, seq) = synchronized(drawnListeners) { latest to latestSeq }
        if (!hasSurface) {
            notifyDrawn(seq)
            return
        }
        val surface = holder.surface
        val canvas = if (surface.isValid) runCatching { surface.lockHardwareCanvas() }.getOrNull() else null
        if (canvas == null) {
            notifyDrawn(seq)
            return
        }
        try {
            canvas.drawPaint(clear)
            val shader = curl
            if (frame != null && shader != null) {
                shader.update(frame)
                paint.shader = shader.shader
                canvas.drawRect(0f, 0f, frame.width, frame.height, paint)
            }
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
        shown = frame
        notifyDrawn(seq)
    }

    private fun notifyDrawn(seq: Long) {
        val ready = synchronized(drawnListeners) {
            val due = drawnListeners.filter { it.first <= seq }.map { it.second }
            drawnListeners.removeAll { it.first <= seq }
            due
        }
        if (ready.isNotEmpty()) post { ready.forEach { it() } }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        handler.post {
            hasSurface = true
            dirty = true
            schedule()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        handler.post {
            dirty = true
            schedule()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Block until the render thread lets go of the surface.
        val done = java.util.concurrent.CountDownLatch(1)
        handler.post {
            hasSurface = false
            shown = null
            done.countDown()
        }
        done.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    fun release() {
        holder.removeCallback(this)
        handler.post { thread.quitSafely() }
    }

    private companion object {
        const val TAG = "PageCurl"
    }
}
