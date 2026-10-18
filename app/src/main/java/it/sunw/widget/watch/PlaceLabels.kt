package it.sunw.widget.watch

import android.content.Context
import android.location.Geocoder
import it.sunw.widget.Place
import it.sunw.widget.R
import java.util.Locale

/**
 * The line in the header of a weather notification that says where it is about: "📍 Parma" for the place the user set,
 * "Device · Milan" for the device's own position (the city from Android's geocoder, kept once found; the rounded
 * coordinates when it cannot be reached). A place in automatic mode is the device's position.
 */
object PlaceLabels {
    private const val PREFS = "place_labels"

    /** May use the network (the geocoder) the first time for a position: call from a background thread. */
    fun subText(context: Context, place: Place): String =
        if (place.automatic) context.getString(R.string.label_device, cityOf(context, place))
        else context.getString(R.string.label_place, place.name ?: coordinates(place))

    private fun coordinates(place: Place) = String.format(Locale.ROOT, "%.2f, %.2f", place.latitude, place.longitude)

    private fun cityOf(context: Context, place: Place): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = EventTracker.placeKey(place.latitude, place.longitude)
        prefs.getString(key, null)?.let { return it }
        val found = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocation(place.latitude, place.longitude, 1)?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull()
        if (found == null) return coordinates(place)
        prefs.edit().putString(key, found).apply()
        return found
    }
}
