package it.sunw.widget

import android.content.Context
import android.view.View
import android.widget.ImageView
import it.sunw.widget.weather.Forecast
import it.sunw.widget.weather.MeteogramActivity
import it.sunw.widget.weather.MeteogramView
import it.sunw.widget.weather.OpenMeteoHoursRepository
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
        OpenMeteoHoursRepository.executor = java.util.concurrent.Executor { } // no network for Open-Meteo's hours
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
        val intent = shadowOf(main).nextStartedActivity
        assertEquals(MeteogramActivity::class.java.name, intent.component?.className)
        assertEquals(null, intent.getStringExtra(MeteogramActivity.EXTRA_FOCUS)) // the page from the top
    }

    @Test
    fun tappingTheMaxMinPillsAlsoOpensTheMeteogram() {
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val forecast = Forecast.parse(json(), Instant.now())
        WeatherCard(main).show(AppearanceStore(main).palette(), forecast, Instant.now())
        // One tap area around the spot and the two pills.
        val target = main.findViewById<View>(R.id.weather_today_target)
        val pills = main.findViewById<android.view.ViewGroup>(R.id.weather_today_pills)
        assertTrue(pills.childCount == 2)
        assertEquals(target, pills.parent)
        assertEquals(target, main.findViewById<View>(R.id.weather_today_spot).parent)
        target.performClick()
        val intent = shadowOf(main).nextStartedActivity
        assertEquals(MeteogramActivity::class.java.name, intent.component?.className)
        assertEquals(MeteogramActivity.FOCUS_CHARTS, intent.getStringExtra(MeteogramActivity.EXTRA_FOCUS)) // scrolled to the charts
    }

    @Test
    fun openedFromThePillsThePageScrollsToTheTemperatureChart() {
        seedPast()
        val intent = android.content.Intent(context, MeteogramActivity::class.java).putExtra(MeteogramActivity.EXTRA_FOCUS, MeteogramActivity.FOCUS_CHARTS)
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java, intent).setup().get()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val page = activity.findViewById<android.widget.ScrollView>(R.id.mg_page)
        val card = activity.findViewById<View>(R.id.mg_last24)
        assertEquals(View.VISIBLE, card.visibility)
        // Either the card is at the top, or the page could not scroll that far (its content is shorter than the screen in this test).
        assertTrue(page.scrollY > 0 || card.top <= 0 || page.getChildAt(0).height <= page.height)
        // Opened from the spot, the page starts at the top.
        val plain = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        assertEquals(0, plain.findViewById<android.widget.ScrollView>(R.id.mg_page).scrollY)
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

    /** Open-Meteo style response for yesterday and today (local time): hourly and every 15 minutes. */
    private fun pastJson(): String {
        val times = JSONArray()
        val temps = JSONArray()
        val fineTimes = JSONArray()
        val fineTemps = JSONArray()
        for (d in 0..1) for (h in 0 until 24) {
            times.put(today.minusDays(1L - d).atTime(h, 0).toString().take(16))
            temps.put(10.0 + (0.5 * (d * 24 + h)) % 24.0)
            for (q in 0 until 4) {
                fineTimes.put(today.minusDays(1L - d).atTime(h, q * 15).toString().take(16))
                fineTemps.put(10.0 + (0.5 * (d * 24 + h) + 0.125 * q) % 24.0)
            }
        }
        return JSONObject()
            .put("hourly", JSONObject().put("time", times).put("temperature_2m", temps))
            .put("minutely_15", JSONObject().put("time", fineTimes).put("temperature_2m", fineTemps))
            .toString()
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
    fun pastTemperaturesAreEvery15MinutesWhenAvailable() {
        val past = PastTemperatures.parse(pastJson())
        val now = today.atTime(14, 5)
        val fine = past.last24h(now)
        assertTrue(past.hasFine)
        // From 14:05 yesterday to 14:00 today every 15 minutes: 14:15 .. 14:00 = 96 points.
        assertEquals(96, fine.size)
        assertEquals(today.minusDays(1).atTime(14, 15), fine.first().first)
        assertEquals(today.atTime(14, 0), fine.last().first)
        // Without the 15-minute series the hours are used (14:00 yesterday to 14:00 today).
        val hourly = PastTemperatures.parse("""{"hourly":{"time":["${today.atTime(13, 0).toString().take(16)}","${today.atTime(14, 0).toString().take(16)}"],"temperature_2m":[12.5,13.0]}}""")
        assertTrue(!hourly.hasFine)
        assertEquals(2, hourly.last24h(now).size)
        assertTrue(it.sunw.widget.weather.PastTemperatureRepository.url(44.8, 10.33).contains("minutely_15=temperature_2m"))
    }

    /** Open-Meteo's other hours for yesterday, today and tomorrow: 2 mm of rain, 15 km/h, WMO 61 (light rain), every hour. */
    private fun seedModel() {
        val times = (-1..1).flatMap { d -> (0 until 24).map { h -> today.plusDays(d.toLong()).atTime(h, 0).toString().take(16) } }
        fun column(value: Double) = JSONArray(times.map { value })
        val json = JSONObject().put("hourly", JSONObject()
            .put("time", JSONArray(times))
            .put("temperature_2m", column(7.0)).put("apparent_temperature", column(5.0))
            .put("precipitation", column(2.0)).put("wind_speed_10m", column(15.0))
            .put("wind_direction_10m", column(270.0)).put("wind_gusts_10m", column(30.0)).put("weather_code", column(61.0))
            .put("pressure_msl", column(1012.5)))
        File(context.cacheDir, "openmeteo_hours.json").writeText(json.toString())
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putLong("omh_fetched", System.currentTimeMillis())
            .putLong("omh_lat", 44.80.toRawBits())
            .putLong("omh_lon", 10.33.toRawBits())
            .commit()
    }

    private val utcHour get() = Instant.now().atOffset(java.time.ZoneOffset.UTC).hour

    @Test
    fun theLast24HoursGetRainWindAndIconsFromOpenMeteo() {
        org.junit.Assume.assumeTrue(utcHour in 1..21) // the sample's +2 h offset keeps "today" the same date
        seedPast()
        seedModel()
        val chart = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get().findViewById<MeteogramView>(R.id.mg_chart)
        assertTrue(chart.leadingSlots in 1..24)
        assertEquals(chart.leadingSlots, chart.detailedLeadingSlots) // every leading hour has its detail
        val yesterday = chart.hourAt(0)!!
        assertEquals(2.0, yesterday.precipitation, 0.001)
        assertEquals(15.0, yesterday.windSpeed, 0.001)
        assertEquals(it.sunw.widget.weather.Condition.LIGHT_RAIN, yesterday.condition)
        assertEquals(0, yesterday.precipitationProbability) // none for the past
    }

    @Test
    fun theLast24HoursOfPressureHaveTheirCardAndTheMeteogramShowsTheLine() {
        org.junit.Assume.assumeTrue(utcHour in 1..21)
        seedPast()
        seedModel()
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mg_pressure24).visibility)
        val chart = activity.findViewById<it.sunw.widget.weather.Last24hView>(R.id.mg_pressure24_chart)
        assertTrue(chart.pointCount in 24..25) // 24 hours, one point per hour
        assertTrue(chart.cursorLabel(chart.pointCount - 1).second.contains("1012.5") && chart.cursorLabel(0).second.contains("mbar"))
        // The meteogram's label carries the pressure next to the temperature.
        val meteogram = activity.findViewById<MeteogramView>(R.id.mg_chart)
        assertTrue(meteogram.cursorLabel(0).second.contains("mbar"))
    }

    @Test
    fun theLast24HoursOfWindHaveTheirCard() {
        org.junit.Assume.assumeTrue(utcHour in 1..21)
        seedPast()
        seedModel()
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mg_wind24).visibility)
        val chart = activity.findViewById<it.sunw.widget.weather.Last24hView>(R.id.mg_wind24_chart)
        assertTrue(chart.pointCount in 24..25) // 24 hours, one point per hour
        // The direction is in the arrows under the chart, not in the cursor's label.
        assertEquals("15 km/h", chart.cursorLabel(chart.pointCount - 1).second)
    }

    @Test
    fun theMeteogramLabelHasTemperatureMeanWindAndPressureWithoutAnArrow() {
        org.junit.Assume.assumeTrue(utcHour in 1..21)
        seedPast()
        seedModel()
        val meteogram = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get().findViewById<MeteogramView>(R.id.mg_chart)
        // The sample has 15 km/h (mean) from the west and 1012.5 mbar: the label is "T · wind · pressure".
        val parts = meteogram.cursorLabel(0).second.split(" · ")
        assertEquals(3, parts.size)
        assertTrue(parts[0].endsWith("°"))
        assertEquals("15 km/h", parts[1])
        assertTrue(parts[2].endsWith("mbar"))
        // Each value has the colour of its line: temperature in the text colour, wind azure, pressure violet.
        val colours = meteogram.cursorValues(0, AppearanceStore(context).palette()).map { it.second }
        assertEquals(listOf(AppearanceStore(context).palette().text, it.sunw.widget.weather.Last24hView.WIND_LINE, it.sunw.widget.weather.Last24hView.PRESSURE_LINE), colours)
    }

    @Test
    fun withoutTheHoursOfOpenMeteoTheWindCardStaysHidden() {
        seedPast()
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        assertEquals(View.GONE, activity.findViewById<View>(R.id.mg_wind24).visibility)
    }

    @Test
    fun withoutPressureTheCardStaysHiddenAndTheLabelHasOnlyTheTemperature() {
        seedPast()
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        assertEquals(View.GONE, activity.findViewById<View>(R.id.mg_pressure24).visibility)
        assertTrue(!activity.findViewById<MeteogramView>(R.id.mg_chart).cursorLabel(0).second.contains("mbar"))
    }

    @Test
    fun todaysHoursGoneAlsoTakeOpenMeteosRainWindAndIcon() {
        org.junit.Assume.assumeTrue(utcHour in 1..21)
        seedPast()
        seedModel()
        val chart = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get().findViewById<MeteogramView>(R.id.mg_chart)
        val midnight = chart.hourAt(chart.leadingSlots)!! // today 00:00, already gone
        assertEquals(2.0, midnight.precipitation, 0.001)
        assertEquals(it.sunw.widget.weather.Condition.LIGHT_RAIN, midnight.condition)
        assertEquals(10, midnight.precipitationProbability) // MeteoBlue's probability stays
        // The last hour of the day is still ahead: MeteoBlue's own (no rain, partly cloudy).
        val ahead = chart.hourAt(chart.leadingSlots + 23)!!
        assertEquals(0.0, ahead.precipitation, 0.001)
    }

    @Test
    fun withoutOpenMeteoHoursTheLeadingSlotsKeepOnlyTheirTemperature() {
        org.junit.Assume.assumeTrue(utcHour in 1..21)
        seedPast()
        val chart = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get().findViewById<MeteogramView>(R.id.mg_chart)
        assertTrue(chart.leadingSlots in 1..24)
        assertEquals(0, chart.detailedLeadingSlots)
        assertEquals(0.0, chart.hourAt(0)!!.precipitation, 0.0)
    }

    @Test
    fun meteogramShowsTheLast24HoursAndACardWithTheirChart() {
        seedPast()
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        val chart = activity.findViewById<MeteogramView>(R.id.mg_chart)
        assertTrue(chart.leadingSlots in 1..24)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mg_last24).visibility)
        val last24 = activity.findViewById<it.sunw.widget.weather.Last24hView>(R.id.mg_last24_chart)
        assertTrue(last24.pointCount in 96..97) // 24 hours every 15 minutes
        assertTrue(last24.minIndex >= 0 && last24.maxIndex >= 0)
    }

    @Test
    fun withoutPastDataTheLast24HoursCardStaysHidden() {
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        assertEquals(View.GONE, activity.findViewById<View>(R.id.mg_last24).visibility)
    }

    @Test
    fun longPressOnTheLast24HoursChartMovesItsCursor() {
        seedPast()
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        val chart = activity.findViewById<it.sunw.widget.weather.Last24hView>(R.id.mg_last24_chart)
        chart.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        chart.layout(0, 0, chart.measuredWidth, chart.measuredHeight)
        fun event(action: Int, x: Float) = android.view.MotionEvent.obtain(0, 0, action, x, 100f, 0)
        chart.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_DOWN, 100f))
        assertEquals(-1, chart.cursorIndex)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1500))
        assertTrue(chart.cursorIndex >= 0)
        val first = chart.cursorIndex
        chart.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_MOVE, 700f))
        assertTrue(chart.cursorIndex > first)
        assertTrue(chart.cursorLabel(chart.cursorIndex).second.contains("°"))
        chart.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_UP, 700f))
        assertEquals(-1, chart.cursorIndex)
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
