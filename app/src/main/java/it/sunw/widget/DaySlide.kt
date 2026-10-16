package it.sunw.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The day change of the Sun/Moon and weather pages, with the motion of the page transitions
 * (`anim/page_*`): the old content slides [FRACTION] of its width away and fades out while the new one
 * slides in from the other side and fades in, so the screen is never left empty.
 */
internal object DaySlide {
    private const val FRACTION = 0.12f
    private const val TAG = R.id.day_slide_animator

    /** Runs [change] (which redraws [content] with the new day) and animates old into new; [delta] 0 only redraws. */
    fun run(content: View, delta: Int, change: () -> Unit) {
        (content.getTag(TAG) as? Animator)?.cancel()
        val parent = content.parent as? ViewGroup
        val old = if (delta != 0 && parent != null && content.width > 0 && content.height > 0) snapshot(content) else null
        change()
        if (delta == 0) return
        val width = content.width * FRACTION * delta
        val interpolator = AnimationUtils.loadInterpolator(content.context, android.R.interpolator.fast_out_slow_in)
        val left = content.left
        val top = content.top
        val ghost = old?.apply { setBounds(left, top, left + intrinsicWidth, top + intrinsicHeight) }
        if (ghost != null) parent!!.overlay.add(ghost)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = content.resources.getInteger(R.integer.page_transition_ms).toLong()
            this.interpolator = interpolator
            addUpdateListener {
                val t = it.animatedValue as Float
                content.translationX = width * (1f - t)
                content.alpha = t
                ghost?.let { g ->
                    val dx = -width * t
                    g.setBounds((left + dx).roundToInt(), top, (left + dx).roundToInt() + g.intrinsicWidth, top + g.intrinsicHeight)
                    g.alpha = ((1f - t) * 255).roundToInt()
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private fun done() {
                    content.translationX = 0f
                    content.alpha = 1f
                    ghost?.let { parent!!.overlay.remove(it) }
                    content.setTag(TAG, null)
                }
                override fun onAnimationEnd(animation: Animator) = done()
                override fun onAnimationCancel(animation: Animator) = done()
            })
        }
        content.translationX = width
        content.alpha = 0f
        content.setTag(TAG, animator)
        animator.start()
    }

    /** What is on screen now, capped to one screen height (the rest of a long page is never seen during the slide). */
    private fun snapshot(view: View): BitmapDrawable {
        val height = min(view.height, view.resources.displayMetrics.heightPixels)
        val bitmap = Bitmap.createBitmap(view.width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return BitmapDrawable(view.resources, bitmap)
    }
}
