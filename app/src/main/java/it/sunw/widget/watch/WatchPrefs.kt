package it.sunw.widget.watch

import android.content.Context

/** What the user switched on: the weather watch as a whole and each of its events (all on at first). */
object WatchPrefs {
    private const val PREFS = "weather_watch_prefs"
    private const val KEY_ENABLED = "enabled"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isOn(context: Context, type: EventType) = prefs(context).getBoolean("on_" + type.key, true)

    fun setOn(context: Context, type: EventType, on: Boolean) {
        prefs(context).edit().putBoolean("on_" + type.key, on).apply()
    }

    /** The event types to watch: none when the watch is off. */
    fun enabledTypes(context: Context): Set<EventType> =
        if (!isEnabled(context)) emptySet() else EventType.values().filter { isOn(context, it) }.toSet()
}
