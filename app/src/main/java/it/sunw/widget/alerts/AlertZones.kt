package it.sunw.widget.alerts

import android.content.Context
import org.json.JSONObject

/**
 * The Civil Protection alert zones of Italy (outlines simplified to ~300 m, from the bulletin's
 * GeoJSON; see tools/make_alert_zones.py), to find which zone a place is in without a network.
 */
class AlertZones(private val zones: List<Zone>) {

    /** Rings as flat (lon, lat) pairs in 1e-4 degrees; holes are just more rings (even-odd rule). */
    class Zone(val name: String, val rings: List<IntArray>)

    /** Name of the zone containing the point, or null outside Italy (or at sea). */
    fun zoneAt(latitude: Double, longitude: Double): String? {
        val x = longitude * 1e4
        val y = latitude * 1e4
        return zones.firstOrNull { zone -> zone.rings.count { inside(it, x, y) } % 2 == 1 }?.name
    }

    private fun inside(ring: IntArray, x: Double, y: Double): Boolean {
        var c = false
        val n = ring.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = ring[2 * i].toDouble()
            val yi = ring[2 * i + 1].toDouble()
            val xj = ring[2 * j].toDouble()
            val yj = ring[2 * j + 1].toDouble()
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    companion object {
        @Volatile private var loaded: AlertZones? = null

        /** Loaded once from the bundled asset. */
        fun get(context: Context): AlertZones = loaded ?: synchronized(this) {
            loaded ?: parse(context.assets.open("alert_zones.json").bufferedReader().use { it.readText() }).also { loaded = it }
        }

        fun parse(json: String): AlertZones {
            val array = JSONObject(json).getJSONArray("zones")
            return AlertZones((0 until array.length()).map { i ->
                val z = array.getJSONObject(i)
                val rings = z.getJSONArray("rings")
                Zone(z.getString("name"), (0 until rings.length()).map { r ->
                    val ring = rings.getJSONArray(r)
                    IntArray(ring.length()) { ring.getInt(it) }
                })
            })
        }
    }
}
