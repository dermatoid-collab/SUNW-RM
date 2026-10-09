package it.sunw.widget

import android.content.Context
import android.view.View
import android.widget.ImageView
import it.sunw.widget.weather.Forecast
import it.sunw.widget.weather.MeteogramActivity
import it.sunw.widget.weather.MeteogramView
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
}
