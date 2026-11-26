package com.itexpert120.yomu.reader.web

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The page curl over the foliate page: turns drawn natively, like a paper book's.
 *
 * A turn starts from a snapshot of the page on screen (taken on every touch-down, so it's fresh)
 * drawn by the curl overlay ([PageCurlSurface]); under it foliate jumps straight to the page the
 * turn goes to (no web animation), and once that page is laid out it is snapshotted too, for what
 * the curl reveals of it (the back of a spread's page, a page coming back in). The leaf follows the
 * finger 1:1; let go, it carries on to the nearer rest — turned over, or back flat — on a spring
 * from the finger's own speed. A tap in an outer third turns with a shallow arc from the bottom
 * corner. When the overlay comes off, the live page under it is already the page it showed.
 *
 * The app owns horizontal drags while the curl is on: the page gets a cancel and never sees the
 * rest of the gesture (foliate's own swipe is off: `no-swipe`). A press held still is text
 * selection and stays the page's.
 */
@RequiresApi(33)
internal class FoliatePageCurl(
    private val container: FrameLayout,
    private val webView: WebView,
    private val jump: (dir: Int, id: Int) -> Unit,
    /** The curl can't be drawn on this device after all: the page should turn its own way. */
    onUnavailable: () -> Unit,
    /** A turn is about to cover the page (the app's controls should step aside). */
    private val onTurnStart: () -> Unit,
    private val log: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val surface = PageCurlSurface(container.context)
    private val choreographer = Choreographer.getInstance()

    // From the page (`turn-mode`): whether turns are the curl's, and how the page is laid out.
    private var active = false
    private var spread = false
    private var rtl = false
    private var paper = Color.WHITE
    private var dark = false

    // The latest snapshot of the page on screen (an immutable GPU bitmap), and the capture under way.
    private var current: Bitmap? = null
    private var currentAt = 0L
    private var staging: Bitmap? = null
    private var capturing = false
    private var captureAgain = false
    private val captureWaiters = ArrayList<(Bitmap?) -> Unit>()

    /** The app's controls are over the page: the first touch only asks them away. */
    var controlsShown = false

    private var turn: Turn? = null
    private var queuedTap = 0
    private val jumpWaiters = HashMap<Int, (Boolean) -> Unit>()
    private var nextJumpId = 1

    private val config = ViewConfiguration.get(container.context)
    private val touchSlop = config.scaledTouchSlop
    private val minFling = config.scaledMinimumFlingVelocity.toFloat()
    private val longPress = ViewConfiguration.getLongPressTimeout().toLong()

    init {
        surface.onUnavailable = {
            active = false
            onUnavailable()
        }
        // The curl covers the whole window, not just the page's view: the reader paints its page
        // colour (and the footer) beyond the WebView, and a curl cut off at the view's edges showed it.
        webView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    if (surface.parent != null) return
                    (webView.rootView as? ViewGroup)?.addView(surface, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }

                override fun onViewDetachedFromWindow(v: View) {
                    cancelAll()
                    (surface.parent as? ViewGroup)?.removeView(surface)
                }
            },
        )
        webView.setOnTouchListener { _, event -> onTouch(event) }
    }

    /** The area the curl covers (the window), and where the page's view sits in it. */
    private val area: View get() = webView.rootView

    private fun pageOffset(): Pair<Float, Float> {
        val location = IntArray(2).also(webView::getLocationInWindow)
        return location[0].toFloat() to location[1].toFloat()
    }

    /** `turn-mode` from the page. */
    fun onTurnMode(json: JSONObject) {
        active = json.optBoolean("curl")
        spread = json.optInt("columns", 1) >= 2
        rtl = json.optBoolean("rtl")
        paper = runCatching { Color.parseColor(json.optString("page", "#ffffff")) }.getOrDefault(Color.WHITE)
        dark = json.optBoolean("dark")
        if (!active) cancelAll()
    }

    /** A new page has been laid out and painted (`settled`). */
    fun onSettled() {
        val t = turn ?: return
        // The page a turn goes to can settle again after the jump (a chapter finishing its load
        // under the preview): snapshot it again.
        if (t.jumped == true) t.captureTarget()
    }

    /** foliate finished a jump asked for with [id]. */
    fun onJumped(id: Int, moved: Boolean) {
        jumpWaiters.remove(id)?.invoke(moved)
    }

    /** A tap in an outer third: [dir] 1 turns forward, -1 back (reading order). */
    fun tap(dir: Int) {
        if (!active) return
        if (controlsShown) return onTurnStart()
        val running = turn
        if (running != null && !running.completeNow()) {
            // It can't end yet (still jumping, or going back): the tap follows it.
            queuedTap = dir
            return
        }
        withSnapshot(freshFor = TAP_SNAPSHOT_MAX_AGE_MS) { shot ->
            if (shot == null || turn != null || !active) return@withSnapshot
            Turn(dir, shot, byTap = true, startX = 0f, startY = 0f).also { turn = it }.start()
        }
    }

    // region Snapshots

    private fun withSnapshot(freshFor: Long, then: (Bitmap?) -> Unit) {
        val shot = current
        if (shot != null && !capturing && SystemClock.uptimeMillis() - currentAt <= freshFor) return then(shot)
        captureWaiters += then
        capture()
    }

    /** Snapshots the page on screen (the overlay is its own surface, never in it). */
    private fun capture() {
        if (capturing) {
            captureAgain = true
            return
        }
        val window = container.context.findActivity()?.window
        val width = area.width
        val height = area.height
        if (window == null || width == 0 || height == 0 || !webView.isAttachedToWindow) return finishCapture(null)
        val target = staging?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { staging = it }
        val location = IntArray(2).also(area::getLocationInWindow)
        capturing = true
        val asked = SystemClock.uptimeMillis()
        runCatching {
            PixelCopy.request(window, Rect(location[0], location[1], location[0] + width, location[1] + height), target, { result ->
                if (result != PixelCopy.SUCCESS) {
                    capturing = false
                    return@request finishCapture(null)
                }
                surface.upload(target) { hw ->
                    capturing = false
                    if (hw != null) {
                        adopt(hw)
                        currentAt = asked
                    }
                    if (captureAgain) {
                        captureAgain = false
                        capture()
                    } else {
                        finishCapture(hw)
                    }
                }
            }, main)
        }.onFailure {
            capturing = false
            finishCapture(null)
        }
    }

    private fun finishCapture(shot: Bitmap?) {
        val waiting = captureWaiters.toList()
        captureWaiters.clear()
        waiting.forEach { it(shot) }
    }

    // endregion

    // region Turns

    /** Makes [shot] the page on screen's snapshot, taken now. */
    private fun adopt(shot: Bitmap?) {
        val old = current
        current = shot
        currentAt = if (shot != null) SystemClock.uptimeMillis() else 0L
        if (old != null && old !== shot && turn?.uses(old) != true) surface.releaseAfterDrawing(listOf(old))
    }

    private inner class Turn(
        /** 1 forward, -1 back, in reading order. */
        val dir: Int,
        /** The page on screen when the turn began. */
        val shot: Bitmap,
        val byTap: Boolean,
        startX: Float,
        startY: Float,
    ) {
        val width = area.width.toFloat()
        val height = area.height.toFloat()
        private val single = !spread

        // Canonical coordinates (see CurlShape): the leaf hinged on the left and turning leftward.
        // A single page is hinged at the reading-start edge and a page going back comes in turned
        // over; in a spread every turn is the same picture, mirrored when it goes rightward.
        val mirror = if (single) rtl else (dir > 0) == rtl
        private val fromTurned = single && dir < 0
        private val hinge = if (single) 0f else width / 2
        private val leafWidth = if (single) width else width / 2
        private fun canonX(x: Float) = if (mirror) width - x else x

        val grip = CurlGrip(hinge, leafWidth, height, canonX(startX), if (byTap) height else startY, fromTurned)

        /** The page the turn goes to, once laid out under the overlay. */
        var target: Bitmap? = null
            private set
        var jumped: Boolean? = null
            private set
        private val onJump = ArrayList<(Boolean) -> Unit>()

        // Where the free-edge point is, and its speed (canonical px, px/s).
        private var px: Float
        private var py: Float
        private var vx = 0f
        private var vy = 0f

        // The settle animation: toward (tx, ty), or along the tap path by [tapT].
        private var animating = false
        private var commit = false
        private var tx = 0f
        private var ty = 0f
        private var tapT = 0f
        private var tapV = 0f
        private var stiffness = DRAG_STIFFNESS
        private var lastFrameNanos = 0L
        private var finished = false
        private val startedAt = SystemClock.uptimeMillis()

        init {
            val (x, y) = grip.rest(commit = false)
            px = x
            py = y
            if (byTap && fromTurned) tapT = 1f
        }

        fun uses(bitmap: Bitmap) = bitmap === shot || bitmap === target

        /** Puts the snapshot over the page, then has foliate jump underneath it. */
        fun start() {
            onTurnStart()
            surface.show(frame()) {
                if (turn !== this) return@show
                val id = nextJumpId++
                jumpWaiters[id] = { moved -> onJumped(moved) }
                jump(dir, id)
            }
            if (byTap) whenJumped { moved -> if (moved) settle(true, 0f, 0f) else finish(committed = false) }
        }

        private fun onJumped(moved: Boolean) {
            jumped = moved
            if (moved) captureTarget()
            redraw()
            val waiting = onJump.toList()
            onJump.clear()
            waiting.forEach { it(moved) }
        }

        fun whenJumped(block: (Boolean) -> Unit) {
            val done = jumped
            if (done != null) block(done) else onJump += block
        }

        fun captureTarget() {
            // Two frames: the page the jump went to is on screen.
            webView.postOnAnimation {
                webView.postOnAnimation {
                    if (turn !== this) return@postOnAnimation
                    withSnapshot(freshFor = 0L) { shot ->
                        if (turn !== this || shot == null) return@withSnapshot
                        target = shot
                        redraw()
                    }
                }
            }
        }

        /** The finger is at ([x], [y]), view coordinates. */
        fun drag(x: Float, y: Float) {
            val (nx, ny) = grip.point(canonX(x), y)
            // No page that way (the book's end): the leaf only gives a little.
            if (jumped == false) {
                val (rx, ry) = grip.rest(commit = false)
                px = rx + (nx - rx) * 0.15f
                py = ry + (ny - ry) * 0.15f
            } else {
                px = nx
                py = ny
            }
            redraw()
        }

        /** Let go with the finger's velocity (view px/s). */
        fun release(velocityX: Float, velocityY: Float) {
            val cvx = if (mirror) -velocityX else velocityX
            val commits = jumped != false && grip.commits(px, cvx, minFling)
            settle(commits, cvx, velocityY)
        }

        fun settle(commits: Boolean, velocityX: Float, velocityY: Float) {
            commit = commits
            if (byTap) {
                tapV = 0f
                stiffness = TAP_STIFFNESS
            } else {
                val (x, y) = grip.rest(commits)
                tx = x
                ty = y
                vx = velocityX
                vy = velocityY
                stiffness = DRAG_STIFFNESS
            }
            animating = true
            lastFrameNanos = 0L
            choreographer.postFrameCallback(::step)
            log("curl dir=$dir tap=$byTap commit=$commits")
        }

        /**
         * Ends a turn that is settling onto the page it went to at once, for the next turn to start
         * from that page (quick taps or swipes in a row). False when it can't: the jump isn't done,
         * or the turn is going back.
         */
        fun completeNow(): Boolean {
            // Only a turn already let go toward the page it jumped to.
            if (!(animating || finished) || !commit || jumped != true) return false
            animating = false
            finished = true
            turn = null
            adopt(target)
            surface.releaseAfterDrawing(listOf(shot).filter { it !== current })
            // The next turn's first frame replaces this; if none comes, the overlay comes off.
            surface.show(null)
            log("curl-cut dir=$dir ${SystemClock.uptimeMillis() - startedAt} ms")
            return true
        }

        private fun step(frameNanos: Long) {
            if (!animating || turn !== this) return
            val dt = if (lastFrameNanos == 0L) 1f / 120f else ((frameNanos - lastFrameNanos) / 1e9f).coerceIn(1f / 240f, 1f / 30f)
            lastFrameNanos = frameNanos
            val damping = 2f * DAMPING_RATIO * sqrt(stiffness)
            val done = if (byTap) {
                val goal = if (commit != fromTurned) 1f else 0f
                // Semi-implicit Euler, in small steps for stability at high stiffness.
                var left = dt
                while (left > 0f) {
                    val h = minOf(left, 1f / 480f)
                    tapV += (-stiffness * (tapT - goal) - damping * tapV) * h
                    tapT += tapV * h
                    left -= h
                }
                val (x, y) = tapPath(hinge, leafWidth, height, tapT.coerceIn(0f, 1f))
                px = x
                py = y
                abs(tapT - goal) < 0.002f && abs(tapV) < 0.02f
            } else {
                var left = dt
                while (left > 0f) {
                    val h = minOf(left, 1f / 480f)
                    vx += (-stiffness * (px - tx) - damping * vx) * h
                    vy += (-stiffness * (py - ty) - damping * vy) * h
                    px += vx * h
                    py += vy * h
                    left -= h
                }
                // The leaf may not pass its rests, or the paper would tear.
                val (cx, cy) = grip.constrain(px, py)
                px = cx
                py = cy
                abs(px - tx) < 0.5f && abs(py - ty) < 0.5f && abs(vx) < 30f && abs(vy) < 30f
            }
            if (done) {
                animating = false
                if (byTap) {
                    val (x, y) = tapPath(hinge, leafWidth, height, if (commit != fromTurned) 1f else 0f)
                    px = x
                    py = y
                } else {
                    px = tx
                    py = ty
                }
                redraw()
                finish(commit)
            } else {
                redraw()
                choreographer.postFrameCallback(::step)
            }
        }

        /** Takes the overlay off once the live page shows what it shows. */
        fun finish(committed: Boolean) {
            if (finished) return
            finished = true
            whenJumped { moved ->
                if (!moved || committed) return@whenJumped reveal(committed && moved)
                // Back where the turn began: undo the jump under the overlay first.
                val id = nextJumpId++
                jumpWaiters[id] = { reveal(false) }
                jump(-dir, id)
            }
        }

        private fun reveal(turned: Boolean) {
            surface.show(null) {
                if (turn !== this) return@show
                turn = null
                // What the overlay showed is the page on screen now.
                val keep = if (turned) target else shot
                adopt(keep)
                surface.releaseAfterDrawing(listOfNotNull(shot, target).filter { it !== keep })
                log("curl-done dir=$dir turned=$turned ${SystemClock.uptimeMillis() - startedAt} ms")
                val queued = queuedTap
                queuedTap = 0
                if (queued != 0) main.post { tap(queued) }
            }
        }

        fun cancelNow() {
            animating = false
            finish(committed = false)
        }

        fun redraw() {
            if (turn !== this) return
            surface.show(frame())
        }

        private fun frame(): CurlFrame {
            val shape = CurlShape(hinge, leafWidth, height, grip.cornerX, grip.cornerY, px, py)
            val underLive = jumped == true
            return when {
                single && !fromTurned -> CurlFrame(width, height, shape, mirror, front = shot, frontLive = false, back = null, backIsNextPage = false, still = null, stillEverywhere = false, underLive = underLive, under = target, paper = paper, dark = dark)
                single -> CurlFrame(width, height, shape, mirror, front = target, frontLive = true, back = null, backIsNextPage = false, still = shot, stillEverywhere = true, underLive = true, under = null, paper = paper, dark = dark)
                else -> CurlFrame(width, height, shape, mirror, front = shot, frontLive = false, back = target, backIsNextPage = true, still = shot, stillEverywhere = false, underLive = underLive, under = target, paper = paper, dark = dark)
            }
        }
    }

    private fun cancelAll() {
        dragging = false
        turn?.cancelNow()
        queuedTap = 0
    }

    // endregion

    // region Gesture

    private var downX = 0f

    // Where the page's view sits in the window (touches are in the view's terms, the curl in the window's).
    private var offX = 0f
    private var offY = 0f
    private var downY = 0f
    private var downAt = 0L
    private var dragging = false
    private var selecting = false
    private var ignoring = false
    private var dragTurn: Turn? = null
    private var lastX = 0f
    private var lastY = 0f
    private var velocity: VelocityTracker? = null

    private fun onTouch(event: MotionEvent): Boolean {
        if (!active && !dragging) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pageOffset().let { (x, y) ->
                    offX = x
                    offY = y
                }
                downAt = event.eventTime
                dragging = false
                selecting = false
                ignoring = false
                dragTurn = null
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                // Fresh for a turn this touch may start.
                if (turn == null) capture()
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                lastX = event.x
                lastY = event.y
                if (!dragging) {
                    if (selecting || ignoring) return ignoring
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) < touchSlop && abs(dy) < touchSlop) return false
                    if (event.eventTime - downAt >= longPress) {
                        selecting = true
                        return false
                    }
                    if (abs(dx) < abs(dy) || event.pointerCount > 1) return false
                    // The page never sees the rest of this gesture.
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    webView.onTouchEvent(cancel)
                    cancel.recycle()
                    if (controlsShown) {
                        // The controls are over the page: this swipe puts them away.
                        onTurnStart()
                        ignoring = true
                        return true
                    }
                    val running = turn
                    if (running != null && !running.completeNow()) {
                        // A turn that can't be cut short (still jumping, or going back): this drag is spent.
                        ignoring = true
                        return true
                    }
                    dragging = true
                    val leftward = dx < 0
                    val dir = if (leftward != rtl) 1 else -1
                    log("curl-drag dx=$dx downX=$downX x=${event.x} rtl=$rtl spread=$spread dir=$dir")
                    val startX = downX + offX
                    val startY = downY + offY
                    withSnapshot(freshFor = DRAG_SNAPSHOT_MAX_AGE_MS) { shot ->
                        if (!dragging || shot == null || turn != null || !active) return@withSnapshot
                        val t = Turn(dir, shot, byTap = false, startX = startX, startY = startY)
                        turn = t
                        dragTurn = t
                        t.start()
                        t.drag(lastX + offX, lastY + offY)
                    }
                }
                dragTurn?.drag(event.x + offX, event.y + offY)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (ignoring) {
                    ignoring = false
                    return true
                }
                if (!dragging) return false
                dragging = false
                val tracker = velocity
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1000)
                val vx = if (event.actionMasked == MotionEvent.ACTION_UP) tracker?.xVelocity ?: 0f else 0f
                val vy = if (event.actionMasked == MotionEvent.ACTION_UP) tracker?.yVelocity ?: 0f else 0f
                val t = dragTurn
                dragTurn = null
                if (t != null) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) t.release(vx, vy) else t.settle(false, 0f, 0f)
                } else {
                    // The snapshot wasn't there before the finger lifted: a quick flick still turns.
                    captureWaiters.clear()
                    val dx = event.x - downX
                    if (event.actionMasked == MotionEvent.ACTION_UP && abs(dx) > touchSlop * 2) tap(if ((dx < 0) != rtl) 1 else -1)
                }
                return true
            }
        }
        return dragging || ignoring
    }

    // endregion

    fun release() {
        cancelAll()
        velocity?.recycle()
        velocity = null
        surface.release()
        current?.let { surface.releaseAfterDrawing(listOf(it)) }
        current = null
        staging?.recycle()
        staging = null
    }

    private fun Context.findActivity(): Activity? {
        var context: Context? = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    private companion object {
        /** A snapshot older than this is retaken before a drag's turn (it's taken on every touch-down). */
        const val DRAG_SNAPSHOT_MAX_AGE_MS = 1_500L
        const val TAP_SNAPSHOT_MAX_AGE_MS = 1_500L

        // Springs (stiffness in 1/s²): a drag's settle carries the finger's speed; a tap's sweep is
        // a little softer, so it reads as a page turned, not snapped.
        const val DRAG_STIFFNESS = 260f
        const val TAP_STIFFNESS = 190f
        const val DAMPING_RATIO = 0.96f
    }
}
