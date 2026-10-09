package it.sunw.widget

import android.content.Context
import it.sunw.widget.alerts.AlertNotifier
import org.json.JSONArray
import org.json.JSONObject

/**
 * All user settings as one JSON document: place, favourites, appearance and alert notifications. Used by
 * "Export / Import settings" so they survive a reinstall or a new phone.
 */
object SettingsBackup {

    private const val VERSION = 1

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
            .toString(2)
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
        root.optJSONObject("alerts")?.let { AlertNotifier.setEnabled(context, it.optBoolean("notify", true)) }
    }
}
