package it.sunw.widget.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import it.sunw.widget.Place
import it.sunw.widget.R
import it.sunw.widget.weather.WeatherDayActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Posts the notifications of the weather watch, with the app's weather icons. */
object WeatherNotifier {
    const val CHANNEL = "weather_watch"
    private const val GROUP = "weather_watch"
    private const val BASE_ID = 4400

    fun post(context: Context, alert: EventTracker.Alert, place: Place, local: LocalDateTime) {
        val e = alert.event
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.watch_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.watch_channel_description) },
        )
        val title = context.getString(e.type.title).let { if (alert.escalation) context.getString(R.string.we_escalation, it) else it }
        val text = context.getString(R.string.we_text, whenText(context, e, local), peakText(context, e.type, e.peak))
        val key = EventTracker.placeKey(place.latitude, place.longitude) + "|" + e.id // the same event at two places is two notifications
        val open = PendingIntent.getActivity(
            context, key.hashCode(),
            Intent(context, WeatherDayActivity::class.java)
                .putExtra(WeatherDayActivity.EXTRA_DATE, e.start.toLocalDate().toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(e.type.icon)
            .setColor(if (e.level >= 2) STRONG else NORMAL)
            .setContentTitle(title)
            .setSubText(PlaceLabels.subText(context, place)) // which place: the one set, or the device's position
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setGroup(GROUP)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(BASE_ID + (key.hashCode() and 0xFFF), notification)
    }

    /** "from 15:00 to 18:00" (with the weekday when it is not today), or "until 18:00" when it is under way. */
    internal fun whenText(context: Context, e: EventTracker.Event, local: LocalDateTime): String {
        fun time(t: LocalDateTime) = if (t.toLocalDate() == local.toLocalDate()) DateTimeFormatter.ofPattern("HH:mm").format(t)
        else DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault()).format(t).replace(".", "").replaceFirstChar { it.titlecase() }
        return if (!e.start.isAfter(local)) context.getString(R.string.we_when_now, time(e.end))
        else context.getString(R.string.we_when_range, time(e.start), time(e.end))
    }

    internal fun peakText(context: Context, type: EventType, peak: Double): String = when (type) {
        EventType.RAIN_SOON, EventType.RAIN_HEAVY -> context.getString(R.string.we_peak_rain, String.format(Locale.getDefault(), "%.1f", peak))
        EventType.STORM -> context.getString(R.string.we_peak_prob, peak.roundToInt())
        EventType.HAIL -> context.getString(R.string.we_peak_hail)
        EventType.SNOW -> context.getString(R.string.we_peak_snow, String.format(Locale.getDefault(), "%.1f", peak))
        EventType.WIND -> context.getString(R.string.we_peak_gust, peak.roundToInt())
        EventType.HEAT, EventType.COLD, EventType.ICE -> context.getString(R.string.we_peak_temp, peak.roundToInt())
        EventType.FOG -> context.getString(R.string.we_peak_vis, peak.roundToInt())
        EventType.CHANGE -> context.getString(R.string.we_peak_change, peak.roundToInt())
    }

    private const val NORMAL = 0xFFFFB547.toInt()
    private const val STRONG = 0xFFE0624A.toInt()
}
