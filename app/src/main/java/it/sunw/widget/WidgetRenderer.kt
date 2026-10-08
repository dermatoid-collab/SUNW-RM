package it.sunw.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Builds the widget's RemoteViews, picking the layout from the widget size:
 * full (curve + times), compact (times only, 1 row high) or tiny (1×1: curve + both times).
 * Colours come from the user's [Palette]; the app's main page shows the same RemoteViews.
 */
class WidgetRenderer(
    private val context: Context,
    private val palette: Palette = AppearanceStore(context).palette(),
) {

    data class Size(val widthDp: Int, val heightDp: Int)

    /** The two 1×1 looks. Fractions must match the layout weights. */
    enum class TinyStyle(val layout: Int, val paddingDp: Int, val curveFraction: Double) {
        /** Big condensed times, squarer corners, thin curve. */
        BIG(R.layout.widget_sun_tiny, paddingDp = 5, curveFraction = 0.26),

        /** Taller curve, system-rounded corners. */
        CURVE(R.layout.widget_sun_tiny_curve, paddingDp = 6, curveFraction = 0.34),
    }

    fun render(
        size: Size,
        place: Place,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        tinyStyle: TinyStyle = TinyStyle.BIG,
        clickable: Boolean = true,
    ): RemoteViews {
        val today = now.atZone(zone).toLocalDate()
        val day = SunCalculator.day(today, place.latitude, place.longitude, zone)
        val views = when {
            size.widthDp < TINY_MAX_WIDTH_DP -> renderTiny(size, place, day, now, zone, tinyStyle)
            size.heightDp < COMPACT_MAX_HEIGHT_DP -> RemoteViews(context.packageName, R.layout.widget_sun_compact)
                .also { fillRow(it, day, place, zone) }
            else -> RemoteViews(context.packageName, R.layout.widget_sun).also {
                fillRow(it, day, place, zone)
                setCurve(it, size.widthDp - 2 * PADDING_DP, size.heightDp - 2 * PADDING_DP - TEXT_ROW_DP, place, today, now, zone)
            }
        }
        val square = size.widthDp < TINY_MAX_WIDTH_DP && tinyStyle == TinyStyle.BIG
        views.setInt(R.id.root, "setBackgroundResource", if (square) palette.theme.backgroundSquare else palette.theme.background)
        views.setInt(R.id.sunrise_icon, "setColorFilter", palette.accent)
        views.setInt(R.id.sunset_icon, "setColorFilter", palette.accent)

        if (clickable) {
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.root, open)
        }
        return views
    }

    /** Sunrise · day length (+ change vs. yesterday) · sunset. */
    private fun fillRow(views: RemoteViews, day: SunCalculator.Day, place: Place, zone: ZoneId) {
        when (day) {
            is SunCalculator.Day.Normal -> {
                views.setTextViewText(R.id.sunrise, Formatters.time(context, day.sunrise, zone))
                views.setTextViewText(R.id.sunset, Formatters.time(context, day.sunset, zone))
                views.setTextViewText(R.id.day_length, Formatters.length(day.length))
                val yesterday = SunCalculator.day(day.date.minusDays(1), place.latitude, place.longitude, zone)
                val delta = (yesterday as? SunCalculator.Day.Normal)?.let { day.length.seconds - it.length.seconds }
                views.setTextViewText(R.id.day_delta, delta?.let(Formatters::delta) ?: "")
            }
            is SunCalculator.Day.PolarDay, is SunCalculator.Day.PolarNight -> {
                views.setTextViewText(R.id.sunrise, DASH)
                views.setTextViewText(R.id.sunset, DASH)
                views.setTextViewText(R.id.day_length, context.getString(polarLabel(day, short = false)))
                views.setTextViewText(R.id.day_delta, "")
            }
        }
        views.setTextColor(R.id.sunrise, palette.text)
        views.setTextColor(R.id.sunset, palette.text)
        views.setTextColor(R.id.day_length, palette.textSecondary)
        views.setTextColor(R.id.day_delta, palette.textSecondary)
    }

    /**
     * 1×1: mini curve with sunrise and sunset stacked below. After today's sunset it
     * switches to tomorrow's times; the next event is in full colour, the other dimmed.
     */
    private fun renderTiny(size: Size, place: Place, day: SunCalculator.Day, now: Instant, zone: ZoneId, style: TinyStyle): RemoteViews {
        val views = RemoteViews(context.packageName, style.layout)
        val shown = if (day is SunCalculator.Day.Normal && !now.isBefore(day.sunset)) {
            SunCalculator.day(day.date.plusDays(1), place.latitude, place.longitude, zone)
        } else {
            day
        }
        if (shown is SunCalculator.Day.Normal) {
            val sunriseNext = now.isBefore(shown.sunrise)
            val bright = palette.text
            val dim = palette.textSecondary
            views.setTextViewText(R.id.sunrise, Formatters.time(context, shown.sunrise, zone))
            views.setTextViewText(R.id.sunset, Formatters.time(context, shown.sunset, zone))
            views.setTextColor(R.id.sunrise, if (sunriseNext) bright else dim)
            views.setTextColor(R.id.sunset, if (sunriseNext) dim else bright)
            views.setViewVisibility(R.id.sunrise_icon, View.VISIBLE)
            views.setViewVisibility(R.id.sunset_row, View.VISIBLE)
        } else {
            views.setTextViewText(R.id.sunrise, context.getString(polarLabel(shown, short = true)))
            views.setTextColor(R.id.sunrise, palette.text)
            views.setViewVisibility(R.id.sunrise_icon, View.GONE)
            views.setViewVisibility(R.id.sunset_row, View.GONE)
        }
        val innerHeight = size.heightDp - 2 * style.paddingDp
        val curveHeight = if (shown is SunCalculator.Day.Normal) (innerHeight * style.curveFraction).toInt() else innerHeight / 2
        setCurve(views, size.widthDp - 2 * style.paddingDp, curveHeight, place, day.date, now, zone)
        return views
    }

    private fun polarLabel(day: SunCalculator.Day, short: Boolean) = when {
        day is SunCalculator.Day.PolarDay -> if (short) R.string.polar_day_short else R.string.polar_day
        else -> if (short) R.string.polar_night_short else R.string.polar_night
    }

    private fun setCurve(views: RemoteViews, widthDp: Int, heightDp: Int, place: Place, date: LocalDate, now: Instant, zone: ZoneId) {
        if (widthDp > 0 && heightDp > MIN_CURVE_HEIGHT_DP) {
            views.setViewVisibility(R.id.curve, View.VISIBLE)
            views.setImageViewBitmap(R.id.curve, drawCurve(widthDp, heightDp, place, date, now, zone))
        } else {
            // Too small for a readable curve: hide it rather than show the static placeholder.
            views.setViewVisibility(R.id.curve, View.GONE)
        }
    }

    /**
     * Solar elevation over the local day (midnight → midnight), coloured by [Palette.curveColor]
     * segment by segment; a dot marks "now". The twilight style adds shaded bands below the horizon.
     */
    fun drawCurve(widthDp: Int, heightDp: Int, place: Place, date: LocalDate, now: Instant, zone: ZoneId): Bitmap {
        val density = context.resources.displayMetrics.density
        // Keep the bitmap small: RemoteViews bitmaps count against a memory budget.
        val scale = minOf(density, MAX_BITMAP_WIDTH_PX.toFloat() / widthDp, MAX_BITMAP_HEIGHT_PX.toFloat() / heightDp)
        val w = (widthDp * scale).roundToInt()
        val h = (heightDp * scale).roundToInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val span = Duration.between(start, end).seconds.toDouble()
        val elevations = DoubleArray(SAMPLES + 1) { i ->
            SunCalculator.elevation(start.plusSeconds((span * i / SAMPLES).toLong()), place.latitude, place.longitude)
        }

        val stroke = 2f * scale
        val dotRadius = 4.5f * scale
        val inset = dotRadius + stroke
        // Symmetric vertical range around the horizon keeps the horizon line centred-ish and the shape honest.
        val range = max(max(elevations.max(), -elevations.min()), 10.0)
        fun x(fraction: Double) = (inset + fraction * (w - 2 * inset)).toFloat()
        fun y(elevation: Double) = (h / 2.0 - elevation / range * (h / 2.0 - inset)).toFloat()
        val horizonY = y(SunCalculator.HORIZON_DEG)

        if (palette.curveStyle == CurveStyle.TWILIGHT) {
            val band = Paint().apply { color = palette.twilightBand }
            for ((top, bottom, alpha) in TWILIGHT_BANDS) {
                band.alpha = alpha
                canvas.drawRect(0f, y(top), w.toFloat(), y(bottom), band)
            }
        }

        val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.dim; alpha = 110; strokeWidth = 1f * scale
        }
        canvas.drawLine(0f, horizonY, w.toFloat(), horizonY, horizonPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until SAMPLES) {
            val mid = (elevations[i] + elevations[i + 1]) / 2
            linePaint.color = palette.curveColor(mid)
            linePaint.alpha = if (palette.isDimmed(mid)) 120 else 255
            canvas.drawLine(x(i.toDouble() / SAMPLES), y(elevations[i]), x((i + 1.0) / SAMPLES), y(elevations[i + 1]), linePaint)
        }

        val nowFraction = (Duration.between(start, now).seconds / span).coerceIn(0.0, 1.0)
        val nowElevation = SunCalculator.elevation(now, place.latitude, place.longitude)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.curveColor(nowElevation) }
        val cx = x(nowFraction)
        val cy = y(nowElevation)
        canvas.drawCircle(cx, cy, dotRadius * 1.9f, Paint(dotPaint).apply { alpha = 60 })
        canvas.drawCircle(cx, cy, dotRadius, dotPaint)
        // Night colours (e.g. the "Sky" style below −12°) vanish on a dark background: add a light ring.
        if (Color.luminance(dotPaint.color) < 0.12f) {
            canvas.drawCircle(cx, cy, dotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.2f * scale; color = palette.textSecondary
            })
        }
        return bitmap
    }

    companion object {
        private const val SAMPLES = 144 // every 10 minutes
        private const val TINY_MAX_WIDTH_DP = 110
        private const val COMPACT_MAX_HEIGHT_DP = 100
        private const val MIN_CURVE_HEIGHT_DP = 12
        private const val PADDING_DP = 14
        private const val TEXT_ROW_DP = 56
        private const val MAX_BITMAP_WIDTH_PX = 900
        private const val MAX_BITMAP_HEIGHT_PX = 420
        private const val DASH = "—"

        /** (top°, bottom°, alpha): civil, nautical, astronomical twilight. */
        private val TWILIGHT_BANDS = listOf(
            Triple(SunCalculator.HORIZON_DEG, -6.0, 80),
            Triple(-6.0, -12.0, 52),
            Triple(-12.0, -18.0, 28),
        )
    }
}
