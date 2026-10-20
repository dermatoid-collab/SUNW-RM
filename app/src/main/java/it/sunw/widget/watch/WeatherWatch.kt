package it.sunw.widget.watch

import android.content.Context
import it.sunw.widget.LocationStore
import it.sunw.widget.Place
import it.sunw.widget.alerts.AlertNotifier
import java.time.Instant
import kotlin.math.abs

/** One pass of the weather watch: for each place watched, download, update the events' states, notify what is new. */
object WeatherWatch {

    /** Two places closer than this (degrees, about 2 km) are the same place. */
    private const val SAME_PLACE_DEG = 0.02

    /** The place in use and, when it is somewhere else, the device's own position: each has its own events. */
    fun placesToWatch(active: Place, device: Place?): List<Place> =
        if (device == null || (abs(device.latitude - active.latitude) < SAME_PLACE_DEG && abs(device.longitude - active.longitude) < SAME_PLACE_DEG)) listOf(active)
        else listOf(active, device)

    /** Blocking (network): call from a background thread. Returns how many notifications were posted. */
    fun run(context: Context, now: Instant = Instant.now()): Int {
        val types = WatchPrefs.enabledTypes(context)
        if (types.isEmpty() || !AlertNotifier.hasPermission(context)) return 0
        val store = LocationStore(context)
        val active = store.current()
        val device = store.lastKnownLocation()?.let { Place(it.latitude, it.longitude, automatic = true) }
        val places = placesToWatch(active, device)
        EventTracker.prune(context, places.map { EventTracker.placeKey(it.latitude, it.longitude) })
        var posted = 0
        for (place in places) {
            // A place that cannot be reached is skipped: no data is not "all clear", so its events stay as they are.
            val data = runCatching { WatchData.fetch(place) }.getOrNull() ?: continue
            val tracker = EventTracker.load(context, EventTracker.placeKey(place.latitude, place.longitude))
            val alerts = tracker.update(data, now, types, Thresholds.load(context))
            for (alert in alerts) WeatherNotifier.post(context, alert, place, data.localNow(now))
            posted += alerts.size
        }
        return posted
    }
}
