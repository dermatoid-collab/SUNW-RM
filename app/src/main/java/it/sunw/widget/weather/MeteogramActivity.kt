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
import android.widget.TextView
import it.sunw.widget.AppearanceStore
import it.sunw.widget.LocationStore
import it.sunw.widget.MainActivity
import it.sunw.widget.Palette
import it.sunw.widget.Place
import it.sunw.widget.R
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

        setUpMap(palette, place, savedInstanceState?.getString(STATE_LAYER)?.let { runCatching { Layer.valueOf(it) }.getOrNull() })

        val forecast = WeatherRepository(this).cached(place)
        if (forecast == null || forecast.hours.isEmpty()) {
            findViewById<View>(R.id.mg_card).visibility = View.GONE
            status.text = getString(R.string.detail_no_data)
            return
        }
        val chart = findViewById<MeteogramView>(R.id.mg_chart)
        chart.show(palette, forecast, forecast.localTime(Instant.now()))
        chart.onDayTap = { date ->
            startActivity(Intent(this, WeatherDayActivity::class.java).putExtra(WeatherDayActivity.EXTRA_DATE, date.toString()))
        }
        // Start a little before the current hour.
        val scroll = findViewById<HorizontalScrollView>(R.id.mg_scroll)
        scroll.post { scroll.scrollTo((chart.nowX() - (40 * resources.displayMetrics.density).toInt()).coerceAtLeast(0), 0) }
        status.text = getString(R.string.weather_source, DateTimeFormatter.ofPattern("HH:mm").format(forecast.fetchedAt.atZone(java.time.ZoneId.systemDefault())))
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
