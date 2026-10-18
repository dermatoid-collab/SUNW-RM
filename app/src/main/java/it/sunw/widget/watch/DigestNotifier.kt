package it.sunw.widget.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import it.sunw.widget.LocationStore
import it.sunw.widget.R
import it.sunw.widget.alerts.AlertNotifier
import it.sunw.widget.weather.Condition
import it.sunw.widget.weather.WeatherDayActivity
import it.sunw.widget.weather.WeatherIcons
import it.sunw.widget.weather.WeatherRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** The notification of a recurring summary: the weather of the next [HOURS] hours for the place in use. */
object DigestNotifier {
    const val CHANNEL = "weather_digest"
    const val HOURS = 12
    private const val BASE_ID = 4500
    private const val GUSTS_WORTH_MENTIONING = 40.0

    /** One hour of the summary, from Open-Meteo or (when it cannot be reached) from the last MeteoBlue forecast. */
    class Slot(val time: LocalDateTime, val temperature: Double, val precipitation: Double, val gust: Double?, val condition: Condition)

    /** Blocking (network): call from a background thread. Posts nothing without notification permission or any data. */
    fun post(context: Context, now: Instant = Instant.now()) {
        if (!AlertNotifier.hasPermission(context)) return
        val place = LocationStore(context).current()
        val (local, slots) = runCatching { fromOpenMeteo(place, now) }.getOrNull() ?: fromCache(context, place, now) ?: return
        if (slots.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.digest_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.digest_channel_description) },
        )
        val worst = worstCondition(slots)
        val text = summary(context, slots)
        val night = local.hour !in 7..19
        val open = PendingIntent.getActivity(
            context, BASE_ID,
            Intent(context, WeatherDayActivity::class.java)
                .putExtra(WeatherDayActivity.EXTRA_DATE, local.toLocalDate().toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(WeatherIcons.icon(worst, night))
            .setContentTitle(context.getString(R.string.digest_title, HOURS, place.name ?: context.getString(R.string.place_device)))
            .setSubText(PlaceLabels.subText(context, place))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(BASE_ID + (local.hour * 60 + local.minute) % 1440, notification)
    }

    private fun fromOpenMeteo(place: it.sunw.widget.Place, now: Instant): Pair<LocalDateTime, List<Slot>> {
        val data = WatchData.fetch(place)
        val local = data.localNow(now)
        val from = local.truncatedTo(ChronoUnit.HOURS)
        val slots = data.hours.filter { it.time.isAfter(from) && !it.time.isAfter(from.plusHours(HOURS.toLong())) }.mapNotNull { h ->
            val t = h.temperature ?: return@mapNotNull null
            Slot(h.time, t, h.precipitation ?: 0.0, h.gust, h.code?.let { Condition.fromWmoCode(it) } ?: Condition.CLEAR)
        }
        return local to slots
    }

    private fun fromCache(context: Context, place: it.sunw.widget.Place, now: Instant): Pair<LocalDateTime, List<Slot>>? {
        val forecast = WeatherRepository(context).cached(place) ?: return null
        val local = forecast.localTime(now)
        val from = local.truncatedTo(ChronoUnit.HOURS)
        val slots = forecast.hours.filter { it.time.isAfter(from) && !it.time.isAfter(from.plusHours(HOURS.toLong())) }
            .map { Slot(it.time, it.temperature, it.precipitation, null, it.condition) }
        return local to slots
    }

    private fun worstCondition(slots: List<Slot>): Condition = slots.maxBy { it.condition.severity }.condition

    /** "Light rain · 14–18 °C · rain from 15:00 (2.4 mm) · gusts up to 52 km/h". */
    internal fun summary(context: Context, slots: List<Slot>): String {
        val parts = ArrayList<String>()
        parts += context.getString(worstCondition(slots).label)
        parts += context.getString(R.string.digest_temp, slots.minOf { it.temperature }.roundToInt(), slots.maxOf { it.temperature }.roundToInt())
        val wet = slots.filter { it.precipitation >= 0.1 }
        val total = slots.sumOf { it.precipitation }
        if (wet.isNotEmpty() && total >= 0.2) {
            parts += context.getString(
                R.string.digest_rain_from, DateTimeFormatter.ofPattern("HH:mm").format(wet.first().time),
                String.format(Locale.getDefault(), "%.1f", total),
            )
        }
        val gust = slots.mapNotNull { it.gust }.maxOrNull()
        if (gust != null && gust >= GUSTS_WORTH_MENTIONING) parts += context.getString(R.string.digest_gust, gust.roundToInt())
        return parts.joinToString(" · ")
    }
}
