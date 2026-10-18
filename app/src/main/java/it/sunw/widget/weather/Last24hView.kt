package it.sunw.widget.weather

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import it.sunw.widget.Palette
import it.sunw.widget.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The temperature of the last 24 hours, one point every 15 minutes (hourly when that is all there
 * is): minimum, maximum, range and change in the last hour on top, then the curve over day/night
 * with its coldest and warmest points. A long press starts a cursor with the time and temperature.
 */
class Last24hView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var palette: Palette? = null
    private var points = emptyList<Pair<LocalDateTime, Double>>()
    private var daylight: (LocalDateTime) -> Boolean = { it.hour in 7..18 }
    private var kind = Kind.TEMPERATURE

    /** What the chart shows: the temperature (degrees, coloured by value) or the sea-level pressure (mbar). */
    enum class Kind { TEMPERATURE, PRESSURE }

    private var cursor = -1
    private var pressing = false
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Coldest and warmest points, or null without data. */
    internal val minIndex: Int get() = points.indices.minByOrNull { points[it].second } ?: -1
    internal val maxIndex: Int get() = points.indices.maxByOrNull { points[it].second } ?: -1
    internal val pointCount: Int get() = points.size
    internal val cursorIndex: Int get() = cursor

    fun show(palette: Palette, points: List<Pair<LocalDateTime, Double>>, isDaylight: (LocalDateTime) -> Boolean, kind: Kind = Kind.TEMPERATURE) {
        this.kind = kind
        this.palette = palette
        this.points = points
        this.daylight = isDaylight
        cursor = -1
        contentDescription = null
        invalidate()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(TILES_DP + CHART_DP).roundToInt())
    }

    private val left get() = dp(6f)
    private val chartTop get() = dp(TILES_DP + 22f)
    private val chartBottom get() = dp(TILES_DP + 160f)

    private fun x(i: Int): Float {
        if (points.size < 2) return left
        val first = points.first().first
        val span = Duration.between(first, points.last().first).toMinutes().toFloat()
        val m = Duration.between(first, points[i].first).toMinutes().toFloat()
        return left + m / span * (width - 2 * left)
    }

    private fun indexAt(px: Float): Int {
        if (points.isEmpty()) return -1
        return points.indices.minBy { abs(x(it) - px) }
    }

    private val temps get() = points.map { it.second }
    private fun lo() = floor(temps.min() - 1)
    private fun hi() = max(ceil(temps.max() + 1), lo() + 4)
    private fun y(t: Double) = (chartBottom - (t - lo()) / (hi() - lo()) * (chartBottom - chartTop)).toFloat()

    private fun deg1(v: Double) = String.format(Locale.getDefault(), if (kind == Kind.PRESSURE) "%.1f" else "%.1f°", v)

    /** The trend tile compares with this long ago: an hour for the temperature, three for the pressure. */
    private val trendMinutes get() = if (kind == Kind.PRESSURE) 180 else 60
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private fun dayClock(t: LocalDateTime) =
        DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault()).format(t).replace(".", "").replaceFirstChar { it.titlecase() }

    override fun onDraw(canvas: Canvas) {
        val p = palette ?: return
        if (points.size < 2) return
        val n = points.size
        val iMin = minIndex
        val iMax = maxIndex
        val last = n - 1
        // Change over the last hour (pressure: 3 hours): against the point that long before, when there is one.
        val hourAgo = points.indices.lastOrNull { Duration.between(points[it].first, points[last].first).toMinutes() >= trendMinutes }
        val delta = hourAgo?.let { points[last].second - points[it].second }

        // Tiles.
        val tiles = listOf(
            Triple(R.string.l24_min, deg1(points[iMin].second) to (if (kind == Kind.PRESSURE) p.text else WeatherCard.MIN_PILL), dayClock(points[iMin].first)),
            Triple(R.string.l24_max, deg1(points[iMax].second) to (if (kind == Kind.PRESSURE) p.text else WeatherCard.MAX_PILL), dayClock(points[iMax].first)),
            Triple(R.string.l24_range, deg1(points[iMax].second - points[iMin].second) to p.text, context.getString(R.string.l24_in_24h)),
            Triple(if (kind == Kind.PRESSURE) R.string.l24_3h else R.string.l24_hour, (delta?.let { (if (it >= 0) "+" else "−") + deg1(abs(it)) } ?: "—") to p.text,
                context.getString(R.string.l24_now, deg1(points[last].second))),
        )
        val gap = dp(6f)
        val cell = (width - 3 * gap) / 4
        for ((k, tile) in tiles.withIndex()) {
            val l = k * (cell + gap)
            paint.style = Paint.Style.FILL
            paint.color = p.raised(0.07f)
            canvas.drawRoundRect(RectF(l, 0f, l + cell, dp(TILES_DP - 8f)), dp(12f), dp(12f), paint)
            paint.textAlign = Paint.Align.LEFT
            paint.color = p.textSecondary
            paint.textSize = sp(10.5f)
            canvas.drawText(context.getString(tile.first), l + dp(8f), dp(14f), paint)
            paint.color = tile.second.second
            paint.textSize = sp(17f)
            canvas.drawText(tile.second.first, l + dp(8f), dp(33f), paint)
            paint.color = p.textSecondary
            paint.textSize = sp(10.5f)
            canvas.drawText(tile.third, l + dp(8f), dp(47f), paint)
        }

        // Night behind the curve.
        paint.color = Palette.blend(p.cardBackground, Color.BLACK, 0.35f)
        for (i in 0 until n - 1) {
            if (!daylight(points[i].first)) canvas.drawRect(x(i), chartTop - dp(8f), x(i + 1), chartBottom, paint)
        }

        // Grid and temperature labels.
        val step = max(1, ((hi() - lo()) / 4).roundToInt())
        paint.strokeWidth = dp(1f)
        paint.textSize = sp(10f)
        var g = lo()
        while (g <= hi()) {
            val gy = y(g)
            paint.style = Paint.Style.STROKE
            paint.color = p.raised(0.12f)
            canvas.drawLine(0f, gy, width.toFloat(), gy, paint)
            paint.style = Paint.Style.FILL
            paint.color = p.textSecondary
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(if (kind == Kind.PRESSURE) "${g.roundToInt()}" else "${g.roundToInt()}°", dp(2f), gy - dp(2f), paint)
            g += step
        }

        // Curve with a soft fill.
        val fill = Path().apply {
            moveTo(x(0), chartBottom)
            for (i in 0 until n) lineTo(x(i), y(points[i].second))
            lineTo(x(n - 1), chartBottom)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = p.text
        paint.alpha = 18
        canvas.drawPath(fill, paint)
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.6f)
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until n - 1) {
            paint.color = if (kind == Kind.PRESSURE) PRESSURE_LINE else WeatherCard.temperatureColor((points[i].second + points[i + 1].second) / 2)
            canvas.drawLine(x(i), y(points[i].second), x(i + 1), y(points[i + 1].second), paint)
        }

        // Coldest and warmest points.
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sp(11f)
        for ((i, above) in listOf(iMax to true, iMin to false)) {
            val cx = x(i).coerceIn(dp(16f), width - dp(16f))
            val cy = y(points[i].second)
            paint.style = Paint.Style.FILL
            paint.color = if (kind == Kind.PRESSURE) PRESSURE_LINE else if (above) WeatherCard.MAX_PILL else WeatherCard.MIN_PILL
            canvas.drawCircle(x(i), cy, dp(4f), paint)
            canvas.drawText(deg1(points[i].second), cx, if (above) cy - dp(8f) else cy + dp(16f), paint)
        }

        // Hour axis: every 6 hours, and "now" at the end.
        paint.color = p.textSecondary
        paint.textSize = sp(10f)
        for (i in 0 until n) {
            val t = points[i].first
            if (t.minute == 0 && t.hour % 6 == 0) canvas.drawText("%02d".format(t.hour), x(i), chartBottom + dp(18f), paint)
        }
        paint.textAlign = Paint.Align.RIGHT
        paint.color = p.accent
        canvas.drawText(context.getString(R.string.l24_now_axis), width - dp(2f), chartBottom + dp(18f), paint)

        if (cursor in 0 until n) drawCursor(canvas, p, cursor)
    }

    private fun drawCursor(canvas: Canvas, p: Palette, i: Int) {
        val cx = x(i)
        val cy = y(points[i].second)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = p.text
        canvas.drawLine(cx, chartTop - dp(8f), cx, chartBottom, paint)
        paint.style = Paint.Style.FILL
        paint.color = p.cardBackground
        canvas.drawCircle(cx, cy, dp(5.5f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)
        paint.color = p.text
        canvas.drawCircle(cx, cy, dp(5.5f), paint)

        // Small time and date on top, the temperature large below.
        val (whenText, temperature) = cursorLabel(i)
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT
        paint.textSize = sp(11f)
        val w1 = paint.measureText(whenText)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = sp(16f)
        val w2 = paint.measureText(temperature)
        val w = max(w1, w2) + dp(20f)
        val boxLeft = (cx - w / 2).coerceIn(dp(4f), max(dp(4f), width - w - dp(4f)))
        val top = chartTop - dp(6f)
        paint.typeface = Typeface.DEFAULT
        paint.color = p.raised(0.14f)
        canvas.drawRoundRect(RectF(boxLeft, top, boxLeft + w, top + dp(42f)), dp(9f), dp(9f), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sp(11f)
        paint.color = p.textSecondary
        canvas.drawText(whenText, boxLeft + w / 2, top + dp(15f), paint)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = sp(16f)
        paint.color = p.text
        canvas.drawText(temperature, boxLeft + w / 2, top + dp(35f), paint)
        paint.typeface = Typeface.DEFAULT
    }

    /** "Sat 10/10 · 14:00 · Open-Meteo" and "18.0°". */
    internal fun cursorLabel(i: Int): Pair<String, String> {
        val t = points[i].first
        val whenText = DateTimeFormatter.ofPattern("EEE dd/MM · HH:mm", Locale.getDefault()).format(t)
            .replace(".", "").replaceFirstChar { it.titlecase() }
        val value = if (kind == Kind.PRESSURE) deg1(points[i].second) + " " + context.getString(R.string.pressure_unit) else deg1(points[i].second)
        return "$whenText · ${context.getString(R.string.mg_cursor_past)}" to value
    }

    private val startCursor = Runnable {
        if (!pressing || points.isEmpty()) return@Runnable
        cursor = indexAt(downX)
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressing = true
                postDelayed(startCursor, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (cursor >= 0) {
                    cursor = indexAt(event.x)
                    invalidate()
                } else if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                    // A normal drag belongs to the page.
                    removeCallbacks(startCursor)
                    pressing = false
                    return false
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressing = false
                removeCallbacks(startCursor)
                if (cursor >= 0) {
                    cursor = -1
                    invalidate()
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        /** The pressure curve: violet, apart from the temperature's blue-to-orange. */
        internal const val PRESSURE_LINE = 0xFFB894FF.toInt()
        private const val TILES_DP = 62f
        private const val CHART_DP = 190f
    }
}
