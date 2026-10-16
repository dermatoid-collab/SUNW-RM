package it.sunw.widget

import android.content.Context
import java.time.Instant

/**
 * Why and when the widgets were last redrawn, kept so that Settings can show which of the triggers
 * (alarm, background job, screen on, the system's own update, the app, a clock change) really fire on
 * this phone. Each cause keeps its own last time and a count: opening the app (which redraws the widgets
 * itself) therefore does not hide what the other triggers did while the phone was idle.
 */
object WidgetRefresh {

    enum class Source(val key: String, val label: Int) {
        ALARM("alarm", R.string.wr_alarm),
        SYSTEM("system", R.string.wr_system),
        JOB("job", R.string.wr_job),
        SCREEN("screen", R.string.wr_screen),
        APP("app", R.string.wr_app),
        TIME("time", R.string.wr_time),
        RESIZE("resize", R.string.wr_resize),
    }

    class Diagnostics(val at: Instant, val source: Source?, val next: Instant?)

    /** One cause: when it last redrew the widgets and how many times since the app was installed. */
    class Entry(val source: Source, val at: Instant, val count: Int)

    private fun prefs(context: Context) = context.getSharedPreferences("widget_refresh", Context.MODE_PRIVATE)

    fun record(context: Context, source: Source, next: Instant?, now: Instant = Instant.now()) {
        val p = prefs(context)
        p.edit()
            .putLong(KEY_AT, now.toEpochMilli())
            .putString(KEY_SOURCE, source.key)
            .putLong(KEY_NEXT, next?.toEpochMilli() ?: 0L)
            .putLong(KEY_AT + source.key, now.toEpochMilli())
            .putInt(KEY_COUNT + source.key, p.getInt(KEY_COUNT + source.key, 0) + 1)
            .apply()
    }

    /** The causes that have redrawn the widgets, most recent first. */
    fun entries(context: Context): List<Entry> {
        val p = prefs(context)
        return Source.values().mapNotNull { source ->
            val at = p.getLong(KEY_AT + source.key, 0L)
            if (at == 0L) null else Entry(source, Instant.ofEpochMilli(at), p.getInt(KEY_COUNT + source.key, 0))
        }.sortedByDescending { it.at }
    }

    /** The last redraw, or null when none has been recorded yet. */
    fun diagnostics(context: Context): Diagnostics? {
        val p = prefs(context)
        val at = p.getLong(KEY_AT, 0L)
        if (at == 0L) return null
        val next = p.getLong(KEY_NEXT, 0L)
        return Diagnostics(
            Instant.ofEpochMilli(at),
            Source.values().firstOrNull { it.key == p.getString(KEY_SOURCE, null) },
            if (next == 0L) null else Instant.ofEpochMilli(next),
        )
    }

    /** Screen on or unlock: redraws unless the widgets were redrawn in the last minute. */
    fun onScreen(context: Context, now: Instant = Instant.now()) {
        val last = diagnostics(context)
        if (last != null && now.toEpochMilli() - last.at.toEpochMilli() < SCREEN_DEBOUNCE_MS) return
        SunWidgetProvider.updateAll(context, Source.SCREEN)
    }

    private const val KEY_AT = "at"
    private const val KEY_COUNT = "count_"
    private const val KEY_SOURCE = "source"
    private const val KEY_NEXT = "next"
    private const val SCREEN_DEBOUNCE_MS = 60_000L
}
