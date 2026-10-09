package it.sunw.widget.weather

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import it.sunw.widget.Palette
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Seven days hour by hour, in the spirit of Meteoblue's meteogram but drawn in the app's style:
 * day headers (weekday, max/min), temperature curve coloured like the pills over day/night
 * shading, rain bars (stronger when more likely), wind speed with arrows, and the current hour.
 * Wider than the screen: it sits in a horizontal scroll view. Tapping a day opens it.
 */
class MeteogramView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var palette: Palette? = null
    private var hours = emptyList<Forecast.Hour>()
    private var days = emptyList<Forecast.Day>()
    private var now: LocalDateTime? = null

    /** Called with the date of the tapped day column. */
    var onDayTap: ((LocalDate) -> Unit)? = null

    fun show(palette: Palette, forecast: Forecast, now: LocalDateTime) {
        this.palette = palette
        val start = now.toLocalDate().atStartOfDay()
        hours = forecast.hours.filter { !it.time.isBefore(start) }.take(MAX_HOURS)
        days = forecast.days.filter { !it.date.isBefore(now.toLocalDate()) }
        this.now = now
        contentDescription = null
        requestLayout()
        invalidate()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val hourWidth get() = dp(HOUR_DP)
    private val left get() = dp(4f)

    /** x of the start of hour slot [i]. */
    private fun x(i: Double) = (left + i * hourWidth).toFloat()

    /** Fractional slot index of [time], relative to the first hour. */
    private fun slot(time: LocalDateTime): Double {
        val first = hours.firstOrNull()?.time ?: return 0.0
        return java.time.Duration.between(first, time).toMinutes() / 60.0
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (2 * left + max(hours.size, 24) * hourWidth).roundToInt()
        setMeasuredDimension(w, dp(HEADER_DP + TEMP_DP + RAIN_DP + WIND_DP + AXIS_DP).roundToInt())
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val p = palette ?: return
        if (hours.isEmpty()) return
        val header = dp(HEADER_DP)
        val tempTop = header
        val tempBottom = tempTop + dp(TEMP_DP)
        val rainTop = tempBottom
        val rainBottom = rainTop + dp(RAIN_DP)
        val windTop = rainBottom
        val windBottom = windTop + dp(WIND_DP)
        val n = hours.size

        // Night shading behind every panel.
        paint.style = Paint.Style.FILL
        paint.color = Palette.blend(p.cardBackground, Color.BLACK, 0.35f)
        for ((i, h) in hours.withIndex()) {
            if (!h.isDaylight) canvas.drawRect(x(i.toDouble()), tempTop, x(i + 1.0), windBottom, paint)
        }

        // Midnight separators and day headers.
        val dayFormat = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.getDefault())
        paint.textAlign = Paint.Align.CENTER
        for ((i, h) in hours.withIndex()) {
            if (h.time.hour != 0) continue
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = Palette.blend(p.cardBackground, Color.WHITE, 0.22f)
            canvas.drawLine(x(i.toDouble()), 0f, x(i.toDouble()), windBottom, paint)
            val date = h.time.toLocalDate()
            val cx = x(i + 12.0)
            paint.style = Paint.Style.FILL
            paint.color = p.text
            paint.textSize = sp(12.5f)
            canvas.drawText(dayFormat.format(date).replace(".", "").replaceFirstChar { it.titlecase() }, cx, dp(15f), paint)
            days.firstOrNull { it.date == date }?.let { day ->
                paint.textSize = sp(11.5f)
                val max = WeatherCard.deg(day.temperatureMax)
                val min = WeatherCard.deg(day.temperatureMin)
                val gap = dp(4f)
                val wMax = paint.measureText(max)
                val wMin = paint.measureText(min)
                val startX = cx - (wMax + gap + wMin) / 2
                paint.textAlign = Paint.Align.LEFT
                paint.color = WeatherCard.MAX_PILL
                canvas.drawText(max, startX, dp(31f), paint)
                paint.color = WeatherCard.MIN_PILL
                canvas.drawText(min, startX + wMax + gap, dp(31f), paint)
                paint.textAlign = Paint.Align.CENTER
            }
        }

        // Temperature curve, coloured by value, with a soft fill.
        val temps = hours.map { it.temperature }
        val lo = floor(temps.min() - 1)
        val hi = ceil(temps.max() + 1)
        fun ty(t: Double) = (tempBottom - dp(8f) - (t - lo) / max(hi - lo, 4.0) * (tempBottom - tempTop - dp(24f))).toFloat()
        fun cxOf(i: Int) = x(i + 0.5)
        val fill = Path().apply {
            moveTo(cxOf(0), tempBottom)
            for (i in 0 until n) lineTo(cxOf(i), ty(temps[i]))
            lineTo(cxOf(n - 1), tempBottom)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = p.text
        paint.alpha = 18
        canvas.drawPath(fill, paint)
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until n - 1) {
            paint.color = WeatherCard.temperatureColor((temps[i] + temps[i + 1]) / 2)
            canvas.drawLine(cxOf(i), ty(temps[i]), cxOf(i + 1), ty(temps[i + 1]), paint)
        }
        // Each day's warmest and coldest hour, labelled.
        paint.textSize = sp(11f)
        paint.textAlign = Paint.Align.CENTER
        for ((_, idx) in hours.indices.groupBy { hours[it].time.toLocalDate() }) {
            if (idx.size < 6) continue
            val iMax = idx.maxBy { temps[it] }
            val iMin = idx.minBy { temps[it] }
            for ((i, above) in listOf(iMax to true, iMin to false)) {
                paint.style = Paint.Style.FILL
                paint.color = WeatherCard.temperatureColor(temps[i])
                canvas.drawCircle(cxOf(i), ty(temps[i]), dp(3f), paint)
                paint.color = p.text
                val y = if (above) ty(temps[i]) - dp(6f) else ty(temps[i]) + dp(14f)
                canvas.drawText(WeatherCard.deg(temps[i]), cxOf(i), y.coerceIn(tempTop + dp(10f), tempBottom - dp(1f)), paint)
            }
        }

        // Rain: bars in mm (scale at least 2 mm/h), more opaque when more likely.
        val maxRain = max(2.0, hours.maxOf { it.precipitation })
        paint.style = Paint.Style.FILL
        for ((i, h) in hours.withIndex()) {
            if (h.precipitation < 0.05) continue
            paint.color = RAIN
            paint.alpha = (90 + 165 * h.precipitationProbability / 100.0).roundToInt().coerceIn(90, 255)
            val top = rainBottom - (h.precipitation / maxRain * (rainBottom - rainTop - dp(4f))).toFloat()
            canvas.drawRect(x(i.toDouble()) + dp(0.5f), top, x(i + 1.0) - dp(0.5f), rainBottom, paint)
        }
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Palette.blend(p.cardBackground, Color.WHITE, 0.15f)
        canvas.drawLine(0f, rainBottom, width.toFloat(), rainBottom, paint)

        // Wind: speed line and an arrow (where it blows to) every 3 hours, km/h every 6.
        val maxWind = max(20.0, hours.maxOf { it.windSpeed })
        fun wy(v: Double) = (windBottom - dp(4f) - v / maxWind * (windBottom - windTop - dp(18f))).toFloat()
        paint.color = p.textSecondary
        paint.strokeWidth = dp(1.5f)
        for (i in 0 until n - 1) canvas.drawLine(cxOf(i), wy(hours[i].windSpeed), cxOf(i + 1), wy(hours[i + 1].windSpeed), paint)
        for ((i, h) in hours.withIndex()) {
            if (h.time.hour % 3 != 1) continue
            drawArrow(canvas, cxOf(i), windTop + dp(8f), h.windDirection, p.text)
            if (h.time.hour % 6 == 1) {
                paint.style = Paint.Style.FILL
                paint.color = p.textSecondary
                paint.textSize = sp(9.5f)
                canvas.drawText("${h.windSpeed.roundToInt()}", cxOf(i), windBottom - dp(2f), paint)
                paint.style = Paint.Style.STROKE
                paint.color = p.textSecondary
            }
        }

        // Hour axis.
        paint.style = Paint.Style.FILL
        paint.color = p.textSecondary
        paint.textSize = sp(10f)
        for ((i, h) in hours.withIndex()) {
            if (h.time.hour % 6 == 0 && h.time.hour != 0) canvas.drawText("%02d".format(h.time.hour), x(i.toDouble()), windBottom + dp(13f), paint)
        }

        // Now.
        now?.let { t ->
            val s = slot(t)
            if (s in 0.0..n.toDouble()) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(2f)
                paint.color = p.accent
                canvas.drawLine(x(s), header - dp(2f), x(s), windBottom, paint)
            }
        }
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, fromDegrees: Int, color: Int) {
        // MeteoBlue gives where the wind comes from; the arrow points where it goes.
        val a = Math.toRadians((fromDegrees + 180.0) % 360)
        val len = dp(6f)
        val dx = (sin(a) * len).toFloat()
        val dy = (-cos(a) * len).toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.4f)
        paint.color = color
        canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint)
        val head = dp(3.2f)
        for (side in listOf(-0.5, 0.5)) {
            val b = a + Math.PI + side
            canvas.drawLine(cx + dx, cy + dy, cx + dx + (sin(b) * head).toFloat(), cy + dy - (cos(b) * head).toFloat(), paint)
        }
    }

    /** Left edge (px) of the current hour, for the initial scroll. */
    fun nowX(): Int = now?.let { x(slot(it)).roundToInt() } ?: 0

    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) < slop && abs(event.y - downY) < slop && hours.isNotEmpty()) {
                    val i = ((event.x - left) / hourWidth).toInt().coerceIn(0, hours.size - 1)
                    onDayTap?.invoke(hours[i].time.toLocalDate())
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        /** Width of one hour: a day is 144 dp, about 2.5 days on a phone screen. */
        private const val HOUR_DP = 6f
        private const val MAX_HOURS = 7 * 24
        private const val HEADER_DP = 38f
        private const val TEMP_DP = 120f
        private const val RAIN_DP = 46f
        private const val WIND_DP = 40f
        private const val AXIS_DP = 18f
        private const val RAIN = 0xFF5B9BE8.toInt()
    }
}
