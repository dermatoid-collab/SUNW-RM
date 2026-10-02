package it.sunw.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

/** Builds the widget's RemoteViews: today's elevation curve plus sunrise / day length / sunset. */
class WidgetRenderer(private val context: Context) {

    data class Size(val widthDp: Int, val heightDp: Int)

    fun render(size: Size, place: Place, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): RemoteViews {
        val compact = size.heightDp < COMPACT_MAX_HEIGHT_DP
        val views = RemoteViews(context.packageName, if (compact) R.layout.widget_sun_compact else R.layout.widget_sun)

        val today = now.atZone(zone).toLocalDate()
        val day = SunCalculator.day(today, place.latitude, place.longitude, zone)
        val yesterday = SunCalculator.day(today.minusDays(1), place.latitude, place.longitude, zone)

        when (day) {
            is SunCalculator.Day.Normal -> {
                views.setTextViewText(R.id.sunrise, Formatters.time(context, day.sunrise, zone))
                views.setTextViewText(R.id.sunset, Formatters.time(context, day.sunset, zone))
                views.setTextViewText(R.id.day_length, Formatters.length(day.length))
                val delta = (yesterday as? SunCalculator.Day.Normal)?.let { day.length.seconds - it.length.seconds }
                views.setTextViewText(R.id.day_delta, delta?.let(Formatters::delta) ?: "")
            }
            is SunCalculator.Day.PolarDay, is SunCalculator.Day.PolarNight -> {
                views.setTextViewText(R.id.sunrise, DASH)
                views.setTextViewText(R.id.sunset, DASH)
                views.setTextViewText(
                    R.id.day_length,
                    context.getString(if (day is SunCalculator.Day.PolarDay) R.string.polar_day else R.string.polar_night),
                )
                views.setTextViewText(R.id.day_delta, "")
            }
        }

        if (!compact) {
            val curveWidthDp = size.widthDp - 2 * PADDING_DP
            val curveHeightDp = size.heightDp - 2 * PADDING_DP - TEXT_ROW_DP
            if (curveWidthDp > 0 && curveHeightDp > 16) {
                views.setImageViewBitmap(R.id.curve, drawCurve(curveWidthDp, curveHeightDp, place, today, now, zone))
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
        private const val COMPACT_MAX_HEIGHT_DP = 100
        private const val PADDING_DP = 14
        private const val TEXT_ROW_DP = 46
        private const val MAX_BITMAP_WIDTH_PX = 900
        private const val MAX_BITMAP_HEIGHT_PX = 420
        private const val DASH = "—"
    }
}
