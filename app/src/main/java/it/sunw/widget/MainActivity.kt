package it.sunw.widget

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import it.sunw.widget.alerts.AlertJobService
import it.sunw.widget.alerts.AlertNotifier
import it.sunw.widget.alerts.AlertRepository
import it.sunw.widget.alerts.AlertUi
import it.sunw.widget.weather.WeatherCard
import it.sunw.widget.weather.WeatherRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Opened by tapping a widget or from the launcher: place (with quick switch between favourites),
 * the full 4×2 widget, and today's Moon. Settings live behind the gear icon.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    /** Next sunrise / sunset for the live countdowns; null near the poles. */
    private var nextSunrise: Instant? = null
    private var nextSunset: Instant? = null
    private var sunriseCountdown: TextView? = null
    private var sunsetCountdown: TextView? = null
    private val secondTick = object : Runnable {
        override fun run() {
            updateCountdowns()
            // Align to the next whole second so the digits change together with the clock.
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    /** False while the last crash is on screen instead of the page. */
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the app closed unexpectedly last time, say why before anything else can fail again.
        CrashLog.pending(this)?.let {
            showCrash(it)
            return
        }
        setContentView(R.layout.activity_main)
        findViewById<ImageButton>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.place).setOnClickListener { showPlaces(it) }
        // Sun or Moon tile → one page for both, at its top for the Sun and at its end for the Moon.
        for ((id, entry) in listOf(R.id.widget_container to SunMoonActivity.ENTRY_SUN, R.id.moon_card to SunMoonActivity.ENTRY_MOON)) {
            findViewById<android.view.View>(id).setOnClickListener {
                startActivity(Intent(this, SunMoonActivity::class.java).putExtra(SunMoonActivity.EXTRA_ENTRY, entry))
            }
        }
        fitToScreen()
        // A background check that can't start must never stop the page from opening.
        runCatching { setUpAlertNotifications() }.onFailure { CrashLog.record(this, it) }
        ready = true
    }

    private fun showCrash(report: String) {
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.crash_title)
            .setMessage(getString(R.string.crash_message) + "\n\n" + report)
            .setCancelable(false)
            .setPositiveButton(R.string.crash_continue) { _, _ ->
                CrashLog.clear(this)
                recreate()
            }
            .setNeutralButton(R.string.crash_copy) { _, _ ->
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("crash", report))
                CrashLog.clear(this)
                recreate()
            }
            .show()
    }

    /**
     * Starts the hourly background check of the alerts and, on Android 13+, asks once for the
     * permission to notify (it can be changed later in Settings).
     */
    private fun setUpAlertNotifications() {
        if (!AlertNotifier.isEnabled(this)) return
        AlertJobService.schedule(this)
        val prefs = getSharedPreferences("alert_notifications", MODE_PRIVATE)
        if (!AlertNotifier.hasPermission(this) && !prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    /** Extra height given to the sun tile so the page fills the screen exactly. */
    private var sunExtraPx = 0

    /**
     * Whenever the page is laid out, the space left below the last tile goes to the sun tile (up
     * to [MAX_SUN_EXTRA_DP] above and below): a quarter to its margins, the rest to the curve, so
     * the page ends at the screen edge.
     */
    private fun fitToScreen() {
        val page = findViewById<ScrollView>(R.id.page)
        page.viewTreeObserver.addOnGlobalLayoutListener {
            val content = page.getChildAt(0) ?: return@addOnGlobalLayoutListener
            if (page.height == 0) return@addOnGlobalLayoutListener
            val natural = content.height - 2 * sunExtraPx
            val free = page.height - page.paddingTop - page.paddingBottom - natural
            val extra = (free / 2).coerceIn(0, dp(MAX_SUN_EXTRA_DP))
            if (extra != sunExtraPx) {
                sunExtraPx = extra
                // The curve is a bitmap drawn for its size: draw the tile again at the new height.
                showSun(AppearanceStore(this).palette(), LocationStore(this).current(), Instant.now(), ZoneId.systemDefault())
            }
        }
    }

    private val sunPaddingPx get() = dp(EMBEDDED_PADDING_V_DP) + sunExtraPx / 4

    /**
     * The same RemoteViews the 4×2 widget shows, sized to the page width and to the tile's height
     * minus its margins (the widget's own 14 dp padding is replaced by the tile's).
     */
    private fun showSun(palette: Palette, place: Place, now: Instant, zone: ZoneId) {
        val container = findViewById<FrameLayout>(R.id.widget_container)
        val metrics = resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt() - 28
        val tilePx = dp(SUN_TILE_DP) + 2 * sunExtraPx
        val contentDp = ((tilePx - 2 * sunPaddingPx) / metrics.density).toInt()
        val renderDp = contentDp + 2 * WIDGET_PADDING_DP - COUNTDOWN_ROW_DP
        val views = WidgetRenderer(this, palette).render(WidgetRenderer.Size(widthDp, renderDp), place, now, zone, clickable = false)
        container.removeAllViews()
        val widget = views.apply(this, container)
        container.addView(widget)
        container.layoutParams = container.layoutParams.apply { height = tilePx }
        widget.setPadding(widget.paddingLeft, sunPaddingPx, widget.paddingRight, sunPaddingPx)
        setUpCountdowns(widget, palette, place, now, zone)
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        handler.post(tick)
        handler.post(secondTick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(secondTick)
        super.onPause()
    }

    private fun refresh() {
        val palette = AppearanceStore(this).palette()
        val place = LocationStore(this).current()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()

        window.decorView.setBackgroundColor(palette.pageBackground)
        window.statusBarColor = palette.pageBackground
        window.navigationBarColor = palette.pageBackground

        findViewById<TextView>(R.id.place).apply {
            text = getString(R.string.place_with_menu, placeLabel(this@MainActivity, place))
            setTextColor(palette.text)
            // Leave room for the date beside a long place name.
            maxWidth = (resources.displayMetrics.widthPixels * 0.6f).toInt()
        }
        findViewById<TextView>(R.id.date).apply {
            // Full date beside the settings button; "Fri 9 Oct" when it doesn't fit.
            val date = now.atZone(zone)
            val full = capitalize(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(date))
            val short = capitalize(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(date).replace(".", ""))
            text = full
            setTextColor(palette.textSecondary)
            post {
                val room = width - paddingLeft - paddingRight
                text = if (room > 0 && paint.measureText(full) > room) short else full
            }
        }
        findViewById<ImageButton>(R.id.settings).setColorFilter(palette.text)

        showSun(palette, place, now, zone)

        showMoon(palette, place, now, zone)
        showWeather(palette, place, now)
        showAlerts(place, now, zone)
    }

    private var alertsInFlight = false
    private var lastAlertsFailure: Instant? = null

    /** Civil Protection alert icon (Italy only): cached bulletin at once, refreshed hourly. */
    private fun showAlerts(place: Place, now: Instant, zone: ZoneId) {
        val icon = findViewById<ImageView>(R.id.weather_alert)
        val repository = AlertRepository(this)
        // Outside Italy there is nothing to show or download.
        if (AlertUi.zone(this, place) == null) {
            icon.visibility = android.view.View.GONE
            return
        }
        AlertUi.bind(icon, this, place, repository.cached(), now.atZone(zone).toLocalDate())
        val recentlyFailed = lastAlertsFailure?.let { now.isBefore(it.plusSeconds(RETRY_AFTER_FAILURE_S)) } == true
        if (repository.isFresh(now) || alertsInFlight || recentlyFailed) return
        alertsInFlight = true
        repository.refresh { result ->
            runOnUiThread {
                alertsInFlight = false
                if (isDestroyed) return@runOnUiThread
                result.onSuccess {
                    lastAlertsFailure = null
                    AlertUi.bind(icon, this, LocationStore(this).current(), it, java.time.LocalDate.now(zone))
                    // Already on screen: no notification for what the icon shows.
                    runCatching { AlertNotifier.check(this, it, post = false) }.onFailure { e -> CrashLog.record(this, e) }
                }.onFailure { lastAlertsFailure = Instant.now() }
            }
        }
    }

    private var weatherInFlight = false
    private var lastWeatherFailure: Instant? = null

    /** Cached forecast right away; a background refresh when it's missing or older than an hour. */
    private fun showWeather(palette: Palette, place: Place, now: Instant) {
        val repository = WeatherRepository(this)
        val card = WeatherCard(this)
        if (!repository.hasKey) {
            card.showStatus(palette, getString(R.string.weather_missing_key))
            return
        }
        val cached = repository.cached(place)
        if (cached != null) {
            card.show(palette, cached, now)
        } else {
            card.showStatus(palette, getString(R.string.weather_loading))
        }
        val recentlyFailed = lastWeatherFailure?.let { now.isBefore(it.plusSeconds(RETRY_AFTER_FAILURE_S)) } == true
        if ((cached == null || !repository.isFresh(cached, now)) && !weatherInFlight && !recentlyFailed) {
            weatherInFlight = true
            repository.refresh(place) { result ->
                runOnUiThread {
                    weatherInFlight = false
                    if (isDestroyed) return@runOnUiThread
                    result.onSuccess {
                        lastWeatherFailure = null
                        card.show(AppearanceStore(this).palette(), it, Instant.now())
                    }.onFailure {
                        lastWeatherFailure = Instant.now()
                        if (cached == null) card.showStatus(palette, getString(R.string.weather_error, it.message ?: it.javaClass.simpleName))
                    }
                }
            }
        }
    }

    /** Shows the countdown row under the times and works out which events it counts down to. */
    private fun setUpCountdowns(widget: android.view.View, palette: Palette, place: Place, now: Instant, zone: ZoneId) {
        val today = now.atZone(zone).toLocalDate()
        fun nextEvent(pick: (SunCalculator.Day.Normal) -> Instant): Instant? =
            (0L..1L).asSequence()
                .mapNotNull { SunCalculator.day(today.plusDays(it), place.latitude, place.longitude, zone) as? SunCalculator.Day.Normal }
                .map(pick)
                .firstOrNull { it.isAfter(now) }
        nextSunrise = nextEvent { it.sunrise }
        nextSunset = nextEvent { it.sunset }
        sunriseCountdown = widget.findViewById<TextView>(R.id.sunrise_countdown)?.apply { setTextColor(palette.textSecondary) }
        sunsetCountdown = widget.findViewById<TextView>(R.id.sunset_countdown)?.apply { setTextColor(palette.textSecondary) }
        widget.findViewById<android.view.View>(R.id.countdown_row)?.visibility =
            if (nextSunrise != null || nextSunset != null) android.view.View.VISIBLE else android.view.View.GONE
        updateCountdowns()
    }

    private fun updateCountdowns() {
        val now = Instant.now()
        // An event just passed: recompute everything (times, curve, next targets).
        if (listOfNotNull(nextSunrise, nextSunset).any { !it.isAfter(now) }) {
            refresh()
            return
        }
        sunriseCountdown?.text = nextSunrise?.let { "− " + Formatters.countdown(it.epochSecond - now.epochSecond) } ?: ""
        sunsetCountdown?.text = nextSunset?.let { "− " + Formatters.countdown(it.epochSecond - now.epochSecond) } ?: ""
    }

    private fun showMoon(palette: Palette, place: Place, now: Instant, zone: ZoneId) {
        findViewById<LinearLayout>(R.id.moon_card).background =
            (getDrawable(R.drawable.card_background)!!.mutate() as GradientDrawable).apply { setColor(palette.cardBackground) }

        val phase = MoonCalculator.phase(now)
        val sizePx = dp(64)
        findViewById<ImageView>(R.id.moon_image).setImageBitmap(
            MoonRenderer.draw(sizePx, phase, place.latitude < 0, dark = Palette.blend(palette.cardBackground, Color.WHITE, 0.08f), lit = MOON_LIT),
        )
        findViewById<TextView>(R.id.moon_illumination).apply {
            text = getString(R.string.percent, Math.round(phase.illumination * 100).toInt())
            setTextColor(palette.text)
        }
        findViewById<TextView>(R.id.moon_phase).apply {
            text = getString(PHASE_NAMES.getValue(phase.name))
            setTextColor(palette.textSecondary)
        }

        val riseSet = MoonCalculator.riseSet(now.atZone(zone).toLocalDate(), place.latitude, place.longitude, zone)
        val parts = listOfNotNull(
            riseSet.rise?.let { getString(R.string.moon_rise, Formatters.time(this, it, zone)) },
            riseSet.set?.let { getString(R.string.moon_set, Formatters.time(this, it, zone)) },
        )
        findViewById<TextView>(R.id.moon_times).apply {
            text = if (parts.isEmpty()) getString(R.string.moon_no_events) else parts.joinToString("   ")
            setTextColor(palette.text)
        }

        val dateFormat = DateTimeFormatter.ofPattern("dd/MM")
        val next = MoonCalculator.nextQuarters(now)
            .filter { it.first == MoonCalculator.Quarter.NEW || it.first == MoonCalculator.Quarter.FULL || it.first == MoonCalculator.Quarter.FIRST_QUARTER }
            .joinToString(" • ") { (quarter, at) ->
                getString(QUARTER_NAMES.getValue(quarter), dateFormat.format(at.atZone(zone)))
            }
        findViewById<TextView>(R.id.moon_next).apply {
            text = next
            setTextColor(palette.textSecondary)
        }
        findViewById<android.view.View>(R.id.moon_divider).setBackgroundColor(Palette.blend(palette.cardBackground, Color.WHITE, 0.08f))
        showMoonWeek(palette, place, now, zone)
    }

    /** The next 7 days on one row: phase icon, illumination at local noon, abbreviated weekday, dd/MM. */
    private fun showMoonWeek(palette: Palette, place: Place, now: Instant, zone: ZoneId) {
        val row = findViewById<LinearLayout>(R.id.moon_week)
        row.removeAllViews()
        val density = resources.displayMetrics.density
        val iconPx = (24 * density).toInt()
        val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM")
        val today = now.atZone(zone).toLocalDate()
        for (offset in 1L..7L) {
            val date = today.plusDays(offset)
            val phase = MoonCalculator.phase(date.atTime(12, 0).atZone(zone).toInstant())
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            cell.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconPx, iconPx)
                setImageBitmap(MoonRenderer.draw(iconPx, phase, place.latitude < 0, Palette.blend(palette.cardBackground, Color.WHITE, 0.08f), MOON_LIT))
            })
            // Labels span the column and centre their text, so they line up under the icon.
            fun label(value: String, sizeSp: Float, color: Int, topPadPx: Int = 0) = TextView(this).apply {
                text = value
                textSize = sizeSp
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                maxLines = 1
                setTextColor(color)
                setPadding(0, topPadPx, 0, 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            cell.addView(label(getString(R.string.percent, Math.round(phase.illumination * 100).toInt()), 13f, palette.text, (6 * density).toInt()))
            cell.addView(label(capitalize(dayFormat.format(date).trimEnd('.')), 11f, palette.textSecondary))
            cell.addView(label(dateFormat.format(date), 10f, palette.textSecondary).apply { alpha = 0.75f })
            row.addView(cell)
        }
    }

    /** Quick switch: favourites plus device location. */
    private fun showPlaces(anchor: android.view.View) {
        val store = LocationStore(this)
        val current = store.current()
        val favorites = FavoritesStore(this).all()
        val menu = PopupMenu(this, anchor)
        favorites.forEachIndexed { i, fav ->
            menu.menu.add(0, i, i, if (fav.matches(current)) "★ ${fav.name}" else fav.name)
        }
        menu.menu.add(0, DEVICE_ITEM, favorites.size, getString(R.string.place_device_menu))
        if (favorites.isEmpty()) menu.menu.add(0, SETTINGS_ITEM, favorites.size + 1, getString(R.string.favorites_empty_menu))
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                DEVICE_ITEM -> {
                    store.save(current.latitude, current.longitude, automatic = true)
                    if (!store.hasLocationPermission()) startActivity(Intent(this, SettingsActivity::class.java))
                }
                SETTINGS_ITEM -> startActivity(Intent(this, SettingsActivity::class.java))
                else -> favorites[item.itemId].let { store.save(it.latitude, it.longitude, automatic = false, name = it.name) }
            }
            SunWidgetProvider.updateAll(this)
            refresh()
            true
        }
        menu.show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun capitalize(s: String) = s.replaceFirstChar { it.titlecase(Locale.getDefault()) }

    companion object {
        private const val REFRESH_MS = 60_000L
        private const val RETRY_AFTER_FAILURE_S = 300L
        /** The widget's own padding on the home screen, replaced on the page by the tile's. */
        private const val WIDGET_PADDING_DP = 14

        /** On the page: 188 dp tall with 8 dp margins, plus the fit-to-screen extra. */
        private const val SUN_TILE_DP = 188
        private const val EMBEDDED_PADDING_V_DP = 8
        private const val MAX_SUN_EXTRA_DP = 40

        /** The countdown row takes this much of the widget height; the curve gets the rest. */
        private const val COUNTDOWN_ROW_DP = 20
        private const val REQUEST_NOTIFICATIONS = 3
        private const val DEVICE_ITEM = 10_000
        private const val SETTINGS_ITEM = 10_001
        private const val MOON_LIT = 0xFFECE6D2.toInt()

        private val PHASE_NAMES = mapOf(
            MoonCalculator.Name.NEW to R.string.moon_new,
            MoonCalculator.Name.WAXING_CRESCENT to R.string.moon_waxing_crescent,
            MoonCalculator.Name.FIRST_QUARTER to R.string.moon_first_quarter,
            MoonCalculator.Name.WAXING_GIBBOUS to R.string.moon_waxing_gibbous,
            MoonCalculator.Name.FULL to R.string.moon_full,
            MoonCalculator.Name.WANING_GIBBOUS to R.string.moon_waning_gibbous,
            MoonCalculator.Name.LAST_QUARTER to R.string.moon_last_quarter,
            MoonCalculator.Name.WANING_CRESCENT to R.string.moon_waning_crescent,
        )
        private val QUARTER_NAMES = mapOf(
            MoonCalculator.Quarter.NEW to R.string.next_new,
            MoonCalculator.Quarter.FIRST_QUARTER to R.string.next_first_quarter,
            MoonCalculator.Quarter.FULL to R.string.next_full,
            MoonCalculator.Quarter.LAST_QUARTER to R.string.next_last_quarter,
        )

        fun phaseName(name: MoonCalculator.Name): Int = PHASE_NAMES.getValue(name)
        fun quarterName(quarter: MoonCalculator.Quarter): Int = QUARTER_NAMES.getValue(quarter)

        /** "Parma", "Device location" or "44.8015, 10.3279". */
        fun placeLabel(activity: Activity, place: Place): String = when {
            place.automatic -> activity.getString(R.string.place_device)
            place.name != null -> place.name
            else -> String.format(Locale.ROOT, "%.4f, %.4f", place.latitude, place.longitude)
        }
    }
}
