package it.sunw.widget

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Opened from the launcher or by tapping the widget: choose automatic location or fixed coordinates. */
class SettingsActivity : Activity() {

    private lateinit var store: LocationStore
    private lateinit var auto: Switch
    private lateinit var latitude: EditText
    private lateinit var longitude: EditText
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = LocationStore(this)
        auto = findViewById(R.id.auto_location)
        latitude = findViewById(R.id.latitude)
        longitude = findViewById(R.id.longitude)
        summary = findViewById(R.id.summary)

        val place = store.current()
        auto.isChecked = place.automatic
        showCoordinates(place.latitude, place.longitude)
        setManualEnabled(!place.automatic)

        auto.setOnCheckedChangeListener { _, checked ->
            setManualEnabled(!checked)
            if (checked && !store.hasLocationPermission()) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
            } else if (checked) {
                applyAutomatic()
            }
        }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
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
        store.save(lat, lon, auto.isChecked)
        SunWidgetProvider.updateAll(this)
        refreshSummary()
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun refreshSummary() {
        val place = store.current()
        val zone = ZoneId.systemDefault()
        summary.text = when (val day = SunCalculator.day(LocalDate.now(zone), place.latitude, place.longitude, zone)) {
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
    }
}
