package it.sunw.widget

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.ZoneId
import java.io.IOException
import java.util.Locale

/**
 * Opened from the launcher or by tapping the widget: choose automatic location, search a place
 * by name (system Geocoder) or type fixed coordinates.
 */
class SettingsActivity : Activity() {

    private lateinit var store: LocationStore
    private lateinit var auto: Switch
    private lateinit var latitude: EditText
    private lateinit var longitude: EditText
    private lateinit var summary: TextView
    private lateinit var searchQuery: EditText

    /** Place picked from search, kept while the coordinate fields still show its values. */
    private var picked: Pair<String, String>? = null
    private var pickedName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = LocationStore(this)
        auto = findViewById(R.id.auto_location)
        latitude = findViewById(R.id.latitude)
        longitude = findViewById(R.id.longitude)
        summary = findViewById(R.id.summary)
        searchQuery = findViewById(R.id.search_query)

        val place = store.current()
        auto.isChecked = place.automatic
        showCoordinates(place.latitude, place.longitude)
        setManualEnabled(!place.automatic)
        if (place.name != null) {
            picked = latitude.text.toString() to longitude.text.toString()
            pickedName = place.name
        }

        auto.setOnCheckedChangeListener { _, checked ->
            setManualEnabled(!checked)
            if (checked && !store.hasLocationPermission()) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
            } else if (checked) {
                applyAutomatic()
            }
        }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.search).setOnClickListener { search() }
        searchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) search()
            actionId == EditorInfo.IME_ACTION_SEARCH
        }
        refreshSummary()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQUEST_LOCATION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            applyAutomatic()
        } else {
            auto.isChecked = false
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun applyAutomatic() {
        val fix = store.lastKnownLocation()
        if (fix != null) {
            showCoordinates(fix.latitude, fix.longitude)
        } else {
            Toast.makeText(this, R.string.no_fix_yet, Toast.LENGTH_LONG).show()
        }
    }

    private fun save() {
        val lat = latitude.text.toString().replace(',', '.').toDoubleOrNull()
        val lon = longitude.text.toString().replace(',', '.').toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            Toast.makeText(this, R.string.invalid_coordinates, Toast.LENGTH_LONG).show()
            return
        }
        // Keep the searched name only if the user didn't retype the coordinates afterwards.
        val name = pickedName.takeIf { picked == latitude.text.toString() to longitude.text.toString() }
        store.save(lat, lon, auto.isChecked, if (auto.isChecked) null else name)
        SunWidgetProvider.updateAll(this)
        refreshSummary()
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun search() {
        val query = searchQuery.text.toString().trim()
        if (query.isEmpty()) return
        if (!Geocoder.isPresent()) {
            Toast.makeText(this, R.string.search_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(searchQuery.windowToken, 0)
        // Geocoder blocks on the network: run it off the main thread.
        Thread {
            val results = try {
                @Suppress("DEPRECATION") // the async overload needs API 33
                Geocoder(this, Locale.getDefault()).getFromLocationName(query, MAX_RESULTS).orEmpty()
                    .filter { it.hasLatitude() && it.hasLongitude() }
            } catch (e: IOException) {
                null
            }
            runOnUiThread { if (!isFinishing && !isDestroyed) showResults(results) }
        }.start()
    }

    private fun showResults(results: List<Address>?) {
        when {
            results == null -> Toast.makeText(this, R.string.search_failed, Toast.LENGTH_LONG).show()
            results.isEmpty() -> Toast.makeText(this, R.string.search_no_results, Toast.LENGTH_LONG).show()
            results.size == 1 -> pick(results[0])
            else -> AlertDialog.Builder(this)
                .setTitle(R.string.search_pick)
                .setItems(results.map { describe(it) }.toTypedArray()) { _, which -> pick(results[which]) }
                .show()
        }
    }

    /** Fills in the coordinates of [address], switches to manual mode and saves right away. */
    private fun pick(address: Address) {
        auto.isChecked = false
        showCoordinates(address.latitude, address.longitude)
        picked = latitude.text.toString() to longitude.text.toString()
        pickedName = shortName(address)
        save()
    }

    /** "Passo dello Stelvio, Bormio, Lombardia, Italia" — for the result list. */
    private fun describe(address: Address): String =
        listOfNotNull(address.featureName, address.locality, address.subAdminArea, address.adminArea, address.countryName)
            .filter { it.isNotBlank() && it.toDoubleOrNull() == null } // featureName can be a house number
            .distinct()
            .joinToString(", ")

    private fun shortName(address: Address): String =
        listOfNotNull(address.locality, address.featureName, address.subAdminArea, address.adminArea)
            .firstOrNull { it.isNotBlank() && it.toDoubleOrNull() == null }
            ?: describe(address)

    private fun refreshSummary() {
        val place = store.current()
        val zone = ZoneId.systemDefault()
        val where = when {
            place.automatic -> getString(R.string.place_device)
            place.name != null -> place.name
            else -> String.format(Locale.ROOT, "%.4f, %.4f", place.latitude, place.longitude)
        }
        summary.text = where + "\n" + when (val day = SunCalculator.day(LocalDate.now(zone), place.latitude, place.longitude, zone)) {
            is SunCalculator.Day.Normal -> getString(
                R.string.summary,
                Formatters.time(this, day.sunrise, zone),
                Formatters.time(this, day.sunset, zone),
                Formatters.length(day.length),
                Formatters.time(this, day.solarNoon, zone),
            )
            is SunCalculator.Day.PolarDay -> getString(R.string.polar_day)
            is SunCalculator.Day.PolarNight -> getString(R.string.polar_night)
        }
    }

    private fun showCoordinates(lat: Double, lon: Double) {
        latitude.setText(String.format(Locale.ROOT, "%.4f", lat))
        longitude.setText(String.format(Locale.ROOT, "%.4f", lon))
    }

    private fun setManualEnabled(enabled: Boolean) {
        latitude.isEnabled = enabled
        longitude.isEnabled = enabled
    }

    companion object {
        private const val REQUEST_LOCATION = 1
        private const val MAX_RESULTS = 6
    }
}
