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
import it.sunw.widget.weather.GustRepository
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
        GustRepository.executor = java.util.concurrent.Executor { } // no network in the tests
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

    /** Open-Meteo gusts for the sample's hours, cached as the repository would have left them. */
    private fun seedGusts(kmh: Double) {
        val times = (0 until 48).map { java.time.LocalDate.of(2026, 10, 8).atStartOfDay().plusHours(it.toLong()).toString().take(16) }
        val json = org.json.JSONObject().put("hourly", org.json.JSONObject()
            .put("time", org.json.JSONArray(times))
            .put("wind_gusts_10m", org.json.JSONArray(times.map { kmh })))
        File(context.cacheDir, "gusts.json").writeText(json.toString())
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putLong("gust_fetched", System.currentTimeMillis())
            .putLong("gust_lat", 44.80.toRawBits())
            .putLong("gust_lon", 10.33.toRawBits())
            .commit()
    }

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
        seedGusts(36.0)
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
}
