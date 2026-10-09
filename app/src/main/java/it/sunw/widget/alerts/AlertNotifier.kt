package it.sunw.widget.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import it.sunw.widget.LocationStore
import it.sunw.widget.MainActivity
import it.sunw.widget.Place
import it.sunw.widget.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Notifies Civil Protection alerts (yellow, orange, red) for the place in use, once per day and
 * level: the same alert seen again in a later bulletin stays quiet, a higher level notifies again.
 */
object AlertNotifier {

    private const val CHANNEL = "alerts"
    private const val PREFS = "alert_notifications"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SEEN = "seen"

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) AlertJobService.schedule(context) else AlertJobService.cancel(context)
    }

    /** Whether Android lets the app post notifications (asked at runtime from Android 13). */
    fun hasPermission(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Posts a notification for each of today and tomorrow that has a new (or higher) alert for
     * [place]'s zone. With [post] false the alerts are only marked as seen (the app is open and
     * already shows them). Returns how many notifications were posted.
     */
    fun check(context: Context, bulletin: Bulletin, place: Place = LocationStore(context).current(),
              today: LocalDate = LocalDate.now(), post: Boolean = true): Int {
        val zone = AlertUi.zone(context, place) ?: return 0
        val seen = prefs(context).getStringSet(KEY_SEEN, emptySet())!!
            .filter { runCatching { LocalDate.parse(it.substringBefore('|')) }.getOrNull()?.isBefore(today) == false }
            .toMutableSet()
        var posted = 0
        for ((index, day) in listOf(today, today.plusDays(1)).withIndex()) {
            val warnings = bulletin.forZone(zone, day)
            if (warnings.isEmpty()) continue
            val level = warnings.maxOf { it.level }
            // Seen at this level or higher (for this zone and day): nothing new to say.
            val already = Bulletin.Level.values().filter { it >= level }.any { "$day|$zone|${it.name}" in seen }
            seen += "$day|$zone|${level.name}"
            if (already || !post || !isEnabled(context) || !hasPermission(context)) continue
            notify(context, index, day == today, day, place, zone, warnings)
            posted++
        }
        prefs(context).edit().putStringSet(KEY_SEEN, seen).apply()
        return posted
    }

    private fun notify(context: Context, id: Int, isToday: Boolean, day: LocalDate, place: Place, zone: String,
                       warnings: List<Bulletin.Warning>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.alert_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.alert_channel_description) },
        )
        val placeName = place.name ?: context.getString(R.string.place_device)
        val dayLabel = if (isToday) context.getString(R.string.alert_notify_today)
        else DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(day)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_alert)
            .setColor(AlertUi.color(warnings.maxOf { it.level }))
            .setContentTitle(context.getString(R.string.alert_notify_title, AlertUi.summary(context, warnings), placeName))
            .setContentText(context.getString(R.string.alert_notify_text, dayLabel, zone))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_BASE + id, notification)
    }

    private const val NOTIFICATION_BASE = 4200

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
