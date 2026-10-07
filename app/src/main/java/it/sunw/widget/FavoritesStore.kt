package it.sunw.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class Favorite(val name: String, val latitude: Double, val longitude: Double) {
    fun matches(place: Place) =
        !place.automatic && abs(place.latitude - latitude) < 1e-4 && abs(place.longitude - longitude) < 1e-4
}

/** Saved places, in the order they were added. */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun all(): List<Favorite> {
        val json = JSONArray(prefs.getString(KEY, "[]"))
        return (0 until json.length()).map { i ->
            json.getJSONObject(i).let { Favorite(it.getString("name"), it.getDouble("lat"), it.getDouble("lon")) }
        }
    }

    fun add(favorite: Favorite) = write(all().filterNot { it.name == favorite.name } + favorite)

    fun remove(favorite: Favorite) = write(all() - favorite)

    private fun write(list: List<Favorite>) {
        val json = JSONArray()
        list.forEach { json.put(JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude)) }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    companion object {
        private const val KEY = "list"
    }
}
