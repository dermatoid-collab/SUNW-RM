package it.sunw.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.time.Duration
import java.time.Instant
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private fun View.dpf(v: Float) = v * resources.displayMetrics.density

/**
 * The Sun's elevation over one local day, as on the widget, plus the twilight bands, blue and
 * golden hour bars along the bottom and a cursor: touch or drag to read any moment.
 */
class SunPathView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var palette: Palette? = null
    private var facts: SunDayFacts? = null
    private var start: Instant = Instant.EPOCH
    private var spanSeconds = 86_400L
    private var elevations = DoubleArray(0)
    private var range = 20.0

    /** Moment under the cursor; "now" for today until the user touches the curve. */
    var cursor: Instant? = null
        private set

    /** Called with the new cursor moment (from a touch). */
    var onCursor: ((Instant) -> Unit)? = null

    fun show(palette: Palette, facts: SunDayFacts, cursor: Instant?) {
        this.palette = palette
        this.facts = facts
        start = facts.date.atStartOfDay(facts.zone).toInstant()
        spanSeconds = Duration.between(start, facts.date.plusDays(1).atStartOfDay(facts.zone).toInstant()).seconds
        elevations = DoubleArray(SAMPLES + 1) { i ->
            SunCalculator.elevation(start.plusSeconds(spanSeconds * i / SAMPLES), facts.latitude, facts.longitude)
        }
        // Room for the bands down to −18° and for the highest Sun.
        range = max(max(elevations.max(), -elevations.min()), 20.0)
        this.cursor = cursor
        invalidate()
    }

    private val barHeight get() = dpf(4f)
    private val barGap get() = dpf(6f)
    private val inset get() = dpf(9f)
    private val curveBottom get() = height - barHeight - barGap

    private fun x(fraction: Double) = (inset + fraction * (width - 2 * inset)).toFloat()
    private fun y(elevation: Double): Float {
        val mid = curveBottom * 0.55
        val above = mid - inset
        val below = curveBottom - mid - dpf(2f)
        return (if (elevation >= 0) mid - elevation / range * above else mid - elevation / max(range, 18.0) * below).toFloat()
    }
    private fun fractionOf(t: Instant) = (Duration.between(start, t).seconds.toDouble() / spanSeconds).coerceIn(0.0, 1.0)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val p = palette ?: return
        val f = facts ?: return
        val w = width.toFloat()

        paint.style = Paint.Style.FILL
        paint.color = p.twilightBand
        for ((top, bottom, alpha) in TWILIGHT_BANDS) {
            paint.alpha = alpha
            canvas.drawRect(0f, y(top), w, y(bottom), paint)
        }
        paint.alpha = 255

        val horizon = y(SunCalculator.HORIZON_DEG)
        paint.color = p.dim
        paint.alpha = 140
        paint.strokeWidth = dpf(1f)
        canvas.drawLine(0f, horizon, w, horizon, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(3f)
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until SAMPLES) {
            val mid = (elevations[i] + elevations[i + 1]) / 2
            paint.color = p.curveColor(mid)
            paint.alpha = if (p.isDimmed(mid)) 120 else 255
            canvas.drawLine(x(i.toDouble() / SAMPLES), y(elevations[i]), x((i + 1.0) / SAMPLES), y(elevations[i + 1]), paint)
        }

        // Blue and golden hours as bars under the curve.
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        val barTop = height - barHeight
        for ((span, color) in listOf(f.morningBlue to p.blueHour, f.morningGolden to p.goldenHour, f.eveningGolden to p.goldenHour, f.eveningBlue to p.blueHour)) {
            span ?: continue
            paint.color = color
            val left = x(fractionOf(span.start))
            val right = max(x(fractionOf(span.end)), left + dpf(2f))
            canvas.drawRoundRect(RectF(left, barTop, right, height.toFloat()), barHeight / 2, barHeight / 2, paint)
        }

        val c = cursor ?: return
        val cx = x(fractionOf(c))
        val elevation = SunCalculator.elevation(c, f.latitude, f.longitude)
        val cy = y(elevation)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(1f)
        paint.color = p.text
        paint.alpha = 90
        paint.pathEffect = DashPathEffect(floatArrayOf(dpf(3f), dpf(3f)), 0f)
        canvas.drawLine(cx, 0f, cx, curveBottom, paint)
        paint.pathEffect = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        val sun = p.curveColor(elevation)
        paint.color = sun
        paint.alpha = 70
        canvas.drawCircle(cx, cy, dpf(9f), paint)
        paint.alpha = 255
        canvas.drawCircle(cx, cy, dpf(5f), paint)
        if (Color.luminance(sun) < 0.12f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpf(1.2f)
            paint.color = p.textSecondary
            canvas.drawCircle(cx, cy, dpf(5f), paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (facts == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Dragging along the curve must not scroll the page.
                parent?.requestDisallowInterceptTouchEvent(true)
                val fraction = ((event.x - inset) / (width - 2 * inset)).toDouble().coerceIn(0.0, 1.0)
                // Snap to whole minutes, at most the last minute of the day.
                val seconds = (fraction * spanSeconds).toLong().coerceAtMost(spanSeconds - 60) / 60 * 60
                val t = start.plusSeconds(seconds)
                cursor = t
                invalidate()
                onCursor?.invoke(t)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val SAMPLES = 144

        /** (top°, bottom°, alpha): civil, nautical, astronomical twilight. */
        private val TWILIGHT_BANDS = listOf(
            Triple(SunCalculator.HORIZON_DEG, -6.0, 70),
            Triple(-6.0, -12.0, 46),
            Triple(-12.0, -18.0, 26),
        )
    }
}

/** Sunrise and sunset directions on a compass rose, with the arc the Sun travels between them. */
class SunCompassView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var palette: Palette? = null
    private var rise: Double? = null
    private var set: Double? = null
    private var southern = false
    private var labels = arrayOf("N", "E", "S", "W")

    fun show(palette: Palette, riseAzimuth: Double?, setAzimuth: Double?, southernHemisphere: Boolean, cardinal: Array<String>) {
        this.palette = palette
        rise = riseAzimuth
        set = setAzimuth
        southern = southernHemisphere
        labels = cardinal
        contentDescription = null
        invalidate()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val p = palette ?: return
        val c = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - dpf(12f)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(1.5f)
        paint.color = Palette.blend(p.cardBackground, Color.WHITE, 0.2f)
        canvas.drawCircle(c, cy, r, paint)

        paint.style = Paint.Style.FILL
        paint.color = p.textSecondary
        paint.textSize = dpf(10f)
        paint.textAlign = Paint.Align.CENTER
        val half = (paint.descent() + paint.ascent()) / 2
        val lr = r + dpf(7f)
        for ((i, label) in labels.withIndex()) {
            val a = Math.toRadians(i * 90.0)
            canvas.drawText(label, c + (lr * sin(a)).toFloat(), cy - (lr * cos(a)).toFloat() - half, paint)
        }

        val up = rise
        val down = set
        if (up != null && down != null) {
            // North of the tropics the Sun passes south (clockwise on the rose), south of them north.
            val sweep = if (southern) -((up - down).mod(360.0)) else (down - up).mod(360.0)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpf(3f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = p.goldenHour
            paint.alpha = 115
            canvas.drawArc(RectF(c - r, cy - r, c + r, cy + r), (up - 90).toFloat(), sweep.toFloat(), false, paint)
            paint.alpha = 255
            paint.strokeWidth = dpf(2f)
            for ((az, color) in listOf(up to p.accent, down to SUNSET_RAY)) {
                val a = Math.toRadians(az)
                paint.color = color
                canvas.drawLine(c, cy, c + (r * sin(a)).toFloat(), cy - (r * cos(a)).toFloat(), paint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.color = p.text
        canvas.drawCircle(c, cy, dpf(2.5f), paint)
    }

    companion object {
        private const val SUNSET_RAY = 0xFFFF6A3D.toInt()
    }
}

/**
 * Day length across the slider's range, with equinox/solstice marks, a line on today and a dot
 * on the chosen date. Touching it picks the date under the finger.
 */
class YearStripView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    data class Mark(val offset: Int, val label: String)

    private var palette: Palette? = null
    private var minutes = IntArray(0)
    private var marks = emptyList<Mark>()
    private var selected = 0
    private var maxOffset = 182

    /** Called with the day offset (from today) under the finger. */
    var onPick: ((Int) -> Unit)? = null

    /** [minutes] holds the daylight for each offset −maxOffset..+maxOffset. */
    fun show(palette: Palette, minutes: IntArray, marks: List<Mark>, maxOffset: Int) {
        this.palette = palette
        this.minutes = minutes
        this.marks = marks
        this.maxOffset = maxOffset
        invalidate()
    }

    fun select(offset: Int) {
        selected = offset
        invalidate()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelRoom get() = dpf(13f)
    private fun x(offset: Int) = dpf(5f) + (offset + maxOffset).toFloat() / (2 * maxOffset) * (width - dpf(10f))

    override fun onDraw(canvas: Canvas) {
        val p = palette ?: return
        if (minutes.isEmpty()) return
        val top = dpf(6f)
        val bottom = height - labelRoom
        val lo = minutes.min()
        val hi = max(minutes.max(), lo + 30)
        fun y(m: Int) = bottom - (m - lo).toFloat() / (hi - lo) * (bottom - top)

        val line = Path()
        minutes.forEachIndexed { i, m -> if (i == 0) line.moveTo(x(i - maxOffset), y(m)) else line.lineTo(x(i - maxOffset), y(m)) }
        val area = Path(line).apply {
            lineTo(x(maxOffset), bottom)
            lineTo(x(-maxOffset), bottom)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = p.accent
        paint.alpha = 30
        canvas.drawPath(area, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(1.5f)
        paint.alpha = 180
        canvas.drawPath(line, paint)

        paint.color = p.textSecondary
        paint.strokeWidth = dpf(1f)
        paint.textSize = dpf(9.5f)
        paint.textAlign = Paint.Align.CENTER
        for (mark in marks) {
            val mx = x(mark.offset)
            paint.style = Paint.Style.STROKE
            paint.alpha = 90
            paint.pathEffect = DashPathEffect(floatArrayOf(dpf(2f), dpf(3f)), 0f)
            canvas.drawLine(mx, top, mx, bottom, paint)
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            canvas.drawText(mark.label, mx.coerceIn(dpf(20f), width - dpf(20f)), height - dpf(2f), paint)
        }

        paint.style = Paint.Style.STROKE
        paint.color = p.text
        paint.alpha = 130
        canvas.drawLine(x(0), top, x(0), bottom, paint)

        val sx = x(selected)
        val sy = y(minutes[(selected + maxOffset).coerceIn(0, minutes.size - 1)])
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.color = p.pageBackground
        canvas.drawCircle(sx, sy, dpf(7f), paint)
        paint.color = p.accent
        canvas.drawCircle(sx, sy, dpf(5f), paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (minutes.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val fraction = ((event.x - dpf(5f)) / (width - dpf(10f))).coerceIn(0f, 1f)
                val offset = Math.round(fraction * 2 * maxOffset) - maxOffset
                if (offset != selected) onPick?.invoke(offset)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
