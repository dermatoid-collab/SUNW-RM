package it.sunw.widget.watch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The recurring weather summaries: a list of times of the day (04:00, 12:00 and 20:00 at first) at which a notification
 * tells the weather of the next hours, every day. One alarm is kept for the next time of the list; it sets the one
 * after when it fires, and is set again after a reboot or a change of clock or time zone.
 */
object DigestScheduler {
    const val ACTION = "it.sunw.widget.DIGEST"
    private const val PREFS = "weather_digest"
    private const val KEY_TIMES = "times"
    private const val REQUEST = 4301

    val DEFAULTS: List<LocalTime> = listOf(LocalTime.of(4, 0), LocalTime.of(12, 0), LocalTime.of(20, 0))

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The times of the day, sorted; the defaults until the user changes the list, empty when all were removed. */
    fun times(context: Context): List<LocalTime> {
        val raw = prefs(context).getString(KEY_TIMES, null) ?: return DEFAULTS
        return raw.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }.distinct().sorted()
    }

    private fun save(context: Context, list: List<LocalTime>) {
        prefs(context).edit().putString(KEY_TIMES, list.distinct().sorted().joinToString(",") { it.toString() }).apply()
        scheduleNext(context)
    }

    /** False when [time] is already in the list. */
    fun add(context: Context, time: LocalTime): Boolean {
        val t = time.withSecond(0).withNano(0)
        val current = times(context)
        if (t in current) return false
        save(context, current + t)
        return true
    }

    fun remove(context: Context, time: LocalTime) = save(context, times(context) - time)

    /** The first of [times] after [now]. */
    fun nextTrigger(times: List<LocalTime>, now: ZonedDateTime): ZonedDateTime? = times.map { t ->
        val today = now.toLocalDate().atTime(t).atZone(now.zone)
        if (today.isAfter(now.plusSeconds(1))) today else now.toLocalDate().plusDays(1).atTime(t).atZone(now.zone)
    }.minOrNull()

    /** Sets the alarm for the next time of the list (or cancels it when the list is empty). */
    fun scheduleNext(context: Context, now: Instant = Instant.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, DigestReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = nextTrigger(times(context), now.atZone(ZoneId.systemDefault()))
        if (next == null) {
            alarms.cancel(pending)
            return
        }
        val at = next.toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
}
