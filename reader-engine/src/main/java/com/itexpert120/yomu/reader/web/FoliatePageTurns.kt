package com.itexpert120.yomu.reader.web

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Page turns drawn natively over the foliate WebView. Each settled page is snapshotted ahead of
 * time; a turn lays that snapshot over the WebView, has foliate jump to the next page underneath
 * (no web animation at all), then moves the snapshot away with the finger or the expressive spring.
 * The motion is a plain View translation on the render thread, so it holds the display's rate
 * whatever the page is doing. The top page always leaves in the direction of travel and the target
 * page waits underneath.
 *
 * The app owns horizontal drags: once one is recognised the WebView gets a cancel and never sees
 * the rest of the gesture. Taps reach the page, which reports them back as [tap].
 */
internal class FoliatePageTurns(
    private val container: FrameLayout,
    private val webView: WebView,
    private val jump: (dir: Int, id: Int) -> Unit,
    private val log: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val over = ImageView(container.context).apply {
        scaleType = ImageView.ScaleType.FIT_XY
        visibility = View.GONE
    }

    private var snapshot: Bitmap? = null
    private var snapshotSeq = -1
    private var settledSeq = 0
    private var capturing = false
    private val snapshotWaiters = ArrayList<() -> Unit>()

    private var turn: Turn? = null
    private var queuedTap = 0
    private val jumpWaiters = HashMap<Int, (Boolean) -> Unit>()
    private var nextJumpId = 1

    /** Off in scrolled layout: no drags are taken and taps turn nothing. */
    var enabled = true

    private val touchSlop = ViewConfiguration.get(container.context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(container.context).scaledMinimumFlingVelocity

    init {
        container.addView(over, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        webView.setOnTouchListener { _, event -> onTouch(event) }
    }

    /** The page has settled and painted (`settled` from the page). */
    fun onSettled(seq: Int) {
        settledSeq = seq
        if (turn == null) captureSoon()
    }

    /** foliate finished a jump we asked for (`jumped` from the page). */
    fun onJumped(id: Int, moved: Boolean) {
        jumpWaiters.remove(id)?.invoke(moved)
    }

    /** A tap in the page's outer thirds, or a programmatic turn. */
    fun tap(dir: Int) {
        if (!enabled) return
        if (turn != null) {
            queuedTap = dir
            return
        }
        withSnapshot {
            val t = start(dir)
            // A tap waits for the new page to be under the snapshot before it moves.
            t.whenJumped { moved -> if (moved) t.settle(commit = true, velocity = 0f) else t.finish(moved = false) }
        }
    }

    // --- snapshots ---

    private fun captureSoon() {
        // Two frames: the overlay is gone and the WebView's new page is on screen.
        webView.postOnAnimation { webView.postOnAnimation { if (turn == null) capture() } }
    }

    private fun withSnapshot(then: () -> Unit) {
        if (!capturing && snapshot != null && snapshotSeq == settledSeq) return then()
        snapshotWaiters += then
        if (!capturing) capture()
    }

    private fun capture() {
        val window = (container.context as? Activity)?.window ?: return
        val width = webView.width
        val height = webView.height
        if (width == 0 || height == 0 || capturing) return
        val bitmap = snapshot?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        webView.getLocationInWindow(location)
        val seq = settledSeq
        capturing = true
        PixelCopy.request(window, Rect(location[0], location[1], location[0] + width, location[1] + height), bitmap, { result ->
            capturing = false
            if (result == PixelCopy.SUCCESS) {
                snapshot = bitmap
                snapshotSeq = seq
                // Upload it to the GPU now, not on a turn's first frame.
                bitmap.prepareToDraw()
            }
            if (snapshotSeq != settledSeq && turn == null) return@request capture()
            val waiters = snapshotWaiters.toList()
            snapshotWaiters.clear()
            waiters.forEach { it() }
        }, main)
    }

    // --- turns ---

    private fun start(dir: Int): Turn {
        val t = Turn(dir)
        turn = t
        over.setImageBitmap(snapshot)
        over.translationX = 0f
        over.visibility = View.VISIBLE
        t.requestJump(dir)
        return t
    }

    private inner class Turn(val dir: Int) {
        val startedAt = android.os.SystemClock.uptimeMillis()
        var progress = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                over.translationX = -dir * field * over.width
            }
        private var jumped: Boolean? = null
        private val onJump = ArrayList<(Boolean) -> Unit>()
        private var animator: ValueAnimator? = null
        private var released = false

        fun requestJump(direction: Int) {
            jumped = null
            val id = nextJumpId++
            jumpWaiters[id] = { moved ->
                jumped = moved
                val waiting = onJump.toList()
                onJump.clear()
                waiting.forEach { it(moved) }
            }
            jump(direction, id)
        }

        fun whenJumped(block: (Boolean) -> Unit) {
            val done = jumped
            if (done != null) block(done) else onJump += block
        }

        fun settle(commit: Boolean, velocity: Float) {
            if (released) return
            released = true
            val target = if (commit) 1f else 0f
            val from = progress
            val distance = abs(target - from)
            animator = ValueAnimator.ofFloat(from, target).apply {
                duration = (SPRING_MS * max(0.5f, sqrt(distance))).toLong()
                interpolator = ExpressiveSpring
                addUpdateListener { progress = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        progress = target
                        if (commit) {
                            whenJumped { moved -> finish(moved) }
                        } else {
                            // Back to where the finger started: undo the jump under the snapshot.
                            whenJumped { moved ->
                                if (!moved) return@whenJumped finish(moved = false)
                                requestJump(-dir)
                                whenJumped { finish(moved = false) }
                            }
                        }
                    }
                })
                start()
            }
            log("turn dir=$dir commit=$commit from=${"%.2f".format(from)} v=${"%.2f".format(velocity)}")
        }

        fun finish(moved: Boolean) {
            over.visibility = View.GONE
            over.translationX = 0f
            if (turn === this) turn = null
            log("turn-done dir=$dir moved=$moved ${android.os.SystemClock.uptimeMillis() - startedAt} ms")
            captureSoon()
            val queued = queuedTap
            queuedTap = 0
            if (queued != 0) main.post { tap(queued) }
        }
    }

    // --- gesture ---

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragTurn: Turn? = null
    private var dragDir = 0
    private var dragProgress = 0f
    private var velocity: VelocityTracker? = null

    private fun onTouch(event: MotionEvent): Boolean {
        if (!enabled && !dragging) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                dragTurn = null
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                val dx = event.x - downX
                if (!dragging) {
                    val dy = event.y - downY
                    if (abs(dx) < touchSlop || abs(dx) < abs(dy) || turn != null || event.pointerCount > 1) return false
                    dragging = true
                    dragDir = if (dx < 0) 1 else -1
                    dragProgress = 0f
                    // The page never sees the rest of this gesture.
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    webView.onTouchEvent(cancel)
                    cancel.recycle()
                    withSnapshot {
                        if (dragging) {
                            dragTurn = start(dragDir).also { it.progress = dragProgress }
                        }
                    }
                }
                dragProgress = (-dragDir * dx / container.width).coerceIn(0f, 1f)
                dragTurn?.progress = dragProgress
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                val tracker = velocity
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                val toward = -dragDir * vx
                val commit = event.actionMasked == MotionEvent.ACTION_UP &&
                    (toward > minFling * 4 || (toward > -minFling * 4 && dragProgress > COMMIT_FRACTION))
                val t = dragTurn
                if (t != null) {
                    t.settle(commit, toward)
                } else {
                    // The snapshot wasn't ready before the finger lifted; turn now if it was meant to.
                    snapshotWaiters.clear()
                    if (commit) tap(dragDir)
                }
                dragTurn = null
                return true
            }
        }
        return dragging
    }

    fun release() {
        velocity?.recycle()
        snapshot?.recycle()
        snapshot = null
    }

    private companion object {
        const val SPRING_MS = 420L
        const val COMMIT_FRACTION = 0.25f

        /** Material 3 Expressive default spatial spring (damping 0.8, stiffness 380) over [SPRING_MS]. */
        val ExpressiveSpring = TimeInterpolator { fraction ->
            val damping = 0.8
            val omega = sqrt(380.0)
            val damped = omega * sqrt(1 - damping * damping)
            val t = fraction * SPRING_MS / 1000.0
            val decay = exp(-damping * omega * t)
            (1 - decay * (cos(damped * t) + damping / sqrt(1 - damping * damping) * sin(damped * t))).toFloat()
        }
    }
}
