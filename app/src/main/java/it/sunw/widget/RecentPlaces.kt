package it.sunw.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** The last places picked from a search, newest first, at most [MAX] (the "Recents" of the search dialog). */
class RecentPlaces(context: Context) {

    private val prefs = context.getSharedPreferences("recent_places", Context.MODE_PRIVATE)

    fun all(): List<Favorite> {
        val json = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until json.length()).mapNotNull { i ->
            json.optJSONObject(i)?.let { Favorite(it.optString("name"), it.optDouble("lat"), it.optDouble("lon")) }
        }.filter { it.name.isNotEmpty() && !it.latitude.isNaN() && !it.longitude.isNaN() }
    }

    /** Puts [place] first; the same place picked again (same name or the same spot) moves up instead of repeating. */
    fun add(place: Favorite) {
        val others = all().filterNot { it.name == place.name || (abs(it.latitude - place.latitude) < SAME_SPOT_DEG && abs(it.longitude - place.longitude) < SAME_SPOT_DEG) }
        val json = JSONArray()
        (listOf(place) + others).take(MAX).forEach {
            json.put(JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude))
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    companion object {
        const val MAX = 10
        private const val KEY = "list"
        private const val SAME_SPOT_DEG = 1e-3 // ~100 m
    }
}
