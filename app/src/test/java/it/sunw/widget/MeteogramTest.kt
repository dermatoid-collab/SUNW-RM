package it.sunw.widget

import android.content.Context
import android.view.View
import android.widget.ImageView
import it.sunw.widget.weather.Forecast
import it.sunw.widget.weather.MeteogramActivity
import it.sunw.widget.weather.MeteogramView
import it.sunw.widget.weather.PastTemperatures
import it.sunw.widget.weather.WeatherCard
import it.sunw.widget.weather.WeatherDayActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Today's rainspot on the main page and the meteogram page it opens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeteogramTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val today = LocalDate.now()

    /** The sample response starting today, with a rainspot on every day. */
    private fun json(): String {
        val root = JSONObject(WeatherTest.sampleJson(today))
        val days = root.getJSONObject("data_day")
        val spot = "0".repeat(24) + "1" + "0".repeat(24)
        days.put("rainspot", JSONArray(List(days.getJSONArray("time").length()) { spot }))
        return root.toString()
    }

    @Before
    fun seedCache() {
        LocationStore(context).save(44.80, 10.33, automatic = false, name = "Parma")
        File(context.cacheDir, "meteoblue.json").writeText(json())
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putLong("fetched", System.currentTimeMillis())
            .putLong("lat", 44.80.toRawBits())
            .putLong("lon", 10.33.toRawBits())
            .commit()
    }

    @Test
    fun todaysRainspotOpensTheMeteogram() {
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val forecast = Forecast.parse(json(), Instant.now())
        WeatherCard(main).show(AppearanceStore(main).palette(), forecast, Instant.now())
        val spot = main.findViewById<ImageView>(R.id.weather_today_spot)
        assertEquals(View.VISIBLE, spot.visibility)
        spot.performClick()
        assertEquals(MeteogramActivity::class.java.name, shadowOf(main).nextStartedActivity.component?.className)
    }

    @Test
    fun withoutARainspotARadarIconTakesItsPlace() {
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val forecast = Forecast.parse(WeatherTest.sampleJson(today), Instant.now())
        WeatherCard(main).show(AppearanceStore(main).palette(), forecast, Instant.now())
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val spot = main.findViewById<ImageView>(R.id.weather_today_spot)
        assertEquals(View.VISIBLE, spot.visibility)
        // The vector radar icon, not a drawn rainspot bitmap.
        assertTrue(spot.drawable != null && spot.drawable !is android.graphics.drawable.BitmapDrawable)
        spot.performClick()
        assertEquals(MeteogramActivity::class.java.name, shadowOf(main).nextStartedActivity.component?.className)
    }

    @Test
    fun meteogramIsWiderThanTheScreenAndOpensDays() {
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        val chart = activity.findViewById<MeteogramView>(R.id.mg_chart)
        chart.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        // 48 sample hours at 6 dp each.
        assertTrue(chart.measuredWidth >= (48 * 6 * activity.resources.displayMetrics.density).toInt())
        assertTrue(chart.measuredHeight > 0)
        chart.onDayTap!!.invoke(today)
        val next = shadowOf(activity).nextStartedActivity
        assertEquals(WeatherDayActivity::class.java.name, next.component?.className)
        assertEquals(today.toString(), next.getStringExtra(WeatherDayActivity.EXTRA_DATE))
    }

    @Test
    fun windyMapStartsOnRadarAndSwitchesToRainForecast() {
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        val map = activity.findViewById<android.webkit.WebView>(R.id.mg_map)
        val first = android.net.Uri.parse(shadowOf(map).lastLoadedUrl)
        assertEquals("embed.windy.com", first.host)
        assertEquals("radar", first.getQueryParameter("overlay"))
        assertEquals("44.800", first.getQueryParameter("lat"))
        assertEquals("km/h", first.getQueryParameter("metricWind"))

        activity.findViewById<View>(R.id.mg_layer_rain).performClick()
        assertEquals(MeteogramActivity.Layer.RAIN, activity.layer)
        assertEquals("rain", android.net.Uri.parse(shadowOf(map).lastLoadedUrl).getQueryParameter("overlay"))
    }

    /** Open-Meteo style response for yesterday and today (local time), 10 °C at midnight rising 0.5 °C an hour. */
    private fun pastJson(): String {
        val times = JSONArray()
        val temps = JSONArray()
        for (d in 0..1) for (h in 0 until 24) {
            times.put(today.minusDays(1L - d).atTime(h, 0).toString().take(16))
            temps.put(10.0 + 0.5 * (d * 24 + h) % 24.0)
        }
        return JSONObject().put("hourly", JSONObject().put("time", times).put("temperature_2m", temps)).toString()
    }

    private fun seedPast() {
        File(context.cacheDir, "openmeteo.json").writeText(pastJson())
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putLong("om_fetched", System.currentTimeMillis())
            .putLong("om_lat", 44.80.toRawBits())
            .putLong("om_lon", 10.33.toRawBits())
            .commit()
    }

    @Test
    fun pastTemperaturesGiveTheExtremesOfTheLast24Hours() {
        val past = PastTemperatures.parse(pastJson())
        val now = today.atTime(14, 5)
        val (min, max) = past.extremes(now)!!
        assertTrue(min.value <= max.value)
        assertTrue(past.last24h(now).size == 25) // from 14:00 yesterday to 14:00 today, both included
        assertTrue(!min.time.isBefore(now.minusHours(25)) && !max.time.isAfter(now))
        assertEquals(null, PastTemperatures.parse("""{"hourly":{"time":[],"temperature_2m":[]}}""").extremes(now))
    }

    @Test
    fun meteogramShowsTheLast24HoursAndTheirExtremes() {
        seedPast()
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        val chart = activity.findViewById<MeteogramView>(R.id.mg_chart)
        assertTrue(chart.leadingSlots in 1..24)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mg_stats).visibility)
        assertTrue(activity.findViewById<android.widget.TextView>(R.id.mg_stats).text.contains("°"))
    }

    @Test
    fun longPressThenDragMovesTheCursor() {
        seedPast()
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        val chart = activity.findViewById<MeteogramView>(R.id.mg_chart)
        val d = activity.resources.displayMetrics.density
        fun event(action: Int, x: Float) = android.view.MotionEvent.obtain(0, 0, action, x, 10f, 0)
        chart.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_DOWN, 60 * d))
        assertEquals(-1, chart.cursorSlot)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1500))
        assertTrue(chart.cursorSlot >= 0)
        val first = chart.cursorSlot
        chart.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_MOVE, 120 * d))
        assertTrue(chart.cursorSlot > first)
        assertTrue(chart.cursorLabel(chart.cursorSlot).second.contains("°"))
        chart.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_UP, 120 * d))
        assertEquals(-1, chart.cursorSlot)
        // No day page was opened by the cursor.
        assertEquals(null, shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun aShortTapStillOpensTheDay() {
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        val chart = activity.findViewById<MeteogramView>(R.id.mg_chart)
        chart.dispatchTouchEvent(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 100f, 10f, 0))
        chart.dispatchTouchEvent(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_UP, 100f, 10f, 0))
        assertEquals(WeatherDayActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }
}
