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
import it.sunw.widget.R
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
 * day headers (weekday, max/min), weather icons and wind (direction, km/h) every 3 hours, temperature curve coloured like the pills over day/night
 * shading, rain bars (stronger when more likely) and the current hour.
 * Wider than the screen: it sits in a horizontal scroll view. Tapping a day opens it.
 */
class MeteogramView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var palette: Palette? = null
    private var hours = emptyList<Forecast.Hour>()
    private var days = emptyList<Forecast.Day>()
    private var now: LocalDateTime? = null

    /** Leading slots before the forecast starts: only the dimmed temperature of the last 24 hours. */
    private var pastCount = 0

    /** Slot under the finger after a long press, or -1. */
    private var cursor = -1

    /** Called with the date of the tapped day column. */
    var onDayTap: ((LocalDate) -> Unit)? = null

    /**
     * [past] (Open-Meteo) adds the last 24 hours to the left of the forecast and replaces the
     * forecast temperatures of the hours already gone; everything before "now" is drawn dimmed.
     */
    fun show(palette: Palette, forecast: Forecast, now: LocalDateTime, past: PastTemperatures? = null) {
        this.palette = palette
        val start = now.toLocalDate().atStartOfDay()
        val ahead = forecast.hours.filter { !it.time.isBefore(start) }.take(MAX_HOURS)
        val thisHour = now.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        val lead = ArrayList<Forecast.Hour>()
        if (past != null && ahead.isNotEmpty()) {
            val daylight = ahead.take(24).associate { it.time.hour to it.isDaylight }
            var t = thisHour.minusHours(24)
            while (t.isBefore(start)) {
                past[t]?.let { lead += blank(t, it, daylight[t.hour] ?: (t.hour in 7..18)) }
                t = t.plusHours(1)
            }
        }
        pastCount = lead.size
        hours = lead + ahead.map { h -> if (past != null && h.time.isBefore(thisHour)) past[h.time]?.let { h.copy(temperature = it) } ?: h else h }
        cursor = -1
        days = forecast.days.filter { !it.date.isBefore(now.toLocalDate()) }
        this.now = now
        contentDescription = null
        requestLayout()
        invalidate()
    }

    private fun blank(time: LocalDateTime, temperature: Double, daylight: Boolean) = Forecast.Hour(
        time = time, temperature = temperature, feltTemperature = temperature, precipitation = 0.0,
        precipitationProbability = 0, windSpeed = 0.0, windDirection = 0, condition = Condition.CLEAR,
        uvIndex = 0, isDaylight = daylight,
    )

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
        setMeasuredDimension(w, dp(HEADER_DP + ICON_DP + TEMP_DP + RAIN_DP + WIND_DP + AXIS_DP).roundToInt())
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val p = palette ?: return
        if (hours.isEmpty()) return
        val header = dp(HEADER_DP)
        val iconTop = header
        val tempTop = iconTop + dp(ICON_DP)
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
            if (!h.isDaylight) canvas.drawRect(x(i.toDouble()), iconTop, x(i + 1.0), windBottom, paint)
        }

        // What the weather does, every 3 hours: the most significant condition of the three.
        val iconPx = dp(ICON_SIZE_DP).roundToInt()
        for (i in pastCount until hours.size step 3) {
            val block = hours.subList(i, minOf(i + 3, hours.size))
            val mid = block[block.size / 2]
            val condition = block.maxBy { it.condition.severity }.condition
            val d = icon(WeatherIcons.icon(condition, !mid.isDaylight), p.text) ?: continue
            val cx = x(i + block.size / 2.0).roundToInt()
            val top = (iconTop + (dp(ICON_DP) - iconPx) / 2).roundToInt()
            d.setBounds(cx - iconPx / 2, top, cx + iconPx / 2, top + iconPx)
            d.draw(canvas)
        }

        // Midnight separators and day headers.
        val dayFormat = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.getDefault())
        paint.textAlign = Paint.Align.CENTER
        if (pastCount > 0 && hours[0].time.hour != 0) {
            // The part of yesterday that is still in the last 24 hours, dimmed.
            val mid = x(((24 - hours[0].time.hour) / 2.0).coerceAtMost(pastCount.toDouble()))
            paint.style = Paint.Style.FILL
            paint.color = p.textSecondary
            paint.textSize = sp(12.5f)
            canvas.drawText(dayFormat.format(hours[0].time.toLocalDate()).replace(".", "").replaceFirstChar { it.titlecase() }, mid, dp(15f), paint)
        }
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
        val elapsed = now?.let { slot(it).toInt() } ?: 0
        for (i in 0 until n - 1) {
            paint.color = WeatherCard.temperatureColor((temps[i] + temps[i + 1]) / 2)
            paint.alpha = if (i < elapsed) PAST_ALPHA else 255
            canvas.drawLine(cxOf(i), ty(temps[i]), cxOf(i + 1), ty(temps[i + 1]), paint)
        }
        paint.alpha = 255
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

        // Wind every 3 hours: an arrow where it blows to, km/h below, stronger winds brighter.
        for (i in pastCount until hours.size step 3) {
            val block = hours.subList(i, minOf(i + 3, hours.size))
            val mid = block[block.size / 2]
            val speed = block.maxOf { it.windSpeed }
            val cx = x(i + block.size / 2.0)
            val color = windColor(speed, p)
            drawArrow(canvas, cx, windTop + dp(11f), mid.windDirection, color)
            paint.style = Paint.Style.FILL
            paint.color = color
            paint.textSize = sp(10.5f)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${speed.roundToInt()}", cx, windBottom - dp(5f), paint)
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

        if (cursor in 0 until n) drawCursor(canvas, p, cursor, cxOf(cursor), ty(temps[cursor]), header, windBottom, elapsed)
    }

    /** Line, dot and a label with the time and temperature of slot [i], kept inside the visible part. */
    private fun drawCursor(canvas: Canvas, p: Palette, i: Int, cx: Float, cy: Float, top: Float, bottom: Float, elapsed: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = p.text
        canvas.drawLine(cx, top, cx, bottom, paint)
        paint.style = Paint.Style.FILL
        paint.color = p.cardBackground
        canvas.drawCircle(cx, cy, dp(5.5f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)
        paint.color = p.text
        canvas.drawCircle(cx, cy, dp(5.5f), paint)

        // Small time and date on top, the temperature large below.
        val label = cursorLabel(i, elapsed)
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = sp(11f)
        val w1 = paint.measureText(label.first)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = sp(16f)
        val w2 = paint.measureText(label.second)
        val boxW = max(w1, w2) + dp(20f)
        val boxH = dp(42f)
        val visible = android.graphics.Rect().also { getLocalVisibleRect(it) }
        val boxLeft = (cx - boxW / 2).coerceIn(visible.left + dp(4f), max(visible.left + dp(4f), visible.right - boxW - dp(4f)))
        val boxTop = top + dp(2f)
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.color = Palette.blend(p.cardBackground, Color.WHITE, 0.14f)
        canvas.drawRoundRect(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH, dp(9f), dp(9f), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sp(11f)
        paint.color = if (i < elapsed) p.textSecondary else p.accent
        canvas.drawText(label.first, boxLeft + boxW / 2, boxTop + dp(15f), paint)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = sp(16f)
        paint.color = p.text
        canvas.drawText(label.second, boxLeft + boxW / 2, boxTop + dp(35f), paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
    }

    /** "Sat 10/10 · 14:00 · forecast" (or Open-Meteo / now) and "15.3°". */
    internal fun cursorLabel(i: Int, elapsed: Int = now?.let { slot(it).toInt() } ?: 0): Pair<String, String> {
        val h = hours[i]
        val whenText = DateTimeFormatter.ofPattern("EEE dd/MM · HH:mm", Locale.getDefault()).format(h.time)
            .replace(".", "").replaceFirstChar { it.titlecase() }
        val kind = when {
            i < elapsed -> context.getString(R.string.mg_cursor_past)
            i == elapsed -> context.getString(R.string.mg_cursor_now)
            else -> context.getString(R.string.mg_cursor_forecast)
        }
        return "$whenText · $kind" to String.format(Locale.getDefault(), "%.1f°", h.temperature)
    }

    /** For tests: shows the cursor on slot [i] (or hides it with -1). */
    internal fun setCursor(i: Int) {
        cursor = if (i in hours.indices) i else -1
        invalidate()
    }

    internal val cursorSlot: Int get() = cursor
    internal val leadingSlots: Int get() = pastCount
    internal val slots: Int get() = hours.size

    /** Calm winds dim, fresh ones in the text colour, strong ones orange, gales red. */
    private fun windColor(kmh: Double, p: Palette): Int = when {
        kmh >= 60 -> 0xFFFF6B6B.toInt()
        kmh >= 35 -> 0xFFFFB36B.toInt()
        kmh >= 15 -> p.text
        else -> p.textSecondary
    }

    private val icons = HashMap<Int, android.graphics.drawable.Drawable>()

    private fun icon(res: Int, color: Int): android.graphics.drawable.Drawable? =
        icons.getOrPut(res) { context.getDrawable(res)?.mutate() ?: return null }.apply { setTint(color) }

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
    private var pressing = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private fun slotAt(px: Float) = ((px - left) / hourWidth).toInt().coerceIn(0, max(hours.size - 1, 0))

    /** A long press starts the cursor: from then on the finger moves it and the page does not scroll. */
    private val startCursor = Runnable {
        if (!pressing || hours.isEmpty()) return@Runnable
        cursor = slotAt(downX)
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
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
                    cursor = slotAt(event.x)
                    invalidate()
                } else if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                    removeCallbacks(startCursor)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressing = false
                removeCallbacks(startCursor)
                val showedCursor = cursor >= 0
                cursor = -1
                if (showedCursor) {
                    invalidate()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                if (event.actionMasked == MotionEvent.ACTION_UP && abs(event.x - downX) < slop && abs(event.y - downY) < slop && hours.isNotEmpty()) {
                    // Yesterday's dimmed hours have no day page to open.
                    val i = slotAt(event.x)
                    if (i >= pastCount) onDayTap?.invoke(hours[i].time.toLocalDate())
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
        private const val PAST_ALPHA = 110
        private const val HEADER_DP = 38f
        private const val ICON_DP = 26f
        private const val ICON_SIZE_DP = 18f
        private const val TEMP_DP = 112f
        private const val RAIN_DP = 46f
        private const val WIND_DP = 40f
        private const val AXIS_DP = 18f
        private const val RAIN = 0xFF5B9BE8.toInt()
    }
}
