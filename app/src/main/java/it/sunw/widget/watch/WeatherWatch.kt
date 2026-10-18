package it.sunw.widget.watch

import android.content.Context
import it.sunw.widget.LocationStore
import it.sunw.widget.alerts.AlertNotifier
import java.time.Instant

/** One pass of the weather watch: download, update the events' states, notify what is new. */
object WeatherWatch {

    /** Blocking (network): call from a background thread. Returns how many notifications were posted. */
    fun run(context: Context, now: Instant = Instant.now()): Int {
        val types = WatchPrefs.enabledTypes(context)
        if (types.isEmpty() || !AlertNotifier.hasPermission(context)) return 0
        val place = LocationStore(context).current()
        val data = WatchData.fetch(place) // an error ends the pass: nothing changes (no data is not "all clear")
        val tracker = EventTracker.load(context, EventTracker.placeKey(place.latitude, place.longitude))
        val alerts = tracker.update(data, now, types)
        for (alert in alerts) WeatherNotifier.post(context, alert, place, data.localNow(now))
        return alerts.size
    }
}
