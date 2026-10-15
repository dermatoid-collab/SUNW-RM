package it.sunw.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Widget picker entry "4×2". [SunWidgetProviderSmall] and [SunWidgetProviderSmallCurve] are the two
 * 1×1 entries; all of them pick their layout from the actual size.
 */
open class SunWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids, WidgetRefresh.Source.SYSTEM)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateWidgets(context, manager, intArrayOf(id), WidgetRefresh.Source.RESIZE)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshJobService.schedule(context)
    }

    override fun onDisabled(context: Context) {
        if (allWidgetIds(context).isEmpty()) {
            context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
            WidgetRefreshJobService.cancel(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> updateAll(context, WidgetRefresh.Source.ALARM)
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context, WidgetRefresh.Source.TIME)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        internal const val ACTION_TICK = "it.sunw.widget.TICK"
        private val PROVIDERS = listOf(
            SunWidgetProvider::class.java,
            SunWidgetProviderSmall::class.java,
            SunWidgetProviderSmallCurve::class.java,
        )

        fun updateAll(context: Context, source: WidgetRefresh.Source = WidgetRefresh.Source.APP) {
            val ids = allWidgetIds(context)
            if (ids.isNotEmpty()) updateWidgets(context, AppWidgetManager.getInstance(context), ids, source)
        }

        private fun allWidgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return PROVIDERS.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
                .toIntArray()
        }

        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray, source: WidgetRefresh.Source) {
            val place = LocationStore(context).current()
            val renderer = WidgetRenderer(context)
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            for (id in ids) {
                val provider = manager.getAppWidgetInfo(id)?.provider?.className
                val small = provider != SunWidgetProvider::class.java.name
                val style = if (provider == SunWidgetProviderSmallCurve::class.java.name) {
                    WidgetRenderer.TinyStyle.CURVE
                } else {
                    WidgetRenderer.TinyStyle.BIG
                }
                val size = sizeOf(manager.getAppWidgetOptions(id), small)
                manager.updateAppWidget(id, renderer.render(size, place, now, zone, style))
            }
            val next = scheduleNextTick(context, place, now, zone)
            WidgetRefreshJobService.schedule(context) // the second trigger, in case alarms are held back
            WidgetRefresh.record(context, source, next, now)
        }

        /** Portrait size of the widget in dp (min width × max height, as launchers report it). */
        private fun sizeOf(options: Bundle, small: Boolean): WidgetRenderer.Size {
            // Defaults for launchers that don't report sizes: a typical 1×1 or 4×2 cell area.
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: if (small) 72 else 250
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: if (small) 80 else 110
            return WidgetRenderer.Size(width, height)
        }

        /**
         * Three independent triggers keep the widgets current (any of them may be held back by battery
         * saving): the system's own update every 30 min, a 15-minute background job, and this alarm. The
         * alarm wakes the device at the next sunrise, sunset or midnight, exactly when the app may set
         * exact alarms, and otherwise at most [HEARTBEAT] later, so the Sun dot keeps moving too.
         * Every redraw sets the next one. Returns when it will fire.
         */
        private fun scheduleNextTick(context: Context, place: Place, now: Instant, zone: ZoneId): Instant {
            val today = now.atZone(zone).toLocalDate()
            val candidates = mutableListOf(today.plusDays(1).atStartOfDay(zone).toInstant())
            val day = SunCalculator.day(today, place.latitude, place.longitude, zone)
            if (day is SunCalculator.Day.Normal) candidates += listOf(day.sunrise, day.sunset)
            val event = candidates.filter { it.isAfter(now) }.min().plusSeconds(30)
            val heartbeat = now.plus(HEARTBEAT)
            val onTime = !event.isAfter(heartbeat)
            val next = if (onTime) event else heartbeat
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return next
            val pending = tickIntent(context)
            val exact = onTime && (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())
            try {
                if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending)
            } catch (e: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pending)
            }
            return next
        }

        private val HEARTBEAT: Duration = Duration.ofMinutes(15)

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, SunWidgetProvider::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
