package it.sunw.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
 * full (curve + times), compact (times only, 1 row high) or tiny (1×1: curve + next event).
 */
class WidgetRenderer(private val context: Context) {

    data class Size(val widthDp: Int, val heightDp: Int)

    fun render(size: Size, place: Place, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): RemoteViews {
        val today = now.atZone(zone).toLocalDate()
        val day = SunCalculator.day(today, place.latitude, place.longitude, zone)
        val views = when {
            size.widthDp < TINY_MAX_WIDTH_DP -> renderTiny(size, place, day, now, zone)
            size.heightDp < COMPACT_MAX_HEIGHT_DP -> RemoteViews(context.packageName, R.layout.widget_sun_compact)
                .also { fillRow(it, day, place, zone) }
            else -> RemoteViews(context.packageName, R.layout.widget_sun).also {
                fillRow(it, day, place, zone)
                setCurve(it, size.widthDp - 2 * PADDING_DP, size.heightDp - 2 * PADDING_DP - TEXT_ROW_DP, place, today, now, zone)
            }
        }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.root, open)
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
    }

    /** 1×1: mini curve plus only the next event (sunrise, then sunset, then tomorrow's sunrise). */
    private fun renderTiny(size: Size, place: Place, day: SunCalculator.Day, now: Instant, zone: ZoneId): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_sun_tiny)
        val next: Pair<Instant, Boolean>? = when (day) {
            is SunCalculator.Day.Normal -> when {
                now.isBefore(day.sunrise) -> day.sunrise to true
                now.isBefore(day.sunset) -> day.sunset to false
                else -> (SunCalculator.day(day.date.plusDays(1), place.latitude, place.longitude, zone)
                    as? SunCalculator.Day.Normal)?.let { it.sunrise to true }
            }
            else -> null
        }
        if (next != null) {
            views.setTextViewText(R.id.next_event, Formatters.time(context, next.first, zone))
            views.setTextViewCompoundDrawablesRelative(
                R.id.next_event, if (next.second) R.drawable.ic_sunrise else R.drawable.ic_sunset, 0, 0, 0,
            )
        } else {
            views.setTextViewText(R.id.next_event, if (day is SunCalculator.Day.Normal) DASH else context.getString(polarLabel(day, short = true)))
            views.setTextViewCompoundDrawablesRelative(R.id.next_event, 0, 0, 0, 0)
        }
        setCurve(views, size.widthDp - 2 * TINY_PADDING_DP, size.heightDp - 2 * TINY_PADDING_DP - TINY_TEXT_ROW_DP, place, day.date, now, zone)
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
     * Solar elevation over the local day (midnight → midnight). The part above the
     * horizon is drawn in the accent colour, the rest dimmed; a dot marks "now".
     */
    private fun drawCurve(widthDp: Int, heightDp: Int, place: Place, date: LocalDate, now: Instant, zone: ZoneId): Bitmap {
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

        val accent = context.getColor(R.color.widget_accent)
        val dim = context.getColor(R.color.widget_text_secondary)

        val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dim; alpha = 90; strokeWidth = 1f * scale
        }
        canvas.drawLine(0f, horizonY, w.toFloat(), horizonY, horizonPaint)

        val path = Path()
        elevations.forEachIndexed { i, el ->
            val px = x(i.toDouble() / SAMPLES)
            if (i == 0) path.moveTo(px, y(el)) else path.lineTo(px, y(el))
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        // Below the horizon: dimmed.
        canvas.save()
        canvas.clipRect(0f, horizonY, w.toFloat(), h.toFloat())
        canvas.drawPath(path, linePaint.apply { color = dim; alpha = 110 })
        canvas.restore()
        // Above the horizon: accent.
        canvas.save()
        canvas.clipRect(0f, 0f, w.toFloat(), horizonY)
        canvas.drawPath(path, linePaint.apply { color = accent; alpha = 255 })
        canvas.restore()

        val nowFraction = (Duration.between(start, now).seconds / span).coerceIn(0.0, 1.0)
        val nowElevation = SunCalculator.elevation(now, place.latitude, place.longitude)
        val up = nowElevation > SunCalculator.HORIZON_DEG
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (up) accent else dim }
        val cx = x(nowFraction)
        val cy = y(nowElevation)
        canvas.drawCircle(cx, cy, dotRadius * 1.9f, Paint(dotPaint).apply { alpha = 60 })
        canvas.drawCircle(cx, cy, dotRadius, dotPaint)
        return bitmap
    }

    companion object {
        private const val SAMPLES = 144 // every 10 minutes
        private const val TINY_MAX_WIDTH_DP = 110
        private const val COMPACT_MAX_HEIGHT_DP = 100
        private const val TINY_PADDING_DP = 8
        private const val TINY_TEXT_ROW_DP = 22
        private const val MIN_CURVE_HEIGHT_DP = 16
        private const val PADDING_DP = 14
        private const val TEXT_ROW_DP = 46
        private const val MAX_BITMAP_WIDTH_PX = 900
        private const val MAX_BITMAP_HEIGHT_PX = 420
        private const val DASH = "—"
    }
}
