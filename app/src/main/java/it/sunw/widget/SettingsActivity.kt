package it.sunw.widget

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.location.Address
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import it.sunw.widget.alerts.AlertNotifier
import it.sunw.widget.watch.DigestScheduler
import it.sunw.widget.watch.EventType
import it.sunw.widget.watch.Param
import it.sunw.widget.watch.Thresholds
import it.sunw.widget.watch.WatchJobService
import it.sunw.widget.watch.WatchPrefs
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
        findViewById<Button>(R.id.export_settings).setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE, BACKUP_FILE_NAME),
                REQUEST_EXPORT,
            )
        }
        findViewById<Button>(R.id.import_settings).setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                REQUEST_IMPORT,
            )
        }
        findViewById<Switch>(R.id.alert_notifications).apply {
            isChecked = AlertNotifier.isEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                AlertNotifier.setEnabled(this@SettingsActivity, checked)
                if (checked && !AlertNotifier.hasPermission(this@SettingsActivity)) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                }
            }
        }
        setUpWatch()
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.search).setOnClickListener { search() }
        searchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) search()
            actionId == EditorInfo.IME_ACTION_SEARCH
        }
        findViewById<Button>(R.id.battery_fix).setOnClickListener { BatteryGuard.open(this) }
        refreshSummary()
        refreshFavorites()
        buildAppearance()
        showBuildInfo()
        refreshBattery()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) refreshBattery() // back from the system's battery choice
    }

    /** Battery state: a line saying whether the widget is held back, and the button only while it is. */
    private fun refreshBattery() {
        val limited = BatteryGuard.isRestricted(this)
        findViewById<TextView>(R.id.battery_status).setText(if (limited) R.string.battery_status_limited else R.string.battery_status_ok)
        findViewById<Button>(R.id.battery_fix).visibility = if (limited) View.VISIBLE else View.GONE
        showWidgetDiagnostics()
    }

    /**
     * For each cause (alarm, job, screen on, system, app…) when it last redrew the widget and how many times, and
     * when the next check is due: shows which triggers really work on this phone. Opening the app is one cause among
     * the others, so it does not hide what they did while the phone was idle.
     */
    private fun showWidgetDiagnostics() {
        val zone = ZoneId.systemDefault()
        val entries = WidgetRefresh.entries(this)
        val next = WidgetRefresh.diagnostics(this)?.next
        findViewById<TextView>(R.id.widget_diag).text = if (entries.isEmpty()) getString(R.string.wr_diag_none) else buildString {
            append(getString(R.string.wr_diag_head))
            for (e in entries) {
                append('\n').append(getString(R.string.wr_diag_line, getString(e.source.label), Formatters.time(this@SettingsActivity, e.at, zone), e.count))
            }
            next?.let { append('\n').append(getString(R.string.wr_diag_next, Formatters.time(this@SettingsActivity, it, zone))) }
        }
    }

    // --- Theme ----------------------------------------------------------------------------------

    /** Weather notifications: the master switch, one box per event, and the list of recurring summary times. */
    private fun setUpWatch() {
        val events = findViewById<LinearLayout>(R.id.watch_events)
        findViewById<Switch>(R.id.watch_notifications).apply {
            isChecked = WatchPrefs.isEnabled(this@SettingsActivity)
            events.alpha = if (isChecked) 1f else 0.4f
            setOnCheckedChangeListener { _, checked ->
                WatchPrefs.setEnabled(this@SettingsActivity, checked)
                WatchJobService.ensure(this@SettingsActivity)
                events.alpha = if (checked) 1f else 0.4f
                if (checked && !AlertNotifier.hasPermission(this@SettingsActivity)) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
                }
            }
        }
        for (type in EventType.values()) {
            // One row per event: its switch, and the button for its thresholds.
            events.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(CheckBox(this@SettingsActivity).apply {
                    text = getString(type.title)
                    tag = type.key
                    minHeight = dp(40)
                    isChecked = WatchPrefs.isOn(this@SettingsActivity, type)
                    setOnCheckedChangeListener { _, on -> WatchPrefs.setOn(this@SettingsActivity, type, on) }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(Button(this@SettingsActivity).apply {
                    text = getString(R.string.thr_button)
                    tag = "thresholds_" + type.key
                    setOnClickListener { showThresholds(type) }
                })
            })
        }
        findViewById<Button>(R.id.watch_general).setOnClickListener { showGeneralParameters() }
        findViewById<Button>(R.id.watch_reset).setOnClickListener {
            Thresholds.reset(this, null)
            Toast.makeText(this, R.string.thr_reset_done, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.digest_add).setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                if (!DigestScheduler.add(this, LocalTime.of(hour, minute))) Toast.makeText(this, R.string.digest_exists, Toast.LENGTH_SHORT).show()
                showDigestTimes()
            }, 12, 0, true).show()
        }
        showDigestTimes()
        style(events, AppearanceStore(this).palette())
    }

    /** The thresholds and the lead time of [type]: edited in a dialog, checked before they are kept. */
    internal fun showThresholds(type: EventType): AlertDialog = showParams(
        getString(type.title), type.params, Thresholds.load(this).valuesOf(type),
        invalid = { type.invalid(it) }, save = { Thresholds.save(this, type, it) }, reset = { Thresholds.reset(this, type) },
        labelOf = { type.param(it).label },
    )

    /** How the watch confirms the end of an event and avoids repeating itself. */
    internal fun showGeneralParameters(): AlertDialog = showParams(
        getString(R.string.thr_general), Thresholds.GENERAL, Thresholds.load(this).generalValues(),
        invalid = { v -> Thresholds.GENERAL.firstOrNull { v[it.key]!! < it.min || v[it.key]!! > it.max }?.key },
        save = { Thresholds.saveGeneral(this, it) },
        reset = { Thresholds.saveGeneral(this, Thresholds.GENERAL.associate { it.key to it.default }) },
        labelOf = { key -> Thresholds.GENERAL.first { it.key == key }.label },
    )

    private fun showParams(
        title: String, params: List<Param>, current: Map<String, Double>,
        invalid: (Map<String, Double>) -> String?, save: (Map<String, Double>) -> Unit, reset: () -> Unit, labelOf: (String) -> Int,
    ): AlertDialog {
        fun plain(v: Double) = v.toString().removeSuffix(".0")
        val fields = LinkedHashMap<String, EditText>()
        val box = LinearLayout(this).apply {
            id = R.id.watch_params
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        box.addView(TextView(this).apply { text = getString(R.string.thr_hint); textSize = 13f; alpha = 0.8f })
        for (p in params) {
            box.addView(TextView(this).apply {
                text = getString(p.label) + if (p.unit.isNotEmpty()) " (${p.unit})" else ""
                textSize = 14f
                setPadding(0, dp(10), 0, 0)
            })
            fields[p.key] = EditText(this).apply {
                setText(plain(current[p.key] ?: p.default))
                hint = plain(p.default)
                tag = p.key
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                setSelectAllOnFocus(true)
            }.also { box.addView(it) }
        }
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton(android.R.string.ok, null) // set below, so a wrong value does not close the dialog
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.thr_defaults) { _, _ -> reset() }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val parsed = fields.mapValues { (_, e) -> e.text.toString().replace(',', '.').toDoubleOrNull() }
            val wrong = parsed.entries.firstOrNull { it.value == null }?.key
            val values = parsed.mapValues { it.value ?: 0.0 }
            val bad = wrong ?: invalid(values)
            if (bad != null) {
                Toast.makeText(this, getString(R.string.thr_invalid, getString(labelOf(bad))), Toast.LENGTH_LONG).show()
            } else {
                save(values)
                dialog.dismiss()
            }
        }
        return dialog
    }

    /** One row per recurring summary time, each with its Delete button. */
    internal fun showDigestTimes() {
        val box = findViewById<LinearLayout>(R.id.digest_times)
        box.removeAllViews()
        val times = DigestScheduler.times(this)
        if (times.isEmpty()) {
            box.addView(TextView(this).apply { text = getString(R.string.digest_none); textSize = 14f })
        }
        for (time in times) {
            box.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                tag = "digest_" + time
                addView(TextView(this@SettingsActivity).apply {
                    text = time.toString()
                    textSize = 18f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(Button(this@SettingsActivity).apply {
                    text = getString(R.string.digest_remove)
                    setOnClickListener {
                        DigestScheduler.remove(this@SettingsActivity, time)
                        showDigestTimes()
                    }
                })
            })
        }
        style(box, AppearanceStore(this).palette())
    }

    /** The page, system bars and every control in the colours of the chosen theme and accent. */
    private fun applyTheme(root: View = findViewById(android.R.id.content)) {
        val palette = AppearanceStore(this).palette()
        window.decorView.setBackgroundColor(palette.pageBackground)
        palette.styleSystemBars(window)
        style(root, palette)
    }

    private fun style(view: View, p: Palette) {
        val checkedOrNot = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        when (view) {
            is Switch -> {
                view.setTextColor(p.text)
                view.thumbTintList = ColorStateList(checkedOrNot, intArrayOf(p.accent, p.textSecondary))
                view.trackTintList = ColorStateList(checkedOrNot, intArrayOf(Palette.blend(p.cardBackground, p.accent, 0.45f), p.raised(0.2f)))
            }
            is RadioButton -> {
                view.setTextColor(p.text)
                view.buttonTintList = ColorStateList(checkedOrNot, intArrayOf(p.accent, p.textSecondary))
            }
            is CompoundButton -> view.setTextColor(p.text)
            is Button -> {
                view.setTextColor(p.text)
                view.background = RippleDrawable(
                    ColorStateList.valueOf(p.raised(0.2f)),
                    rounded(Palette.blend(p.cardBackground, p.accent, 0.25f), dp(10).toFloat()), null,
                )
            }
            is EditText -> {
                view.setTextColor(p.text)
                view.setHintTextColor(Palette.blend(p.cardBackground, p.text, 0.5f))
                view.backgroundTintList = ColorStateList.valueOf(p.textSecondary)
                view.highlightColor = Palette.blend(p.cardBackground, p.accent, 0.4f)
            }
            is TextView -> {
                view.setTextColor(if (view.tag == "section") p.accent else p.text)
                if (view.compoundDrawablesRelative.any { it != null }) view.compoundDrawableTintList = ColorStateList.valueOf(p.text)
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i), p)
    }

    /** "Version 1.0.42 · build #42 · 08/10/2026 14:05 · a1b2c3d" from the values baked in by CI. */
    private fun showBuildInfo() {
        val built = java.time.Instant.ofEpochMilli(BuildConfig.BUILD_TIME_MILLIS).atZone(ZoneId.systemDefault())
        findViewById<TextView>(R.id.build_info).text = getString(
            R.string.build_info,
            BuildConfig.VERSION_NAME,
            BuildConfig.BUILD_NUMBER,
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(built),
            BuildConfig.GIT_SHA,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_EXPORT -> exportTo(uri)
            REQUEST_IMPORT -> importFrom(uri)
        }
    }

    internal fun exportTo(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(SettingsBackup.export(this)) }
            Toast.makeText(this, R.string.settings_exported, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.backup_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
        }
    }

    internal fun importFrom(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            SettingsBackup.import(this, json)
            SunWidgetProvider.updateAll(this)
            Toast.makeText(this, R.string.settings_imported, Toast.LENGTH_SHORT).show()
            recreate() // re-read every field from the restored settings
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.backup_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.alert_notifications_denied, Toast.LENGTH_LONG).show()
            }
            return
        }
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

    /** "Save" button: store the place and go back to the main page, where the change shows. */
    private fun save() {
        if (persist()) {
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** Stores the place shown in the fields and refreshes the widgets; false if the coordinates are invalid. */
    private fun persist(): Boolean {
        val lat = latitude.text.toString().replace(',', '.').toDoubleOrNull()
        val lon = longitude.text.toString().replace(',', '.').toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            Toast.makeText(this, R.string.invalid_coordinates, Toast.LENGTH_LONG).show()
            return false
        }
        // Keep the searched name only if the user didn't retype the coordinates afterwards.
        val name = pickedName.takeIf { picked == latitude.text.toString() to longitude.text.toString() }
        store.save(lat, lon, auto.isChecked, if (auto.isChecked) null else name)
        SunWidgetProvider.updateAll(this)
        refreshSummary()
        refreshFavorites()
        return true
    }

    private fun search() {
        val query = searchQuery.text.toString().trim()
        if (query.isEmpty()) return
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(searchQuery.windowToken, 0)
        PlaceSearch.search(this, query) { pick(it) }
    }

    /**
     * Uses the found place right away (manual mode), then offers to add it to the favourites;
     * either way it returns to the main page, which shows the new place.
     */
    internal fun pick(address: Address) {
        auto.isChecked = false
        showCoordinates(address.latitude, address.longitude)
        val name = PlaceSearch.shortName(address)
        picked = latitude.text.toString() to longitude.text.toString()
        pickedName = name
        if (!persist()) return
        val place = store.current()
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(R.string.search_picked)
            .setPositiveButton(R.string.add_to_favorites) { _, _ ->
                FavoritesStore(this).add(Favorite(name, place.latitude, place.longitude))
                Toast.makeText(this, getString(R.string.favorite_added, name), Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.just_use) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

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
        val palette = AppearanceStore(this).palette()
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
                if (selected) background = rounded(Palette.blend(palette.cardBackground, palette.accent, 0.22f), dp(10).toFloat())
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
            row.addView(TextView(this).apply {
                text = "✎"
                textSize = 18f
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.rename_favorite)
                minimumWidth = dp(44)
                minimumHeight = dp(44)
                setOnClickListener { renameFavorite(fav) }
            })
            list.addView(row)
        }
        style(list, palette)
    }

    private fun selectFavorite(fav: Favorite) {
        auto.isChecked = false
        showCoordinates(fav.latitude, fav.longitude)
        picked = latitude.text.toString() to longitude.text.toString()
        pickedName = fav.name
        if (persist()) finish()
    }

    private fun addFavorite() {
        val place = store.current()
        val coordinates = String.format(Locale.ROOT, "%.4f, %.4f", place.latitude, place.longitude)
        val input = EditText(this).apply {
            id = R.id.favorite_name
            setText(place.name ?: if (place.automatic) "" else coordinates)
            hint = getString(R.string.favorite_name_hint)
            setSingleLine()
            selectAll()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.add_favorite_title)
            .setView(LinearLayout(this).apply { setPadding(dp(22), dp(8), dp(22), 0); addView(input) })
            .setPositiveButton(R.string.save) { _, _ ->
                // An empty name still saves the place, labelled with its coordinates.
                val name = input.text.toString().trim().ifEmpty { coordinates }
                FavoritesStore(this).add(Favorite(name, place.latitude, place.longitude))
                if (!place.automatic) store.save(place.latitude, place.longitude, automatic = false, name = name)
                SunWidgetProvider.updateAll(this)
                refreshSummary()
                refreshFavorites()
                Toast.makeText(this, getString(R.string.favorite_added, name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renameFavorite(fav: Favorite) {
        val input = EditText(this).apply {
            id = R.id.favorite_name
            setText(fav.name)
            hint = getString(R.string.favorite_name_hint)
            setSingleLine()
            selectAll()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename_favorite)
            .setView(LinearLayout(this).apply { setPadding(dp(22), dp(8), dp(22), 0); addView(input) })
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty() || name == fav.name) return@setPositiveButton
                if (!FavoritesStore(this).rename(fav, name)) {
                    Toast.makeText(this, getString(R.string.favorite_exists, name), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // The place in use is this favourite: show the new name on the page and widgets.
                val current = store.current()
                if (fav.matches(current)) {
                    store.save(current.latitude, current.longitude, automatic = false, name = name)
                    SunWidgetProvider.updateAll(this)
                    refreshSummary()
                }
                refreshFavorites()
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
        showThemes()
    }

    /** The theme picker: one scrolling row per group, the Legacy theme first and alone, without a title. */
    private fun showThemes() {
        val appearance = AppearanceStore(this)
        val container = findViewById<LinearLayout>(R.id.themes)
        container.removeAllViews()
        val rows = ArrayList<Pair<LinearLayout, List<Theme>>>()
        fun refresh() = rows.forEach { (row, items) ->
            swatches(row, items, appearance.theme, { getString(it.label) }, { appearance.palette(it).background }, ::pickTheme)
        }
        pickThemeHook = ::refresh
        for (group in ThemeGroup.values()) {
            val items = Theme.values().filter { it.group == group }
            if (items.isEmpty()) continue
            group.title?.let { title ->
                container.addView(TextView(this).apply {
                    text = getString(title)
                    textSize = 12f
                    alpha = 0.7f
                    setPadding(0, dp(8), 0, 0)
                })
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            container.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            })
            rows.add(row to items)
        }
        refresh()
    }

    private var pickThemeHook: (() -> Unit)? = null

    private fun pickTheme(theme: Theme) {
        AppearanceStore(this).theme = theme
        pickThemeHook?.invoke()
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
                    applyTheme() // the page follows the new theme or accent at once
                }
            }
            cell.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color(item))
                    val ring = AppearanceStore(this@SettingsActivity).palette()
                    if (item == selected) setStroke(dp(3), ring.text) else setStroke(dp(1), Palette.blend(ring.pageBackground, ring.text, 0.3f))
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
        private const val REQUEST_EXPORT = 2
        private const val REQUEST_IMPORT = 3
        private const val REQUEST_NOTIFICATIONS = 4
        private const val BACKUP_FILE_NAME = "alba-tramonto-impostazioni.json"
    }
}
