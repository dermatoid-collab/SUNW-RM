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
import kotlin.math.abs
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
 * One series over the slider's range (−[Chart.maxOffset]…+[Chart.maxOffset] days from today),
 * as a filled line with optional dashed marks (solstices, clock changes), moon-phase dots, month
 * ticks, a line on today and a dot on the chosen date. Touching or dragging it picks the date.
 */
class YearChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** A dashed vertical line, with a [label] under it (or at the top of the plot). */
    class Mark(val offset: Int, val label: String? = null, val color: Int? = null, val labelOnTop: Boolean = false)

    /** A moon-phase dot on the bottom row. */
    class Dot(val offset: Double, val filled: Boolean)

    class Chart(
        /** One value per day, index = offset + maxOffset; NaN leaves a gap. */
        val values: DoubleArray,
        val maxOffset: Int,
        val lineColor: Int,
        /** A step between two days bigger than this starts a new segment (clock changes). */
        val breakJump: Double = Double.MAX_VALUE,
        val marks: List<Mark> = emptyList(),
        val dots: List<Dot> = emptyList(),
        /** Month ticks along the bottom: (offset, label). */
        val ticks: List<Pair<Int, String>> = emptyList(),
        /** Keep a bottom row for labels, ticks and dots. */
        val bottomRow: Boolean = true,
    )

    private var palette: Palette? = null
    private var chart: Chart? = null
    private var selected = 0
    private var low = 0.0
    private var high = 1.0

    /** Called with the day offset (from today) under the finger. */
    var onPick: ((Int) -> Unit)? = null

    fun show(palette: Palette, chart: Chart) {
        this.palette = palette
        this.chart = chart
        // The scale is worked out once, not at every redraw while a finger drags along the chart.
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        for (v in chart.values) if (!v.isNaN()) { lo = minOf(lo, v); hi = maxOf(hi, v) }
        val pad = max((hi - lo) * 0.06, 0.5)
        low = lo - pad
        high = hi + pad
        invalidate()
    }

    fun select(offset: Int) {
        selected = offset
        invalidate()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelRoom get() = if (chart?.bottomRow == false) dpf(4f) else dpf(15f)
    private fun x(offset: Double, c: Chart) = dpf(5f) + ((offset + c.maxOffset) / (2 * c.maxOffset)).toFloat() * (width - dpf(10f))

    override fun onDraw(canvas: Canvas) {
        val p = palette ?: return
        val c = chart ?: return
        val top = dpf(8f)
        val bottom = height - labelRoom
        if (c.values.all { it.isNaN() }) return
        val lo = low
        val hi = high
        fun y(v: Double) = bottom - ((v - lo) / (hi - lo)).toFloat() * (bottom - top)

        val line = Path()
        var started = false
        var previous = Double.NaN
        c.values.forEachIndexed { i, v ->
            if (v.isNaN()) { started = false; return@forEachIndexed }
            val px = x((i - c.maxOffset).toDouble(), c)
            if (!started || abs(v - previous) > c.breakJump) line.moveTo(px, y(v)) else line.lineTo(px, y(v))
            started = true
            previous = v
        }
        paint.style = Paint.Style.FILL
        paint.color = c.lineColor
        paint.alpha = 28
        canvas.drawPath(Path(line).apply {
            // Fill down to the base only for charts without clock jumps (a filled step looks wrong).
            if (c.breakJump == Double.MAX_VALUE) {
                lineTo(x(c.maxOffset.toDouble(), c), bottom)
                lineTo(x(-c.maxOffset.toDouble(), c), bottom)
                close()
            }
        }, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(1.8f)
        paint.alpha = 220
        canvas.drawPath(line, paint)

        paint.textSize = dpf(9.5f)
        paint.textAlign = Paint.Align.CENTER
        for (mark in c.marks) {
            val mx = x(mark.offset.toDouble(), c)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpf(1f)
            paint.color = mark.color ?: p.textSecondary
            paint.alpha = 100
            paint.pathEffect = DashPathEffect(floatArrayOf(dpf(2f), dpf(3f)), 0f)
            canvas.drawLine(mx, top, mx, bottom, paint)
            paint.pathEffect = null
            mark.label?.let {
                paint.style = Paint.Style.FILL
                paint.alpha = 255
                paint.textAlign = if (mark.labelOnTop) (if (mx < width / 2f) Paint.Align.LEFT else Paint.Align.RIGHT) else Paint.Align.CENTER
                val tx = if (mark.labelOnTop) mx + (if (mx < width / 2f) dpf(4f) else -dpf(4f)) else mx.coerceIn(dpf(22f), width - dpf(22f))
                canvas.drawText(it, tx, if (mark.labelOnTop) top + dpf(9f) else height - dpf(2f), paint)
                paint.textAlign = Paint.Align.CENTER
            }
        }
        paint.color = p.textSecondary
        for ((offset, label) in c.ticks) {
            val tx = x(offset.toDouble(), c)
            paint.style = Paint.Style.STROKE
            paint.alpha = 120
            canvas.drawLine(tx, bottom, tx, bottom + dpf(3f), paint)
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            canvas.drawText(label, tx, height - dpf(2f), paint)
        }
        for (dot in c.dots) {
            val dx = x(dot.offset, c)
            paint.style = Paint.Style.FILL
            paint.color = if (dot.filled) MOON_DOT else p.cardBackground
            canvas.drawCircle(dx, height - dpf(5f), dpf(3.2f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpf(1f)
            paint.color = MOON_DOT
            canvas.drawCircle(dx, height - dpf(5f), dpf(3.2f), paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(1f)
        paint.color = p.text
        paint.alpha = 130
        canvas.drawLine(x(0.0, c), top, x(0.0, c), bottom, paint)

        val index = (selected + c.maxOffset).coerceIn(0, c.values.size - 1)
        val v = c.values[index]
        if (!v.isNaN()) {
            val sx = x(selected.toDouble(), c)
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            paint.color = p.cardBackground
            canvas.drawCircle(sx, y(v), dpf(7f), paint)
            paint.color = c.lineColor
            canvas.drawCircle(sx, y(v), dpf(5f), paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val c = chart ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val fraction = ((event.x - dpf(5f)) / (width - dpf(10f))).coerceIn(0f, 1f)
                val offset = Math.round(fraction * 2 * c.maxOffset) - c.maxOffset
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

    companion object {
        private const val MOON_DOT = 0xFFECE6D2.toInt()
    }
}
