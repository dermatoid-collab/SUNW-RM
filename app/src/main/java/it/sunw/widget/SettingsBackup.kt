package it.sunw.widget

import android.content.Context
import it.sunw.widget.alerts.AlertNotifier
import it.sunw.widget.watch.DigestScheduler
import it.sunw.widget.watch.EventType
import it.sunw.widget.watch.Thresholds
import it.sunw.widget.watch.WatchJobService
import it.sunw.widget.watch.WatchPrefs
import it.sunw.widget.weather.WeatherDayActivity
import it.sunw.widget.webcams.OwnWebcamStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/**
 * All user settings as one JSON document: place, favourites and recent searches, appearance (themes included), the
 * Civil Protection alerts switch, the weather notifications (switches, thresholds, hysteresis parameters and the
 * recurring summary times), the every-hour / every-3-hours choice of the day pages and own webcams. Used by
 * "Export / Import settings" so they survive a reinstall or a new phone. A file of an older version still imports:
 * what it lacks keeps its current value.
 */
object SettingsBackup {

    private const val VERSION = 2

    fun export(context: Context): String {
        val place = LocationStore(context).current()
        val appearance = AppearanceStore(context)
        val favorites = JSONArray()
        FavoritesStore(context).all().forEach {
            favorites.put(JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude))
        }
        return JSONObject()
            .put("app", "it.sunw.widget")
            .put("version", VERSION)
            .put("place", JSONObject()
                .put("automatic", place.automatic)
                .put("lat", place.latitude)
                .put("lon", place.longitude)
                .put("name", place.name ?: JSONObject.NULL))
            .put("favorites", favorites)
            .put("appearance", JSONObject()
                .put("curve", appearance.curveStyle.key)
                .put("accent", appearance.accent.key)
                .put("theme", appearance.theme.key))
            .put("alerts", JSONObject().put("notify", AlertNotifier.isEnabled(context)))
            .put("watch", watchToJson(context))
            .put("digest", JSONArray(DigestScheduler.times(context).map { it.toString() }))
            .put("weatherDay", JSONObject().put("stepHours", WeatherDayActivity.stepHours(context)))
            .put("recents", JSONArray().also { list ->
                RecentPlaces(context).all().forEach { list.put(JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude)) }
            })
            .put("webcams", OwnWebcamStore(context).toJson())
            .toString(2)
    }

    /** The master switch, one switch per event, and every threshold and general parameter as set now. */
    private fun watchToJson(context: Context): JSONObject {
        val thresholds = Thresholds.load(context)
        val events = JSONObject()
        val values = JSONObject()
        for (type in EventType.values()) {
            events.put(type.key, WatchPrefs.isOn(context, type))
            val v = JSONObject()
            thresholds.valuesOf(type).forEach { (k, x) -> v.put(k, x) }
            values.put(type.key, v)
        }
        val general = JSONObject()
        thresholds.generalValues().forEach { (k, x) -> general.put(k, x) }
        return JSONObject().put("enabled", WatchPrefs.isEnabled(context)).put("events", events).put("thresholds", values).put("general", general)
    }

    private fun restoreWatch(context: Context, watch: JSONObject) {
        if (watch.has("enabled")) WatchPrefs.setEnabled(context, watch.optBoolean("enabled", true))
        val events = watch.optJSONObject("events")
        val values = watch.optJSONObject("thresholds")
        val current = Thresholds.load(context)
        for (type in EventType.values()) {
            events?.takeIf { it.has(type.key) }?.let { WatchPrefs.setOn(context, type, it.optBoolean(type.key, true)) }
            val v = values?.optJSONObject(type.key) ?: continue
            // What the file does not have keeps its current value; a set that is out of range or out of order is left out.
            val merged = type.params.associate { it.key to v.optDouble(it.key, current.value(type, it.key)) }
            if (type.invalid(merged) == null) Thresholds.save(context, type, merged)
        }
        watch.optJSONObject("general")?.let { g ->
            val merged = Thresholds.GENERAL.associate { it.key to g.optDouble(it.key, current.generalValues().getValue(it.key)) }
            if (Thresholds.GENERAL.all { merged.getValue(it.key) in it.min..it.max }) Thresholds.saveGeneral(context, merged)
        }
        WatchJobService.ensure(context) // schedules or cancels the 30-minute check as the restored switch says
    }

    /** Restores what [json] contains; unknown or missing fields keep their current value. */
    fun import(context: Context, json: String) {
        val root = JSONObject(json)
        require(root.optString("app") == "it.sunw.widget") { "Not a Sunrise · Sunset settings file" }

        root.optJSONObject("place")?.let {
            LocationStore(context).save(
                it.getDouble("lat"), it.getDouble("lon"),
                automatic = it.optBoolean("automatic", false),
                name = if (it.isNull("name")) null else it.optString("name"),
            )
        }
        root.optJSONArray("favorites")?.let { list ->
            val store = FavoritesStore(context)
            for (i in 0 until list.length()) {
                val f = list.getJSONObject(i)
                store.add(Favorite(f.getString("name"), f.getDouble("lat"), f.getDouble("lon")))
            }
        }
        root.optJSONObject("appearance")?.let { a ->
            val store = AppearanceStore(context)
            CurveStyle.values().firstOrNull { it.key == a.optString("curve") }?.let { store.curveStyle = it }
            Accent.values().firstOrNull { it.key == a.optString("accent") }?.let { store.accent = it }
            Theme.values().firstOrNull { it.key == a.optString("theme") }?.let { store.theme = it }
        }
        root.optJSONObject("watch")?.let { restoreWatch(context, it) }
        root.optJSONArray("digest")?.let { list ->
            DigestScheduler.setTimes(context, (0 until list.length()).mapNotNull { runCatching { LocalTime.parse(list.getString(it)) }.getOrNull() })
        }
        root.optJSONObject("weatherDay")?.let { WeatherDayActivity.setStepHours(context, it.optInt("stepHours", 1)) }
        root.optJSONArray("recents")?.let { list ->
            RecentPlaces(context).replace((0 until list.length()).mapNotNull { i ->
                list.optJSONObject(i)?.let { Favorite(it.optString("name"), it.optDouble("lat"), it.optDouble("lon")) }
            }.filter { it.name.isNotEmpty() && !it.latitude.isNaN() && !it.longitude.isNaN() })
        }
        root.optJSONArray("webcams")?.let { OwnWebcamStore(context).restore(it) }
        root.optJSONObject("alerts")?.let { AlertNotifier.setEnabled(context, it.optBoolean("notify", true)) }
    }
}
