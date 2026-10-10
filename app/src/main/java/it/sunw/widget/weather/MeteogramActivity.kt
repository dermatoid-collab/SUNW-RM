package it.sunw.widget.weather

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import it.sunw.widget.AppearanceStore
import it.sunw.widget.LocationStore
import it.sunw.widget.MainActivity
import it.sunw.widget.Palette
import it.sunw.widget.Place
import it.sunw.widget.R
import it.sunw.widget.webcams.ImageLoader
import it.sunw.widget.webcams.WebcamViewActivity
import it.sunw.widget.webcams.WebcamsActivity
import it.sunw.widget.webcams.WindyWebcams
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Opened from today's rainspot on the main page: the Windy map (radar of the last hours or rain
 * forecast, with Windy's own timeline, play and zoom) and the 7-day meteogram from the cached
 * MeteoBlue forecast. Tapping a day of the meteogram opens [WeatherDayActivity].
 */
class MeteogramActivity : Activity() {

    enum class Layer { RADAR, RAIN }

    internal var layer = Layer.RADAR
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meteogram)
        val palette = AppearanceStore(this).palette()
        window.decorView.setBackgroundColor(palette.pageBackground)
        window.statusBarColor = palette.pageBackground
        window.navigationBarColor = palette.pageBackground

        val place = LocationStore(this).current()
        findViewById<ImageButton>(R.id.mg_back).apply {
            setColorFilter(palette.text)
            setOnClickListener { finish() }
        }
        findViewById<TextView>(R.id.mg_title).setTextColor(palette.text)
        findViewById<TextView>(R.id.mg_subtitle).apply {
            text = getString(R.string.mg_subtitle, MainActivity.placeLabel(this@MeteogramActivity, place))
            setTextColor(palette.textSecondary)
        }
        findViewById<View>(R.id.mg_card).background =
            (getDrawable(R.drawable.card_background)!!.mutate() as GradientDrawable).apply { setColor(palette.cardBackground) }
        findViewById<TextView>(R.id.mg_legend).setTextColor(palette.textSecondary)
        val status = findViewById<TextView>(R.id.mg_status).apply { setTextColor(palette.textSecondary) }

        setUpWebcams(palette, place)
        setUpMap(palette, place, savedInstanceState?.getString(STATE_LAYER)?.let { runCatching { Layer.valueOf(it) }.getOrNull() })

        val forecast = WeatherRepository(this).cached(place)
        if (forecast == null || forecast.hours.isEmpty()) {
            findViewById<View>(R.id.mg_card).visibility = View.GONE
            status.text = getString(R.string.detail_no_data)
            return
        }
        val chart = findViewById<MeteogramView>(R.id.mg_chart)
        val pastRepo = PastTemperatureRepository(this)
        val now = forecast.localTime(Instant.now())
        val updated = DateTimeFormatter.ofPattern("HH:mm").format(forecast.fetchedAt.atZone(java.time.ZoneId.systemDefault()))

        // The last 24 hours (Open-Meteo) sit to the left of "now"; they arrive after the first draw when not cached.
        fun draw(past: PastTemperatures?) {
            chart.show(palette, forecast, now, past)
            showLast24(palette, forecast, past, now)
            status.text = getString(if (past == null) R.string.weather_source else R.string.weather_source_past, updated)
        }
        draw(pastRepo.cached(place))
        if (!pastRepo.isFresh() || pastRepo.cached(place)?.hasFine != true) {
            pastRepo.refresh(place) { result ->
                result.getOrNull()?.let { fresh -> runOnUiThread { if (!isDestroyed) draw(fresh) } }
            }
        }
        chart.onDayTap = { date ->
            startActivity(Intent(this, WeatherDayActivity::class.java).putExtra(WeatherDayActivity.EXTRA_DATE, date.toString()))
        }
        // Start a little before the current hour.
        val scroll = findViewById<HorizontalScrollView>(R.id.mg_scroll)
        scroll.post { scroll.scrollTo((chart.nowX() - (40 * resources.displayMetrics.density).toInt()).coerceAtLeast(0), 0) }
    }

    /** The 15-minute chart of the last 24 hours; the card stays hidden without data. */
    private fun showLast24(palette: Palette, forecast: Forecast, past: PastTemperatures?, now: java.time.LocalDateTime) {
        val card = findViewById<View>(R.id.mg_last24)
        val points = past?.last24h(now).orEmpty()
        if (points.size < 2) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        card.background = (getDrawable(R.drawable.card_background)!!.mutate() as GradientDrawable).apply { setColor(palette.cardBackground) }
        findViewById<TextView>(R.id.mg_last24_title).setTextColor(palette.text)
        findViewById<TextView>(R.id.mg_last24_step).apply {
            setTextColor(palette.textSecondary)
            text = getString(if (past!!.hasFine) R.string.l24_step_15 else R.string.l24_step_hour)
        }
        findViewById<TextView>(R.id.mg_last24_legend).setTextColor(palette.textSecondary)
        // Day and night as the forecast has them, by hour of the day.
        val daylight = forecast.hours.take(24).associate { it.time.hour to it.isDaylight }
        findViewById<Last24hView>(R.id.mg_last24_chart).show(palette, points) { daylight[it.hour] ?: (it.hour in 7..18) }
    }

    /** Four previews of the nearest webcams and the button to the full page. */
    private fun setUpWebcams(palette: Palette, place: Place) {
        findViewById<View>(R.id.mg_webcams).background =
            (getDrawable(R.drawable.card_background)!!.mutate() as GradientDrawable).apply { setColor(palette.cardBackground) }
        findViewById<TextView>(R.id.mg_webcams_title).setTextColor(palette.text)
        val note = findViewById<TextView>(R.id.mg_webcams_note).apply { setTextColor(palette.textSecondary) }
        findViewById<TextView>(R.id.mg_webcams_all).apply {
            setTextColor(palette.text)
            background = GradientDrawable().apply {
                cornerRadius = 14 * resources.displayMetrics.density
                setColor(Palette.blend(palette.cardBackground, palette.accent, 0.22f))
            }
            setOnClickListener { startActivity(Intent(this@MeteogramActivity, WebcamsActivity::class.java)) }
        }
        val windy = WindyWebcams()
        if (!windy.hasKey) {
            note.text = getString(R.string.wc_section_none)
            return
        }
        val row = findViewById<LinearLayout>(R.id.mg_webcams_row)
        windy.nearby(place) { result ->
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val cams = result.getOrNull()?.take(4).orEmpty()
                if (cams.isEmpty()) {
                    note.text = getString(R.string.wc_section_none)
                    return@runOnUiThread
                }
                note.text = getString(R.string.wc_section_note)
                val d = resources.displayMetrics.density
                for (cam in cams) {
                    row.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        tag = cam.id
                        addView(android.widget.ImageView(context).apply {
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            background = GradientDrawable().apply { cornerRadius = 10 * d; setColor(Palette.blend(palette.cardBackground, android.graphics.Color.WHITE, 0.08f)) }
                            clipToOutline = true
                            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                            cam.imageUrl?.let { url -> ImageLoader.load(url, 480) { b -> if (b != null) setImageBitmap(b) } }
                        }, LinearLayout.LayoutParams(-1, (52 * d).toInt()))
                        addView(TextView(context).apply {
                            text = cam.title
                            textSize = 10.5f
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setTextColor(palette.textSecondary)
                            setPadding(0, (4 * d).toInt(), 0, 0)
                        })
                        setOnClickListener {
                            startActivity(Intent(context, WebcamViewActivity::class.java)
                                .putExtra(WebcamViewActivity.EXTRA_TITLE, cam.title)
                                .putExtra(WebcamViewActivity.EXTRA_WINDY_ID, cam.id)
                                .putExtra(WebcamViewActivity.EXTRA_IMAGE, cam.imageUrl)
                                .putExtra(WebcamViewActivity.EXTRA_PAGE, cam.pageUrl))
                        }
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (8 * d).toInt() })
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_LAYER, layer.name)
    }

    override fun onDestroy() {
        findViewById<WebView>(R.id.mg_map)?.destroy()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setUpMap(palette: Palette, place: Place, saved: Layer?) {
        findViewById<View>(R.id.mg_radar_card).background =
            (getDrawable(R.drawable.card_background)!!.mutate() as GradientDrawable).apply { setColor(palette.cardBackground) }
        findViewById<TextView>(R.id.mg_map_error).setTextColor(palette.textSecondary)
        val map = findViewById<WebView>(R.id.mg_map)
        map.setBackgroundColor(palette.cardBackground)
        map.settings.javaScriptEnabled = true
        map.settings.domStorageEnabled = true
        map.webViewClient = object : WebViewClient() {
            // The map stays in the page; any other link (e.g. the Windy logo) opens the browser.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == WINDY_EMBED_HOST) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) findViewById<View>(R.id.mg_map_error).visibility = View.VISIBLE
            }
        }
        // Pinch and pan move the map, not the page.
        map.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        val radar = findViewById<TextView>(R.id.mg_layer_radar)
        val rain = findViewById<TextView>(R.id.mg_layer_rain)
        fun show(l: Layer) {
            layer = l
            for ((view, own) in listOf(radar to Layer.RADAR, rain to Layer.RAIN)) {
                val on = own == l
                view.setTextColor(if (on) palette.text else palette.textSecondary)
                view.background = GradientDrawable().apply {
                    cornerRadius = 14 * resources.displayMetrics.density
                    setColor(if (on) Palette.blend(palette.cardBackground, palette.accent, 0.22f) else android.graphics.Color.TRANSPARENT)
                }
            }
            findViewById<View>(R.id.mg_map_error).visibility = View.GONE
            map.loadUrl(windyUrl(place.latitude, place.longitude, l))
        }
        radar.setOnClickListener { if (layer != Layer.RADAR) show(Layer.RADAR) }
        rain.setOnClickListener { if (layer != Layer.RAIN) show(Layer.RAIN) }
        show(saved ?: Layer.RADAR)
    }

    companion object {
        private const val STATE_LAYER = "layer"
        private const val WINDY_EMBED_HOST = "embed.windy.com"

        /**
         * Windy's embeddable map centred on the place, with a marker, km/h and °C: the radar
         * layer (recent hours) or the rain forecast (ECMWF), starting from now.
         */
        fun windyUrl(latitude: Double, longitude: Double, layer: Layer): String {
            val lat = String.format(Locale.ROOT, "%.3f", latitude)
            val lon = String.format(Locale.ROOT, "%.3f", longitude)
            val (overlay, product) = when (layer) {
                Layer.RADAR -> "radar" to "radar"
                Layer.RAIN -> "rain" to "ecmwf"
            }
            return Uri.Builder().scheme("https").authority(WINDY_EMBED_HOST).path("embed2.html")
                .appendQueryParameter("lat", lat)
                .appendQueryParameter("lon", lon)
                .appendQueryParameter("detailLat", lat)
                .appendQueryParameter("detailLon", lon)
                .appendQueryParameter("zoom", if (layer == Layer.RADAR) "8" else "7")
                .appendQueryParameter("level", "surface")
                .appendQueryParameter("overlay", overlay)
                .appendQueryParameter("product", product)
                .appendQueryParameter("menu", "")
                .appendQueryParameter("message", "true")
                .appendQueryParameter("marker", "true")
                .appendQueryParameter("calendar", "now")
                .appendQueryParameter("pressure", "")
                .appendQueryParameter("type", "map")
                .appendQueryParameter("location", "coordinates")
                .appendQueryParameter("detail", "")
                .appendQueryParameter("metricWind", "km/h")
                .appendQueryParameter("metricTemp", "°C")
                .appendQueryParameter("radarRange", "-1")
                .build().toString()
        }
    }
}
