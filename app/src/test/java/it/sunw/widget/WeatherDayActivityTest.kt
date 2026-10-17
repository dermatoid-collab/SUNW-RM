package it.sunw.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import it.sunw.widget.weather.Condition
import it.sunw.widget.weather.DayChartView
import it.sunw.widget.weather.OpenMeteoHoursRepository
import it.sunw.widget.weather.WeatherDayActivity
import it.sunw.widget.weather.WeatherIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import java.time.ZoneOffset

/** The day detail page, fed from a cached MeteoBlue response (no network, no key). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherDayActivityTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun seedCache() {
        OpenMeteoHoursRepository.executor = java.util.concurrent.Executor { } // no network in the tests
        LocationStore(context).save(44.80, 10.33, automatic = false, name = "Parma")
        File(context.cacheDir, "meteoblue.json").writeText(WeatherTest.sampleJson())
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putLong("fetched", System.currentTimeMillis())
            .putLong("lat", 44.80.toRawBits())
            .putLong("lon", 10.33.toRawBits())
            .commit()
    }

    private fun open(date: String) = Robolectric.buildActivity(
        WeatherDayActivity::class.java,
        Intent(context, WeatherDayActivity::class.java).putExtra(WeatherDayActivity.EXTRA_DATE, date),
    ).setup().get()

    @Test
    fun everyConditionHasAnIconByDayAndByNight() {
        for (condition in Condition.values()) for (night in listOf(false, true)) {
            assertNotNull("$condition night=$night", context.getDrawable(WeatherIcons.icon(condition, night)))
        }
    }

    @Test
    fun showsTabsHeroAndHours() {
        val activity = open("2026-10-09")
        assertEquals(3, activity.findViewById<LinearLayout>(R.id.day_tabs).childCount)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.day_hero).visibility)
        val hours = activity.findViewById<LinearLayout>(R.id.day_hours)
        assertTrue("hour rows: ${hours.childCount}", hours.childCount > 1)
        val info = activity.findViewById<TextView>(R.id.day_info).text.toString()
        assertTrue(info, info.lines().size >= 4) // sun, moon, rain, wind
    }

    /** The sample response with a rainSPOT on every day and every hour. */
    private fun seedRainspots() {
        val root = org.json.JSONObject(WeatherTest.sampleJson())
        val spot = "0".repeat(24) + "1" + "0".repeat(24)
        for (key in listOf("data_1h", "data_day")) {
            val data = root.getJSONObject(key)
            data.put("rainspot", org.json.JSONArray(List(data.getJSONArray("time").length()) { spot }))
        }
        File(context.cacheDir, "meteoblue.json").writeText(root.toString())
    }

    private fun assertOpensRadar(activity: WeatherDayActivity, view: View) {
        view.performClick()
        assertEquals(it.sunw.widget.weather.MeteogramActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun maxMinPillsAndEveryRainspotOpenTheRadarPage() {
        seedRainspots()
        val activity = open("2026-10-09")
        assertOpensRadar(activity, activity.findViewById(R.id.day_pills))
        assertOpensRadar(activity, activity.findViewById(R.id.day_rainspot_box))
        // Every hour row's rainSPOT is the last view of its row.
        val hours = activity.findViewById<LinearLayout>(R.id.day_hours)
        val spots = (0 until hours.childCount).map { hours.getChildAt(it) }.filterIsInstance<LinearLayout>()
            .map { it.getChildAt(it.childCount - 1) }.filter { it.hasOnClickListeners() }
        assertTrue("rows with a rainSPOT: ${spots.size}", spots.size > 1)
        spots.forEach { assertOpensRadar(activity, it) }
    }

    /** The sample response with MeteoBlue's predictability per day. */
    private fun seedPredictability(vararg values: Int) {
        val root = org.json.JSONObject(WeatherTest.sampleJson())
        root.getJSONObject("data_day").put("predictability", org.json.JSONArray(values.toList()))
        File(context.cacheDir, "meteoblue.json").writeText(root.toString())
    }

    /**
     * Open-Meteo's hours as the repository would have left them in its cache, for every hour of [first] and
     * the day after: [temperature] and the rest are the same for every hour so that they can be told apart
     * from the MeteoBlue sample's (15–26° rising with the hour, 9 km/h wind, no rain, partly cloudy).
     */
    private fun seedModelHours(first: java.time.LocalDate = java.time.LocalDate.of(2026, 10, 8), temperature: Double = 3.0, gust: Double = 36.0) {
        val times = (0 until 48).map { first.atStartOfDay().plusHours(it.toLong()).toString().take(16) }
        fun column(value: Double) = org.json.JSONArray(times.map { value })
        val json = org.json.JSONObject().put("hourly", org.json.JSONObject()
            .put("time", org.json.JSONArray(times))
            .put("temperature_2m", column(temperature))
            .put("apparent_temperature", column(temperature - 2))
            .put("precipitation", column(1.4))
            .put("wind_speed_10m", column(21.0))
            .put("wind_direction_10m", column(90.0))
            .put("wind_gusts_10m", column(gust))
            .put("weather_code", column(65.0)))
        File(context.cacheDir, "openmeteo_hours.json").writeText(json.toString())
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putLong("omh_fetched", System.currentTimeMillis())
            .putLong("omh_lat", 44.80.toRawBits())
            .putLong("omh_lon", 10.33.toRawBits())
            .commit()
    }

    private val today get() = Instant.now().atOffset(ZoneOffset.ofHours(2)).toLocalDate() // the sample's UTC offset is +2 h
    private val nowHour get() = Instant.now().atOffset(ZoneOffset.ofHours(2)).hour

    @Test
    fun theChartCardSitsBetweenTheInfoTileAndTheHourlyRows() {
        val activity = open("2026-10-09")
        val content = activity.findViewById<LinearLayout>(R.id.day_content)
        val info = content.indexOfChild(activity.findViewById(R.id.day_info_card))
        val chart = content.indexOfChild(activity.findViewById(R.id.day_chart_card))
        val hourly = content.indexOfChild(activity.findViewById(R.id.day_hourly_card))
        assertTrue("info=$info chart=$chart hourly=$hourly", info in 0 until chart && chart == hourly - 1)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.day_chart_card).visibility)
        assertEquals(24, activity.findViewById<DayChartView>(R.id.day_chart).hourCount)
    }

    @Test
    fun reliabilityShowsADotWithTheWrittenPercentage() {
        seedPredictability(78, 55, 91)
        val activity = open("2026-10-08")
        val label = activity.findViewById<TextView>(R.id.day_reliability)
        assertEquals(View.VISIBLE, label.visibility)
        assertEquals("● 78 %", label.text.toString())
        activity.showDay(1)
        assertEquals("● 55 %", label.text.toString())
    }

    @Test
    fun withoutPredictabilityTheDotIsHidden() {
        val activity = open("2026-10-09")
        assertEquals(View.GONE, activity.findViewById<View>(R.id.day_reliability).visibility)
    }

    @Test
    fun theCursorReadsTheValuesOfTheHour() {
        seedModelHours()
        val activity = open("2026-10-09")
        val chart = activity.findViewById<DayChartView>(R.id.day_chart)
        val label = chart.cursorLabel(14)!!
        assertTrue(label.first, label.first.contains("14:00"))
        assertTrue(label.second, label.second.contains("°") && label.second.contains("%") && label.second.contains("mm"))
        assertTrue(label.third, label.third.contains("km/h") && label.third.contains("36"))
    }

    @Test
    fun withoutGustsTheCursorShowsTheMeanWindOnly() {
        val activity = open("2026-10-09")
        val label = activity.findViewById<DayChartView>(R.id.day_chart).cursorLabel(14)!!
        assertTrue(label.third, label.third.contains("km/h") && !label.third.contains("gust"))
    }

    @Test
    fun longPressOnTheDayChartMovesItsCursor() {
        val activity = open("2026-10-09")
        val chart = activity.findViewById<DayChartView>(R.id.day_chart)
        chart.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        chart.layout(0, 0, chart.measuredWidth, chart.measuredHeight)
        fun event(action: Int, x: Float) = android.view.MotionEvent.obtain(0, 0, action, x, 100f, 0)
        chart.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_DOWN, 100f))
        assertEquals(-1, chart.cursorIndex)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1500))
        assertTrue(chart.cursorIndex >= 0)
        val first = chart.cursorIndex
        chart.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_MOVE, 700f))
        assertTrue(chart.cursorIndex > first)
        chart.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_UP, 700f))
        assertEquals(-1, chart.cursorIndex)
    }

    @Test
    fun theTilesSitCloserTogether() {
        val activity = open("2026-10-09")
        val d = activity.resources.displayMetrics.density
        for (id in listOf(R.id.day_hero, R.id.day_info_card, R.id.day_chart_card, R.id.day_hourly_card)) {
            val gap = (activity.findViewById<View>(id).layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin
            assertTrue("tile gap $gap", gap <= (6 * d).toInt())
        }
    }

    @Test
    fun stepToggleShowsEveryThreeHours() {
        val activity = open("2026-10-09")
        val hours = activity.findViewById<LinearLayout>(R.id.day_hours)
        val hourly = hours.childCount
        activity.findViewById<TextView>(R.id.day_step).performClick()
        assertTrue("1h=$hourly 3h=${hours.childCount}", hours.childCount < hourly)
    }

    @Test
    fun tappingATabSwitchesDay() {
        val activity = open("2026-10-08")
        val title = activity.findViewById<TextView>(R.id.day_title)
        val before = title.text.toString()
        (activity.findViewById<LinearLayout>(R.id.day_tabs).getChildAt(1)).performClick()
        assertTrue(before != title.text.toString())
    }

    @Test
    fun swipeMovesBetweenDaysAndStopsAtTheEnds() {
        val activity = open("2026-10-08")
        val title = activity.findViewById<TextView>(R.id.day_title)
        val first = title.text.toString()
        assertTrue(!activity.showDay(-1))          // no day before the first
        assertTrue(activity.showDay(1))            // swipe left → next day
        assertTrue(first != title.text.toString())
        assertTrue(activity.showDay(1))
        assertTrue(!activity.showDay(1))           // last day
    }

    /** A horizontal drag of [dxDp] across the day's header card (negative: to the left), fast enough to count as a flick. */
    private fun drag(activity: WeatherDayActivity, dxDp: Float) {
        val r = android.graphics.Rect().also { activity.findViewById<android.view.View>(R.id.day_hero).getGlobalVisibleRect(it) }
        val y = r.exactCenterY()
        val density = activity.resources.displayMetrics.density
        val x0 = r.exactCenterX() - dxDp / 2 * density
        fun event(action: Int, x: Float, t: Long) = android.view.MotionEvent.obtain(0, t, action, x, y, 0)
        activity.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_DOWN, x0, 0))
        activity.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_MOVE, x0 + dxDp / 2 * density, 40))
        activity.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_UP, x0 + dxDp * density, 80))
    }

    @Test
    fun draggingAcrossThePageMovesToTheNextAndPreviousDay() {
        val activity = open("2026-10-08")
        val title = activity.findViewById<TextView>(R.id.day_title)
        val first = title.text.toString()
        drag(activity, -160f) // to the left: next day
        assertTrue(first != title.text.toString())
        drag(activity, 160f)  // and back
        assertEquals(first, title.text.toString())
        drag(activity, 160f)  // nothing before the first day
        assertEquals(first, title.text.toString())
    }

    @Test
    fun hourRowsShowAllHoursOfTheDay() {
        val activity = open("2026-10-09")
        val hours = activity.findViewById<LinearLayout>(R.id.day_hours)
        // 24 rows plus 23 dividers for a full day in 1 h steps.
        assertEquals(47, hours.childCount)
    }

    @Test
    fun todayOpensOnTheCurrentHourOtherDaysAtTheTop() {
        // The sample's UTC offset is +2 h: "today" is the forecast's local date.
        val today = Instant.now().atOffset(ZoneOffset.ofHours(2)).toLocalDate()
        File(context.cacheDir, "meteoblue.json").writeText(WeatherTest.sampleJson(today))
        val activity = open(today.toString())
        val page = activity.findViewById<ScrollView>(R.id.day_page)
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(activity.focusedRowOffset())
        assertTrue("today scrollY=${page.scrollY}", page.scrollY > 0)

        assertTrue(activity.showDay(1))           // tomorrow: no current hour, top of the page
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(null, activity.focusedRowOffset())
        assertEquals(0, page.scrollY)

        assertTrue(activity.showDay(-1))          // back to today: current hour again
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("back to today scrollY=${page.scrollY}", page.scrollY > 0)
    }
    @Test
    fun theCurveJoinsAcrossAnHourTheClockChangeSkips() {
        assertTrue(DayChartView.connects(1, 2))
        assertTrue(DayChartView.connects(1, 3)) // 02:00 does not exist on the day clocks go forward
        assertTrue(!DayChartView.connects(1, 4)) // a longer gap in the data stays a gap
        assertTrue(!DayChartView.connects(3, 3))
    }

    @Test
    fun aDayWithoutTwoOClockStillShowsItsOtherHours() {
        val root = org.json.JSONObject(WeatherTest.sampleJson())
        val hourly = root.getJSONObject("data_1h")
        val skipped = hourly.getJSONArray("time").let { times -> (0 until times.length()).first { times.getString(it) == "2026-10-09 02:00" } }
        for (key in hourly.keys().asSequence().toList()) {
            val old = hourly.getJSONArray(key)
            hourly.put(key, org.json.JSONArray((0 until old.length()).filter { it != skipped }.map { old.get(it) }))
        }
        File(context.cacheDir, "meteoblue.json").writeText(root.toString())
        val chart = open("2026-10-09").findViewById<DayChartView>(R.id.day_chart)
        assertEquals(23, chart.hourCount)
        assertEquals(null, chart.cursorLabel(2))
        assertTrue(chart.cursorLabel(1) != null && chart.cursorLabel(3) != null)
    }

    private fun openToday(): WeatherDayActivity {
        File(context.cacheDir, "meteoblue.json").writeText(WeatherTest.sampleJson(today))
        seedModelHours(first = today)
        return open(today.toString())
    }

    private fun rowPill(activity: WeatherDayActivity, row: Int): String {
        val hours = activity.findViewById<LinearLayout>(R.id.day_hours)
        val first = (hours.getChildAt(row * 2) as LinearLayout).getChildAt(0) as LinearLayout
        return (first.getChildAt(1) as TextView).text.toString()
    }

    @Test
    fun theHoursGoneTodayTakeOpenMeteosValuesAndTheRestStaysMeteoBlues() {
        org.junit.Assume.assumeTrue(nowHour in 1..22) // needs a past hour and a future one
        val activity = openToday()
        val chart = activity.findViewById<DayChartView>(R.id.day_chart)
        // Hour 0 is gone: Open-Meteo's 3°, feels 1°, 1.4 mm and 21 km/h with its gust.
        val past = chart.cursorLabel(0)!!
        assertTrue(past.second, past.second.startsWith("3° · ") && past.second.contains("1°") && past.second.contains("1.4 mm"))
        assertTrue(past.third, past.third.contains("21 km/h") && past.third.contains("36"))
        assertTrue(past.first, past.first.endsWith(activity.getString(R.string.mg_cursor_past)))
        // Hour 23 is ahead: MeteoBlue's own values (26°, 9 km/h, no rain).
        val future = chart.cursorLabel(23)!!
        assertTrue(future.second, future.second.startsWith("26° · ") && future.second.contains("0 mm"))
        assertTrue(future.third, future.third.contains("9 km/h"))
        assertTrue(future.first, future.first.endsWith(activity.getString(R.string.mg_cursor_forecast)))
        // The rain probability is MeteoBlue's for every hour (Open-Meteo has none for the past).
        assertTrue(past.second, past.second.endsWith("10 %"))
    }

    @Test
    fun theHourRowsFollowTheSameRuleAndTheGoneOnesAreDimmed() {
        org.junit.Assume.assumeTrue(nowHour in 1..22)
        val activity = openToday()
        assertEquals("3°", rowPill(activity, 0)) // hour 0, gone
        assertEquals("26°", rowPill(activity, 23)) // hour 23, ahead
        val hours = activity.findViewById<LinearLayout>(R.id.day_hours)
        assertTrue("past row alpha", hours.getChildAt(0).alpha < 1f)
        assertEquals(1f, hours.getChildAt(23 * 2).alpha, 0f)
        assertEquals(nowHour, activity.findViewById<DayChartView>(R.id.day_chart).pastHours)
    }

    @Test
    fun theCurrentHourAndOtherDaysAreLeftToMeteoBlue() {
        org.junit.Assume.assumeTrue(nowHour in 1..22)
        val activity = openToday()
        val chart = activity.findViewById<DayChartView>(R.id.day_chart)
        val now = chart.cursorLabel(nowHour)!!
        assertTrue(now.second, !now.second.startsWith("3° · "))
        assertTrue(now.first, now.first.endsWith(activity.getString(R.string.mg_cursor_now)))
        activity.showDay(1) // tomorrow: nothing is past
        assertEquals(0, chart.pastHours)
        assertTrue(chart.cursorLabel(0)!!.second, !chart.cursorLabel(0)!!.second.startsWith("3° · "))
        assertEquals(1f, activity.findViewById<LinearLayout>(R.id.day_hours).getChildAt(0).alpha, 0f)
    }

    @Test
    fun withoutOpenMeteoThePastKeepsMeteoBluesValuesButStillDimmed() {
        org.junit.Assume.assumeTrue(nowHour in 1..22)
        File(context.cacheDir, "meteoblue.json").writeText(WeatherTest.sampleJson(today))
        val activity = open(today.toString())
        assertEquals("15°", rowPill(activity, 0)) // MeteoBlue: 15 + hour % 12
        assertTrue(activity.findViewById<LinearLayout>(R.id.day_hours).getChildAt(0).alpha < 1f)
    }
}
