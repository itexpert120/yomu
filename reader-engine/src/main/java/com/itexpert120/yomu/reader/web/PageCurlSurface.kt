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
 * Frames are drawn on a render thread of their own, paced by its Choreographer. The main thread
 * hands over frames as the finger moves ([show]); a settling turn runs entirely here ([animate]),
 * ticked on this thread's own vsync, so a busy main thread (the page reporting where it is, the
 * reader updating) never costs the curl a frame.
 */
@RequiresApi(33)
internal class PageCurlSurface(context: Context) :
    SurfaceView(context),
    SurfaceHolder.Callback {
    private val thread = HandlerThread("PageCurl", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)

    // Snapshots go to the GPU on a thread of their own: a 16 MB upload on the render thread held
    // the curl's frames back for 2–3 vsyncs just as a drag began.
    private val uploadThread = HandlerThread("PageCurlUpload", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val uploadHandler = Handler(uploadThread.looper)

    // Debug builds: per-turn render timings (render thread only).
    private val measuring = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    private var statFrames = 0
    private var statTotalNs = 0L
    private var statMaxNs = 0L
    private var statSlow = 0

    // Render thread only.
    private var choreographer: Choreographer? = null
    private var curl: PageCurlShader? = null
    private val paint = Paint()
    private val clear = Paint().apply { blendMode = BlendMode.CLEAR }
    private var scheduled = false
    private var shown: CurlFrame? = null
    private var hasSurface = false
    private var frame: CurlFrame? = null
    private var frameSeq = 0L
    private var dirty = false
    private var warmed = false

    // A running animation: called each vsync with the frame time, it gives the frame to draw, or
    // null once it has finished.
    private var ticker: ((Long) -> CurlFrame?)? = null

    /** Called (main thread) if the curl can't be drawn here: the page should turn its own way. */
    var onUnavailable: () -> Unit = {}

    // Frames handed over so far; a listener fires once a frame at least as new as its own is drawn.
    private var shownSeq = 0L

    // Called (main thread) once a posted frame (or the clearing frame) is on screen.
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
        val seq = synchronized(drawnListeners) {
            shownSeq += 1
            if (onDrawn != null) drawnListeners += shownSeq to onDrawn
            shownSeq
        }
        // In order with [animate]: a clearing frame also ends any animation.
        handler.post {
            this.frame = frame
            frameSeq = seq
            if (frame == null) ticker = null
            dirty = true
            schedule()
        }
    }

    /**
     * Runs [tick] on the render thread every vsync, drawing the frame it returns, until it returns
     * null (or a clearing [show] ends it).
     */
    fun animate(tick: (frameTimeNanos: Long) -> CurlFrame?) {
        handler.post {
            ticker = tick
            schedule()
        }
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
        uploadHandler.post {
            val started = System.nanoTime()
            val copy = runCatching { source.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
            if (measuring) Log.i(TAG, "curl-upload ${(System.nanoTime() - started) / 1_000_000} ms")
            post { then(copy) }
        }
    }

    private fun schedule() {
        if (scheduled) return
        val c = choreographer ?: return
        scheduled = true
        c.postFrameCallback { draw(it) }
    }

    private fun draw(frameTimeNanos: Long) {
        scheduled = false
        ticker?.let { tick ->
            val next = tick(frameTimeNanos)
            if (next == null) {
                ticker = null
            } else {
                frame = next
                dirty = true
            }
        }
        if (ticker != null) schedule()
        if (!dirty) return
        dirty = false
        val frame = frame
        val seq = frameSeq
        if (!hasSurface) {
            notifyDrawn(seq)
            return
        }
        val surface = holder.surface
        val started = System.nanoTime()
        val canvas = if (surface.isValid) runCatching { surface.lockHardwareCanvas() }.getOrNull() else null
        if (canvas == null) {
            notifyDrawn(seq)
            return
        }
        try {
            canvas.drawPaint(clear)
            warmUp(canvas)
            val shader = curl
            if (frame != null && shader != null) {
                shader.update(frame)
                paint.shader = shader.shader
                canvas.drawRect(0f, 0f, frame.width, frame.height, paint)
            }
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
        if (measuring) record(frame, System.nanoTime() - started)
        shown = frame
        notifyDrawn(seq)
    }

    /**
     * The first draw with the curl's shader compiles its GPU pipeline (~50 ms): do that with the
     * first clearing frame, one invisible pixel, instead of on a turn's first frame.
     */
    private fun warmUp(canvas: android.graphics.Canvas) {
        val shader = curl ?: return
        if (warmed) return
        warmed = true
        shader.update(CurlFrame(1f, 1f, CurlShape(0f, 1f, 1f, 1f, 1f, 1f, 1f), mirror = false, front = null, frontLive = false, back = null, backIsNextPage = false, still = null, stillEverywhere = false, underLive = false, under = null, paper = 0, dark = false))
        val dot = Paint().apply {
            this.shader = shader.shader
            alpha = 1
        }
        canvas.drawRect(0f, 0f, 1f, 1f, dot)
    }

    private fun record(frame: CurlFrame?, ns: Long) {
        if (frame != null) {
            statFrames++
            statTotalNs += ns
            statMaxNs = maxOf(statMaxNs, ns)
            if (ns > 12_000_000L) statSlow++
        } else if (statFrames > 0) {
            Log.i(TAG, "curl-render frames=$statFrames avg=${"%.1f".format(statTotalNs / 1e6 / statFrames)} ms max=${"%.1f".format(statMaxNs / 1e6)} ms over12ms=$statSlow")
            statFrames = 0
            statTotalNs = 0L
            statMaxNs = 0L
            statSlow = 0
        }
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
        uploadThread.quitSafely()
    }

    private companion object {
        const val TAG = "PageCurl"
    }
}
