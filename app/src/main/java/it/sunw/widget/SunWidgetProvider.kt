package it.sunw.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.time.Instant
import java.time.ZoneId

/** Widget picker entry "4×2"; [SunWidgetProviderSmall] is the 1×1 entry. Both render by size. */
open class SunWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateWidgets(context, manager, intArrayOf(id))
    }

    override fun onDisabled(context: Context) {
        if (allWidgetIds(context).isEmpty()) {
            context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_TICK = "it.sunw.widget.TICK"

        fun updateAll(context: Context) {
            val ids = allWidgetIds(context)
            if (ids.isNotEmpty()) updateWidgets(context, AppWidgetManager.getInstance(context), ids)
        }

        private fun allWidgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return listOf(SunWidgetProvider::class.java, SunWidgetProviderSmall::class.java)
                .flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
                .toIntArray()
        }

        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val place = LocationStore(context).current()
            val renderer = WidgetRenderer(context)
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            for (id in ids) {
                val small = manager.getAppWidgetInfo(id)?.provider?.className == SunWidgetProviderSmall::class.java.name
                manager.updateAppWidget(id, renderer.render(sizeOf(manager.getAppWidgetOptions(id), small), place, now, zone))
            }
            scheduleNextTick(context, place, now, zone)
        }

        /** Portrait size of the widget in dp (min width × max height, as launchers report it). */
        private fun sizeOf(options: Bundle, small: Boolean): WidgetRenderer.Size {
            // Defaults for launchers that don't report sizes: a typical 1×1 or 4×2 cell area.
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: if (small) 72 else 250
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: if (small) 80 else 110
            return WidgetRenderer.Size(width, height)
        }

        /**
         * The system refreshes us every 30 min (updatePeriodMillis); on top of that, wake up
         * right after the next sunrise, sunset or midnight so the text flips on time.
         * Inexact alarms need no special permission and don't wake the device.
         */
        private fun scheduleNextTick(context: Context, place: Place, now: Instant, zone: ZoneId) {
            val today = now.atZone(zone).toLocalDate()
            val candidates = mutableListOf(today.plusDays(1).atStartOfDay(zone).toInstant())
            val day = SunCalculator.day(today, place.latitude, place.longitude, zone)
            if (day is SunCalculator.Day.Normal) candidates += listOf(day.sunrise, day.sunset)
            val next = candidates.filter { it.isAfter(now) }.min().plusSeconds(30)
            context.getSystemService(AlarmManager::class.java)
                ?.set(AlarmManager.RTC, next.toEpochMilli(), tickIntent(context))
        }

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, SunWidgetProvider::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
