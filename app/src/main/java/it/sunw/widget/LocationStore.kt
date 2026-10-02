package it.sunw.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager

data class Place(val latitude: Double, val longitude: Double, val automatic: Boolean)

/**
 * Remembers where the widget computes the Sun for. In automatic mode it uses the
 * device's last known (coarse) location, falling back to the last saved fix and
 * finally to Parma.
 */
class LocationStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("place", Context.MODE_PRIVATE)

    fun current(): Place {
        val automatic = prefs.getBoolean(KEY_AUTO, true)
        if (automatic) lastKnownLocation()?.let { save(it.latitude, it.longitude, automatic = true) }
        return Place(
            Double.fromBits(prefs.getLong(KEY_LAT, DEFAULT_LAT.toRawBits())),
            Double.fromBits(prefs.getLong(KEY_LON, DEFAULT_LON.toRawBits())),
            automatic,
        )
    }

    fun save(latitude: Double, longitude: Double, automatic: Boolean) {
        prefs.edit()
            .putLong(KEY_LAT, latitude.toRawBits())
            .putLong(KEY_LON, longitude.toRawBits())
            .putBoolean(KEY_AUTO, automatic)
            .apply()
    }

    fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return try {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
    }

    companion object {
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_AUTO = "auto"
        const val DEFAULT_LAT = 44.8015 // Parma
        const val DEFAULT_LON = 10.3279
    }
}
