package it.sunw.widget.weather

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import it.sunw.widget.Palette
import it.sunw.widget.R
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One day at a glance: a strip with every third hour (time, icon, temperature, rain, mean wind,
 * gust and wind direction) over a chart with the temperature curve and the rain bars of every hour,
 * the columns of the strip lining up with the hours of the chart. A long press starts a cursor
 * that reads out the values of the hour under the finger.
 */
class DayChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var palette: Palette? = null
    private var slots: Array<Forecast.Hour?> = arrayOfNulls(24)
    private var gusts: Array<Double?> = arrayOfNulls(24)
    private var hasRain = false
    private var hasGusts = false

    private var cursor = -1
    private var pressing = false
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icons = HashMap<Int, Drawable>()

    /** Hour under the cursor, or -1. */
    internal val cursorIndex: Int get() = cursor

    /** How many of the day's 24 hours the forecast has. */
    internal val hourCount: Int get() = slots.count { it != null }

    /** [hours] are the forecast hours of one day; [gust] gives the gust (km/h) of an hour, when known. */
    fun show(palette: Palette, hours: List<Forecast.Hour>, gust: (LocalDateTime) -> Double?) {
        this.palette = palette
        slots = Array(24) { h -> hours.firstOrNull { it.time.hour == h } }
        gusts = Array(24) { h -> slots[h]?.let { gust(it.time) } }
        hasRain = slots.any { (it?.precipitation ?: 0.0) >= RAIN_MIN }
        hasGusts = gusts.any { it != null }
        cursor = -1
        requestLayout()
        invalidate()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    // Vertical layout, in dp.
    private val stripHeight get() = if (hasGusts) 144f else 126f
    private val plotTop get() = stripHeight + 8f
    private val tempBottom get() = plotTop + 96f
    private val rainTop get() = tempBottom + 8f
    private val rainBottom get() = if (hasRain) rainTop + 28f else tempBottom
    private val readoutTop get() = rainBottom + 10f
    private val totalHeight get() = readoutTop + READOUT_DP

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(totalHeight).roundToInt())
    }

    private val pad get() = dp(4f)
    private val slotWidth get() = (width - 2 * pad) / 24f
    private fun x(hour: Int) = pad + (hour + 0.5f) * slotWidth
    private fun slotAt(px: Float) = ((px - pad) / slotWidth).toInt().coerceIn(0, 23)

    private fun windColor(p: Palette) = if (p.isLight) 0xFFB36B00.toInt() else 0xFFE6B36A.toInt()
    private fun rainColor(p: Palette) = if (p.isLight) 0xFF2F6FB0.toInt() else 0xFF5AA7E6.toInt()

    private fun drawText(canvas: Canvas, value: String, cx: Float, baseline: Float, sizeSp: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) {
        paint.style = Paint.Style.FILL
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textSize = sp(sizeSp)
        paint.textAlign = align
        paint.color = color
        canvas.drawText(value, cx, baseline, paint)
    }

    private fun icon(res: Int, color: Int): Drawable? =
        icons.getOrPut(res) { context.getDrawable(res)?.mutate() ?: return null }.apply { setTint(color) }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, fromDegrees: Int, color: Int) {
        // MeteoBlue gives where the wind comes from; the arrow points where it goes.
        val a = Math.toRadians((fromDegrees + 180.0) % 360)
        val len = dp(6f)
        val dx = (sin(a) * len).toFloat()
        val dy = (-cos(a) * len).toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.4f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
        canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint)
        val head = dp(3.2f)
        for (side in listOf(-0.5, 0.5)) {
            val b = a + Math.PI + side
            canvas.drawLine(cx + dx, cy + dy, cx + dx + (sin(b) * head).toFloat(), cy + dy - (cos(b) * head).toFloat(), paint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val p = palette ?: return
        if (slots.all { it == null }) return
        drawStrip(canvas, p)
        drawPlot(canvas, p)
        if (cursor in 0..23 && slots[cursor] != null) drawCursor(canvas, p, cursor)
        drawReadout(canvas, p)
    }

    /** Every third hour (02, 05, … 23): time, icon, temperature, rain, mean wind, gust and direction. */
    private fun drawStrip(canvas: Canvas, p: Palette) {
        val gustColor = windColor(p)
        for (h in COLUMN_HOURS) {
            val hour = slots[h] ?: continue
            val cx = x(h)
            drawText(canvas, "%02d".format(h), cx, dp(12f), 11.5f, p.textSecondary)
            icon(WeatherIcons.icon(hour.condition, !hour.isDaylight), p.text)?.let {
                val size = dp(26f).roundToInt()
                val left = (cx - size / 2f).roundToInt()
                val top = dp(18f).roundToInt()
                it.setBounds(left, top, left + size, top + size)
                it.draw(canvas)
            }
            drawText(canvas, WeatherCard.deg(hour.temperature), cx, dp(62f), 16f, p.text, bold = true)
            if (hour.precipitation >= RAIN_MIN) drawText(canvas, String.format(Locale.getDefault(), "%.1f", hour.precipitation), cx, dp(78f), 11f, rainColor(p))
            drawText(canvas, hour.windSpeed.roundToInt().toString(), cx, dp(98f), 13f, p.text, bold = true)
            if (hasGusts) {
                drawText(canvas, gusts[h]?.roundToInt()?.toString() ?: "—", cx, dp(114f), 11.5f, gustColor)
                drawArrow(canvas, cx, dp(131f), hour.windDirection, p.textSecondary)
            } else {
                drawArrow(canvas, cx, dp(114f), hour.windDirection, p.textSecondary)
            }
        }
    }

    private fun drawPlot(canvas: Canvas, p: Palette) {
        val top = dp(plotTop)
        val bottom = dp(rainBottom)
        val w = slotWidth

        // Night behind both charts.
        paint.style = Paint.Style.FILL
        paint.color = Palette.blend(p.cardBackground, Color.BLACK, 0.35f)
        for (h in 0..23) if (slots[h]?.isDaylight == false) canvas.drawRect(x(h) - w / 2, top, x(h) + w / 2, bottom, paint)

        // The strip's columns, continued down the chart.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = p.raised(0.12f)
        for (h in COLUMN_HOURS) canvas.drawLine(x(h), top, x(h), bottom, paint)

        drawTemperature(canvas, p)
        if (hasRain) drawRain(canvas, p)
    }

    private fun drawTemperature(canvas: Canvas, p: Palette) {
        val known = (0..23).filter { slots[it] != null }
        if (known.size < 2) return
        val temps = known.map { slots[it]!!.temperature }
        val lo = floor(temps.min() - 1)
        val hi = max(ceil(temps.max() + 1), lo + 4)
        val yTop = dp(plotTop + 14f)
        val yBottom = dp(tempBottom - 12f)
        fun y(t: Double) = (yBottom - (t - lo) / (hi - lo) * (yBottom - yTop)).toFloat()

        val fill = Path().apply {
            moveTo(x(known.first()), dp(tempBottom))
            for (h in known) lineTo(x(h), y(slots[h]!!.temperature))
            lineTo(x(known.last()), dp(tempBottom))
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
        for ((a, b) in known.zipWithNext()) {
            if (b != a + 1) continue
            val ta = slots[a]!!.temperature
            val tb = slots[b]!!.temperature
            paint.color = WeatherCard.temperatureColor((ta + tb) / 2)
            canvas.drawLine(x(a), y(ta), x(b), y(tb), paint)
        }
        // Warmest and coldest hours.
        val iMax = known.maxBy { slots[it]!!.temperature }
        val iMin = known.minBy { slots[it]!!.temperature }
        for ((h, above) in listOf(iMax to true, iMin to false)) {
            val cx = x(h).coerceIn(dp(14f), width - dp(14f))
            val t = slots[h]!!.temperature
            paint.style = Paint.Style.FILL
            paint.color = if (above) WeatherCard.MAX_PILL else WeatherCard.MIN_PILL
            canvas.drawCircle(x(h), y(t), dp(3.5f), paint)
            drawText(canvas, WeatherCard.deg(t), cx, if (above) y(t) - dp(7f) else y(t) + dp(15f), 10.5f, p.text)
        }
    }

    private fun drawRain(canvas: Canvas, p: Palette) {
        val top = dp(rainTop)
        val bottom = dp(rainBottom)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = p.raised(0.14f)
        canvas.drawLine(pad, bottom, width - pad, bottom, paint)
        val peak = slots.maxOf { it?.precipitation ?: 0.0 }
        val scale = max(peak, 1.0)
        val barWidth = slotWidth * 0.62f
        paint.style = Paint.Style.FILL
        paint.color = rainColor(p)
        var peakHour = -1
        for (h in 0..23) {
            val mm = slots[h]?.precipitation ?: continue
            if (mm < RAIN_MIN) continue
            val height = max(dp(2f), (mm / scale).toFloat() * (bottom - top - dp(10f)))
            canvas.drawRoundRect(RectF(x(h) - barWidth / 2, bottom - height, x(h) + barWidth / 2, bottom), dp(1.5f), dp(1.5f), paint)
            if (mm >= peak) peakHour = h
        }
        drawText(canvas, "mm", pad, top + dp(8f), 9.5f, p.textSecondary, align = Paint.Align.LEFT)
        if (peakHour >= 0) {
            val cx = x(peakHour).coerceIn(dp(14f), width - dp(14f))
            val barTop = bottom - max(dp(2f), (peak / scale).toFloat() * (bottom - top - dp(10f)))
            drawText(canvas, String.format(Locale.getDefault(), "%.1f", peak), cx, barTop - dp(3f), 10f, rainColor(p))
        }
    }

    private fun drawCursor(canvas: Canvas, p: Palette, i: Int) {
        val cx = x(i)
        paint.style = Paint.Style.FILL
        paint.color = p.text
        paint.alpha = 24
        canvas.drawRoundRect(RectF(cx - slotWidth / 2, 0f, cx + slotWidth / 2, dp(rainBottom)), dp(6f), dp(6f), paint)
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = p.text
        canvas.drawLine(cx, dp(plotTop), cx, dp(rainBottom), paint)
    }

    /** The readout under the chart: the values of the hour under the cursor, or a hint and the legend. */
    private fun drawReadout(canvas: Canvas, p: Palette) {
        val top = dp(readoutTop)
        paint.style = Paint.Style.FILL
        paint.color = p.raised(0.07f)
        canvas.drawRoundRect(RectF(0f, top, width.toFloat(), top + dp(READOUT_DP)), dp(10f), dp(10f), paint)
        val left = dp(10f)
        val label = if (cursor in 0..23) cursorLabel(cursor) else null
        if (label != null) {
            drawText(canvas, label.first, left, top + dp(17f), 11f, p.textSecondary, align = Paint.Align.LEFT)
            drawFitted(canvas, label.second, left, top + dp(36f), 14f, p.text, width - 2 * left, bold = true)
            drawFitted(canvas, label.third, left, top + dp(52f), 11.5f, p.textSecondary, width - 2 * left)
            return
        }
        drawText(canvas, context.getString(R.string.dc_hint), left, top + dp(20f), 11.5f, p.textSecondary, align = Paint.Align.LEFT)
        // Legend of the strip: the mean wind is in the text colour, the gust in its own.
        var lx = left
        val items = buildList {
            add(context.getString(R.string.dc_wind) to p.text)
            if (hasGusts) add(context.getString(R.string.dc_gust) to windColor(p))
        }
        for ((name, color) in items) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(lx + dp(3.5f), top + dp(42f) - dp(4f), dp(3f), paint)
            lx += dp(11f)
            drawText(canvas, "$name km/h", lx, top + dp(42f), 11.5f, p.textSecondary, align = Paint.Align.LEFT)
            lx += paint.measureText("$name km/h") + dp(16f)
        }
    }

    private fun drawFitted(canvas: Canvas, value: String, x: Float, baseline: Float, sizeSp: Float, color: Int, maxWidth: Float, bold: Boolean = false) {
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textSize = sp(sizeSp)
        val natural = paint.measureText(value)
        val size = if (natural > maxWidth) sizeSp * maxWidth / natural else sizeSp
        drawText(canvas, value, x, baseline, size, color, bold, Paint.Align.LEFT)
    }

    /**
     * What the cursor reads out for [hour]: "Sat 18/10 · 14:00", "20° · Feels 18° · 0.2 mm · 25 %" and
     * "Wind ↙ 18 km/h · gust 36 km/h" (the gust only when known); null without data for that hour.
     */
    internal fun cursorLabel(hour: Int): Triple<String, String, String>? {
        val h = slots.getOrNull(hour) ?: return null
        val whenText = DateTimeFormatter.ofPattern("EEE dd/MM · HH:mm", Locale.getDefault()).format(h.time)
            .replace(".", "").replaceFirstChar { it.titlecase() }
        val rain = if (h.precipitation < RAIN_MIN) "0 mm" else String.format(Locale.getDefault(), "%.1f mm", h.precipitation)
        val values = listOf(
            WeatherCard.deg(h.temperature),
            context.getString(R.string.detail_felt, WeatherCard.deg(h.feltTemperature)),
            rain,
            "${h.precipitationProbability} %",
        ).joinToString(" · ")
        val wind = buildString {
            append(context.getString(R.string.dc_cursor_wind, WeatherCard.windArrow(h.windDirection), h.windSpeed.roundToInt()))
            gusts[hour]?.let { append(" · ").append(context.getString(R.string.dc_cursor_gust, it.roundToInt())) }
        }
        return Triple(whenText, values, wind)
    }

    /** For tests: puts the cursor on [hour] (or removes it with -1). */
    internal fun setCursor(hour: Int) {
        cursor = if (hour in 0..23) hour else -1
        invalidate()
    }

    private val startCursor = Runnable {
        if (!pressing) return@Runnable
        cursor = slotAt(downX)
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
                    cursor = slotAt(event.x)
                    invalidate()
                } else if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                    // A normal drag belongs to the page (and a sideways swipe to the day change).
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
        /** The hours of the strip: the middle of each three-hour block, as the Windy and Meteoblue strips. */
        internal val COLUMN_HOURS = listOf(2, 5, 8, 11, 14, 17, 20, 23)
        private const val RAIN_MIN = 0.05
        private const val READOUT_DP = 58f
    }
}
