package it.sunw.widget.weather

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.TextView
import it.sunw.widget.AppearanceStore
import it.sunw.widget.LocationStore
import it.sunw.widget.MainActivity
import it.sunw.widget.R
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Opened from today's rainspot on the main page: the 7-day meteogram from the cached MeteoBlue
 * forecast (the radar will sit above it). Tapping a day opens [WeatherDayActivity].
 */
class MeteogramActivity : Activity() {

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
}
