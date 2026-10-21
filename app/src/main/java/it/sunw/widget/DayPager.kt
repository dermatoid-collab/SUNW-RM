package it.sunw.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Swipe between days like a pager, for the Sun/Moon and weather pages: the page follows the finger and
 * the neighbouring day, drawn in advance as a picture, slides in beside it, so there is never an empty
 * strip. The direction is chosen as soon as the gesture is clearly horizontal; the children (the
 * vertical scroll, buttons) then lose the gesture. Let go past a threshold, or flicked, the day changes.
 *
 * The page supplies [content] (the part that changes with the day), [hasDay] (is there a day [Int]
 * steps away), [startsOnControl] (charts and sliders that use horizontal drags themselves), [capture]
 * (draws the day [Int] steps away into a picture and leaves the page as it was) and [change] (moves
 * there, no animation). A chart with a long-press cursor is no control: a quick horizontal drag over it
 * swipes like anywhere else, and only once its cursor is up ([isBusy]) does the drag belong to the chart.
 */
internal class DayPager(
    private val activity: Activity,
    private val content: () -> View,
    private val hasDay: (Int) -> Boolean,
    private val startsOnControl: (MotionEvent) -> Boolean,
    private val capture: (Int) -> Bitmap?,
    private val change: (Int) -> Unit,
    /** True while something on the page has taken the gesture (a chart's long-press cursor): no swipe then. */
    private val isBusy: () -> Boolean = { false },
) {
    private val slop = ViewConfiguration.get(activity).scaledTouchSlop
    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Int) = v * density

    /** Pictures of the next (+1) and previous (−1) day, ready for the next drag. */
    private val ready = HashMap<Int, BitmapDrawable>()

    /** Where, in the page's own coordinates, each picture in [ready] starts: only the part in view is drawn. */
    private val offsets = HashMap<Int, Int>()

    /** The ones on screen during a drag. */
    private val shown = HashMap<Int, BitmapDrawable>()
    private var candidate = false
    private var touching = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var x = 0f
    private var velocity: VelocityTracker? = null
    private var generation = 0
    private var animator: Animator? = null

    fun dispatch(ev: MotionEvent, superDispatch: (MotionEvent) -> Boolean): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.end()
                touching = true
                candidate = !startsOnControl(ev)
                dragging = false
                downX = ev.x
                downY = ev.y
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(ev)
                if (candidate) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (!dragging && !isBusy() && abs(dx) > 2 * slop && abs(dx) > 1.5f * abs(dy)) {
                        dragging = true
                        val cancel = MotionEvent.obtain(ev).also { it.action = MotionEvent.ACTION_CANCEL }
                        superDispatch(cancel)
                        cancel.recycle()
                        showNeighbours()
                    }
                    if (dragging) {
                        follow(dx)
                        return true
                    }
                    if (abs(dy) > slop) candidate = false // a vertical scroll
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                val wasDragging = dragging
                val dx = ev.x - downX
                velocity?.addMovement(ev)
                velocity?.computeCurrentVelocity(1000)
                val vx = velocity?.xVelocity ?: 0f
                velocity?.recycle()
                velocity = null
                dragging = false
                candidate = false
                if (wasDragging) {
                    finish(dx, vx, canceled = ev.actionMasked == MotionEvent.ACTION_CANCEL)
                    return true
                }
            }
        }
        return superDispatch(ev)
    }

    /** Draws the neighbouring days again, a little later so the page is idle (call after the page has changed). */
    fun prepare() {
        generation++
        ready.clear()
        offsets.clear()
        val view = content()
        for ((i, d) in listOf(1, -1).withIndex()) schedule(view, d, generation, 250L + 150L * i, 6)
    }

    private fun schedule(view: View, d: Int, g: Int, delay: Long, tries: Int) {
        view.postDelayed({
            if (g != generation || activity.isDestroyed || !hasDay(d) || view.width <= 0) return@postDelayed
            if (touching || dragging || animator != null) {
                if (tries > 0) schedule(view, d, g, 400L, tries - 1) // never redraw under a finger
                return@postDelayed
            }
            store(view, d)
        }, delay)
    }

    /** Draws day [d] (only the part in view now) and keeps it for the next drag. */
    private fun store(view: View, d: Int) {
        val top = visibleSpan(view).first
        capture(d)?.let {
            ready[d] = BitmapDrawable(activity.resources, it)
            offsets[d] = top
        }
    }

    private fun showNeighbours() {
        val parent = content().parent as? ViewGroup ?: return
        for (d in listOf(1, -1)) {
            // Scrolled since the picture was drawn: it would show the wrong part of the page. Draw it again now.
            if (hasDay(d) && ready[d] != null && offsets[d] != visibleSpan(content()).first) store(content(), d)
            val picture = ready[d]
            if (picture != null && hasDay(d)) {
                picture.setBounds(0, 0, picture.intrinsicWidth, picture.intrinsicHeight)
                parent.overlay.add(picture)
                shown[d] = picture
            }
        }
        x = 0f
        place()
    }

    private fun hideNeighbours() {
        val parent = content().parent as? ViewGroup
        for (picture in shown.values) parent?.overlay?.remove(picture)
        shown.clear()
    }

    /** The page follows the finger; against the first and last day it resists. */
    private fun follow(dx: Float) {
        val delta = if (dx < 0) 1 else -1
        val w = content().width.toFloat().coerceAtLeast(1f)
        x = (dx * if (hasDay(delta)) 1f else 0.25f).coerceIn(-w, w)
        place()
    }

    private fun place() {
        val c = content()
        c.translationX = x
        for ((d, picture) in shown) {
            val left = (c.left + d * c.width + x).roundToInt()
            val top = c.top + (offsets[d] ?: 0)
            picture.setBounds(left, top, left + picture.intrinsicWidth, top + picture.intrinsicHeight)
        }
    }

    private fun finish(dx: Float, vx: Float, canceled: Boolean) {
        val c = content()
        val w = c.width.toFloat()
        val delta = if (dx < 0) 1 else -1
        val far = abs(dx) >= dp(SWIPE_MIN_DP)
        val flick = abs(vx) >= SWIPE_MIN_VELOCITY && abs(dx) >= dp(SWIPE_FLICK_DP)
        val go = !canceled && (far || flick) && hasDay(delta)
        if (!go) {
            settleBack()
            return
        }
        // The day changes now; what was on screen goes on as a picture and slides out while the new
        // page, from where its picture was, slides to the middle.
        val from = x
        val parent = c.parent as? ViewGroup
        val oldTop = c.top + visibleSpan(c).first
        val old = drawOnly(c)?.let { BitmapDrawable(activity.resources, it) }
        hideNeighbours()
        change(delta)
        val start = from + delta * w
        val oldTo = -delta * w
        old?.let { parent?.overlay?.add(it) }
        c.translationX = start
        play(duration(abs(start) / w), update = { t ->
            c.translationX = start * (1f - t)
            old?.let {
                val left = (c.left + from + (oldTo - from) * t).roundToInt()
                it.setBounds(left, oldTop, left + it.intrinsicWidth, oldTop + it.intrinsicHeight)
            }
        }) {
            c.translationX = 0f
            old?.let { parent?.overlay?.remove(it) }
            prepare()
        }
    }

    private fun settleBack() {
        val from = x
        if (from == 0f) {
            hideNeighbours()
            content().translationX = 0f
            return
        }
        play(duration(abs(from) / content().width.coerceAtLeast(1)), update = { t ->
            x = from * (1f - t)
            place()
        }) {
            hideNeighbours()
            x = 0f
            content().translationX = 0f
        }
    }

    private fun duration(fraction: Float): Long =
        (activity.resources.getInteger(R.integer.page_transition_ms) * fraction.coerceIn(0.4f, 1f)).toLong()

    private fun play(ms: Long, update: (Float) -> Unit, done: () -> Unit) {
        animator?.end()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = ms
        a.interpolator = AnimationUtils.loadInterpolator(activity, android.R.interpolator.fast_out_slow_in)
        a.addUpdateListener { update(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            private var finished = false
            private fun finish() {
                if (finished) return
                finished = true
                animator = null
                done()
            }
            override fun onAnimationEnd(animation: Animator) = finish()
            override fun onAnimationCancel(animation: Animator) = finish()
        })
        animator = a
        a.start()
    }

    companion object {
        private const val SWIPE_MIN_DP = 80
        private const val SWIPE_MIN_VELOCITY = 600f
        private const val SWIPE_FLICK_DP = 24

        /** Longest picture drawn: the GPU cannot take a bitmap much taller than 4096 pixels. */
        private const val MAX_PICTURE_PX = 4000

        /**
         * The rows of [view], top and bottom in its own coordinates, that are on screen now (all of it when no
         * scroll view holds it, up to the picture limit). A page much taller than the screen is drawn only there.
         */
        fun visibleSpan(view: View, height: Int = view.height): Pair<Int, Int> {
            var offset = view.top
            var parent = view.parent
            while (parent is View) {
                if (parent is ScrollView) {
                    val top = (parent.scrollY - offset).coerceIn(0, max(0, height))
                    val bottom = (parent.scrollY + parent.height - offset).coerceIn(top, max(top, height))
                    return top to min(bottom, top + MAX_PICTURE_PX)
                }
                offset += parent.top
                parent = parent.parent
            }
            return 0 to min(height, MAX_PICTURE_PX)
        }

        private fun draw(view: View, top: Int, bottom: Int): Bitmap? {
            if (view.width <= 0 || bottom <= top) return null
            val bitmap = Bitmap.createBitmap(view.width, bottom - top, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(0f, -top.toFloat())
            view.draw(canvas)
            return bitmap
        }

        /** What [view] shows now: the part of it in view (see [visibleSpan]). */
        fun drawOnly(view: View): Bitmap? {
            if (view.width <= 0 || view.height <= 0) return null
            val (top, bottom) = visibleSpan(view)
            return draw(view, top, bottom)
        }

        /** Lays [view] out as it is now (its content may just have been redrawn) and draws the part of it in view. */
        fun snapshot(view: View): Bitmap? {
            val w = view.width
            if (w <= 0) return null
            val (top, _) = visibleSpan(view) // by the page as it stands: the picture belongs to the same part of the screen
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val h = view.measuredHeight
            if (h <= 0) return null
            view.layout(view.left, view.top, view.left + w, view.top + h)
            val (_, bottom) = visibleSpan(view, h)
            // Shorter than the part in view (a short day beside a long one): the rest of the picture stays empty.
            val picture = Bitmap.createBitmap(w, max(1, bottom - top), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(picture)
            canvas.translate(0f, -top.toFloat())
            view.draw(canvas)
            return picture
        }
    }
}
