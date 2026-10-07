package it.sunw.widget

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.ZoneId
import java.io.IOException
import java.util.Locale

/**
 * Second page, behind the gear icon: location (device, search by name via the system Geocoder,
 * or typed coordinates), favourite places, and appearance (curve style, accent, background).
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
        findViewById<TextView>(R.id.back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.add_favorite).setOnClickListener { addFavorite() }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.search).setOnClickListener { search() }
        searchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) search()
            actionId == EditorInfo.IME_ACTION_SEARCH
        }
        refreshSummary()
        refreshFavorites()
        buildAppearance()
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
        refreshFavorites()
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
        summary.text = MainActivity.placeLabel(this, place) + "\n" + when (val day = SunCalculator.day(LocalDate.now(zone), place.latitude, place.longitude, zone)) {
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

    // --- Favourites --------------------------------------------------------------------------

    private fun refreshFavorites() {
        val list = findViewById<LinearLayout>(R.id.favorites)
        list.removeAllViews()
        val current = store.current()
        val favorites = FavoritesStore(this).all()
        if (favorites.isEmpty()) {
            list.addView(TextView(this).apply {
                text = getString(R.string.favorites_empty)
                alpha = 0.6f
                setPadding(dp(10), dp(8), dp(10), dp(8))
            })
        }
        for (fav in favorites) {
            val selected = fav.matches(current)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(44)
                setPadding(dp(10), 0, dp(10), 0)
                if (selected) background = rounded(SELECTED_ROW, dp(10).toFloat())
                setOnClickListener { selectFavorite(fav) }
                setOnLongClickListener { confirmRemove(fav); true }
            }
            row.addView(TextView(this).apply {
                text = (if (selected) "★  " else "☆  ") + fav.name
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = String.format(Locale.ROOT, "%.2f, %.2f", fav.latitude, fav.longitude)
                textSize = 12f
                alpha = 0.6f
            })
            list.addView(row)
        }
    }

    private fun selectFavorite(fav: Favorite) {
        auto.isChecked = false
        showCoordinates(fav.latitude, fav.longitude)
        picked = latitude.text.toString() to longitude.text.toString()
        pickedName = fav.name
        save()
    }

    private fun addFavorite() {
        val place = store.current()
        val input = EditText(this).apply {
            setText(place.name ?: if (place.automatic) "" else MainActivity.placeLabel(this@SettingsActivity, place))
            hint = getString(R.string.favorite_name_hint)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_favorite_title)
            .setView(LinearLayout(this).apply { setPadding(dp(22), dp(8), dp(22), 0); addView(input) })
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    FavoritesStore(this).add(Favorite(name, place.latitude, place.longitude))
                    if (!place.automatic) store.save(place.latitude, place.longitude, automatic = false, name = name)
                    SunWidgetProvider.updateAll(this)
                    refreshSummary()
                    refreshFavorites()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemove(fav: Favorite) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.remove_favorite, fav.name))
            .setPositiveButton(R.string.remove) { _, _ ->
                FavoritesStore(this).remove(fav)
                refreshFavorites()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- Appearance ---------------------------------------------------------------------------

    private fun buildAppearance() {
        val appearance = AppearanceStore(this)
        val group = findViewById<RadioGroup>(R.id.curve_styles)
        group.removeAllViews()
        for (style in CurveStyle.values()) {
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(style.label)
                isChecked = style == appearance.curveStyle
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        appearance.curveStyle = style
                        SunWidgetProvider.updateAll(this@SettingsActivity)
                    }
                }
            })
        }
        swatches(findViewById(R.id.accents), Accent.values().toList(), appearance.accent, { getString(it.label) },
            { it.resolve(this) }) { appearance.accent = it }
        swatches(findViewById(R.id.themes), Theme.values().toList(), appearance.theme, { getString(it.label) },
            { appearance.palette(it).background }) { appearance.theme = it }
    }

    /** A row of round colour swatches with labels; the selected one gets a ring. */
    private fun <T> swatches(
        row: LinearLayout,
        items: List<T>,
        selected: T,
        label: (T) -> String,
        color: (T) -> Int,
        onPick: (T) -> Unit,
    ) {
        row.removeAllViews()
        for (item in items) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener {
                    onPick(item)
                    SunWidgetProvider.updateAll(this@SettingsActivity)
                    swatches(row, items, item, label, color, onPick)
                }
            }
            cell.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color(item))
                    if (item == selected) setStroke(dp(3), Color.WHITE) else setStroke(dp(1), 0x40FFFFFF)
                }
            })
            cell.addView(TextView(this).apply {
                text = label(item)
                textSize = 10f
                gravity = Gravity.CENTER
                alpha = if (item == selected) 1f else 0.65f
                setPadding(0, dp(4), 0, 0)
            })
            row.addView(cell)
        }
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
        private const val SELECTED_ROW = 0x33FFB547
    }
}
