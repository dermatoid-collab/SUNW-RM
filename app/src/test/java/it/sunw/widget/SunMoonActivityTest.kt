package it.sunw.widget

import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
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
import java.time.LocalDate

/** Sun and Moon by date: opened from the main page, moved with tabs, slider and Today. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SunMoonActivityTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun place() {
        LocationStore(context).save(44.80, 10.33, automatic = false, name = "Parma")
        // The Moon's year is computed on the calling thread, so the tests don't wait for a worker.
        SunMoonActivity.moonExecutor = java.util.concurrent.Executor { it.run() }
    }

    private fun open(date: LocalDate? = null) = Robolectric.buildActivity(
        SunMoonActivity::class.java,
        Intent(context, SunMoonActivity::class.java).apply { date?.let { putExtra(SunMoonActivity.EXTRA_DATE, it.toString()) } },
    ).setup().get()

    @Test
    fun sunAndMoonTilesOpenThePageAtTheirEnd() {
        for ((id, entry) in listOf(R.id.widget_container to SunMoonActivity.ENTRY_SUN, R.id.moon_card to SunMoonActivity.ENTRY_MOON)) {
            val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            main.findViewById<android.view.View>(id).performClick()
            val next = shadowOf(main).nextStartedActivity
            assertEquals(SunMoonActivity::class.java.name, next.component?.className)
            assertEquals(entry, next.getStringExtra(SunMoonActivity.EXTRA_ENTRY))
        }
    }

    @Test
    fun todayShowsEverySectionAndTheNowCursor() {
        val activity = open()
        assertEquals(0, activity.offset)
        assertEquals("Parma", activity.findViewById<TextView>(R.id.sm_place).text.toString())
        assertTrue(activity.findViewById<TextView>(R.id.sm_date).text.isNotEmpty())
        assertTrue(activity.findViewById<TextView>(R.id.sm_sunrise).text.contains(":"))
        assertTrue(activity.findViewById<TextView>(R.id.sm_rise_az).text.contains("°"))
        assertTrue(activity.findViewById<TextView>(R.id.sm_useful).text.contains("–"))
        // Header row + three twilights + noon.
        assertEquals(5, activity.findViewById<LinearLayout>(R.id.sm_twilights).childCount)
        assertTrue(activity.findViewById<TextView>(R.id.sm_cursor).text.contains("°"))
        assertTrue(activity.findViewById<TextView>(R.id.sm_moon_pct).text.endsWith("%"))
        assertEquals(4, activity.findViewById<TextView>(R.id.sm_moon_next).text.split("•").size)
        assertTrue(activity.findViewById<TextView>(R.id.sm_next_event).text.isNotEmpty())
        assertEquals(7, activity.findViewById<LinearLayout>(R.id.sm_tabs).childCount)
        assertEquals(SunMoonActivity.RANGE_DAYS, activity.findViewById<SeekBar>(R.id.sm_slider).progress)
    }

    @Test
    fun tabsSliderAndTodayMoveTheDate() {
        val activity = open()
        // Tabs are centred on the chosen day: the last one is three days ahead.
        activity.findViewById<LinearLayout>(R.id.sm_tabs).getChildAt(6).performClick()
        assertEquals(3, activity.offset)

        activity.select(-182)
        assertEquals(0, activity.findViewById<SeekBar>(R.id.sm_slider).progress)
        activity.select(500)
        assertEquals(SunMoonActivity.RANGE_DAYS, activity.offset)

        activity.findViewById<TextView>(R.id.sm_today).performClick()
        assertEquals(0, activity.offset)
    }

    @Test
    fun opensOnTheRequestedDateWithinRange() {
        assertEquals(10, open(LocalDate.now().plusDays(10)).offset)
        assertEquals(-SunMoonActivity.RANGE_DAYS, open(LocalDate.now().minusYears(2)).offset)
    }

    @Test
    fun tappingTheDateOpensAPickerLimitedToTheRange() {
        val activity = open()
        activity.findViewById<android.view.View>(R.id.sm_date).performClick()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.DatePickerDialog
        val picker = dialog.datePicker
        val zone = java.time.ZoneId.systemDefault()
        assertEquals(LocalDate.now().minusDays(182L), java.time.Instant.ofEpochMilli(picker.minDate).atZone(zone).toLocalDate())
        val target = LocalDate.now().plusDays(40)
        dialog.updateDate(target.year, target.monthValue - 1, target.dayOfMonth)
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        // Dialog buttons report their click through a message on the main thread.
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(40, activity.offset)
    }

    @Test
    fun dateBarAndSliderStayOutsideTheScrollingPart() {
        val activity = open()
        val page = activity.findViewById<android.widget.ScrollView>(R.id.sm_page)
        fun inside(id: Int): Boolean {
            var v: android.view.View? = activity.findViewById(id)
            while (v != null) { if (v === page) return true; v = v.parent as? android.view.View }
            return false
        }
        assertTrue(inside(R.id.sm_sun_card))
        assertTrue(!inside(R.id.sm_date))
        assertTrue(!inside(R.id.sm_slider))
    }

    /** A quick horizontal fling of [dxDp] across the Moon card (negative: to the left). */
    private fun fling(activity: SunMoonActivity, dxDp: Float) {
        val r = android.graphics.Rect().also { activity.findViewById<android.view.View>(R.id.sm_moon_card).getGlobalVisibleRect(it) }
        val y = r.exactCenterY()
        val density = activity.resources.displayMetrics.density
        val x0 = r.exactCenterX() - dxDp / 2 * density
        fun event(action: Int, x: Float, t: Long) = android.view.MotionEvent.obtain(0, t, action, x, y, 0)
        activity.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_DOWN, x0, 0))
        activity.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_MOVE, x0 + dxDp / 2 * density, 40))
        activity.dispatchTouchEvent(event(android.view.MotionEvent.ACTION_UP, x0 + dxDp * density, 80))
    }

    @Test
    fun swipingMovesADay() {
        val activity = open()
        fling(activity, -160f) // to the left: next day
        assertEquals(1, activity.offset)
        fling(activity, 160f)  // to the right: back to today
        fling(activity, 160f)  // and the day before
        assertEquals(-1, activity.offset)
        assertTrue(!activity.isFinishing)
    }

    @Test
    fun draggingOnTheYearStripChangesTheWholePage() {
        val activity = open()
        val strip = activity.findViewById<YearChartView>(R.id.sm_year)
        // The same call the strip makes while a finger moves along it.
        strip.onPick!!.invoke(60)
        assertEquals(60, activity.offset)
        // The page stays visible while dragging: no slide-in that restarts from transparent.
        assertEquals(1f, activity.findViewById<android.view.View>(R.id.sm_content).alpha, 0f)
        assertEquals(SunMoonActivity.RANGE_DAYS + 60, activity.findViewById<SeekBar>(R.id.sm_slider).progress)
        val title = activity.findViewById<TextView>(R.id.sm_date).text.toString()
        strip.onPick!!.invoke(-100)
        assertEquals(-100, activity.offset)
        assertTrue("the date in the bar follows", activity.findViewById<TextView>(R.id.sm_date).text.toString() != title)
    }

    private fun openAt(entry: String) = Robolectric.buildActivity(
        SunMoonActivity::class.java,
        Intent(context, SunMoonActivity::class.java).putExtra(SunMoonActivity.EXTRA_ENTRY, entry),
    ).setup().get()

    @Test
    fun theValuesAreStatedOutright() {
        val a = open()
        fun text(id: Int) = a.findViewById<TextView>(id).text.toString()
        assertTrue(text(R.id.sm_kpi_day).matches(Regex("\\d+h \\d{2}m")))
        assertTrue(text(R.id.sm_kpi_day_sub).contains("m"))
        assertTrue(text(R.id.sm_kpi_sun_max).endsWith("°"))
        assertTrue(text(R.id.sm_kpi_night).matches(Regex("\\d+h \\d{2}m")))
        assertTrue(text(R.id.sm_kpi_moon_sky).matches(Regex("\\d+h \\d{2}m")))
        assertTrue(text(R.id.sm_kpi_moon_max).endsWith("°"))
        // Each chart says what it shows.
        for (id in listOf(R.id.sm_day_legend, R.id.sm_times_legend, R.id.sm_moon_legend)) assertTrue(text(id).isNotEmpty())
        assertTrue(text(R.id.sm_day_range).contains("–"))
    }

    @Test
    fun theMoonChartHasASelectorBetweenNightAndMoonInTheSky() {
        val a = open()
        assertEquals(SunMoonActivity.Metric.NIGHT, a.metric)
        val nightTitle = a.findViewById<TextView>(R.id.sm_moon_title).text.toString()
        val nightNext = a.findViewById<TextView>(R.id.sm_moon_chart_next).text.toString()
        a.findViewById<android.view.View>(R.id.sm_chip_sky).performClick()
        assertEquals(SunMoonActivity.Metric.SKY, a.metric)
        assertTrue(a.findViewById<TextView>(R.id.sm_moon_title).text.toString() != nightTitle)
        // Moon in the sky: the next new and full moon, with the legend of the phase dots.
        val next = a.findViewById<TextView>(R.id.sm_moon_chart_next).text.toString()
        assertTrue(next != nightNext && next.contains("/"))
        a.findViewById<android.view.View>(R.id.sm_chip_night).performClick()
        assertEquals(SunMoonActivity.Metric.NIGHT, a.metric)
    }

    @Test
    fun sunriseAndSunsetHaveTheirOwnChartWithTheChosenDatesTimes() {
        val a = open()
        val rise = a.findViewById<TextView>(R.id.sm_rise_label).text.toString()
        val set = a.findViewById<TextView>(R.id.sm_set_label).text.toString()
        assertTrue(rise.contains(":") && set.contains(":"))
        a.select(100, animate = false)
        assertTrue(a.findViewById<TextView>(R.id.sm_rise_label).text.toString() != rise)
        // The three year charts and the Moon's all move to the chosen date together.
        for (id in listOf(R.id.sm_year, R.id.sm_rise_chart, R.id.sm_set_chart, R.id.sm_moon_chart)) {
            assertTrue(a.findViewById<YearChartView>(id).onPick != null)
        }
    }

    @Test
    fun theSunTileOpensAtTheTopAndTheMoonTileAtTheBottom() {
        val sun = openAt(SunMoonActivity.ENTRY_SUN)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(0, sun.findViewById<android.widget.ScrollView>(R.id.sm_page).scrollY)
        val moon = openAt(SunMoonActivity.ENTRY_MOON)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val page = moon.findViewById<android.widget.ScrollView>(R.id.sm_page)
        val content = page.getChildAt(0)
        // At the end of the content when it is taller than the window (in tests it is laid out at a small size).
        if (content.height > page.height) assertEquals(content.height - page.height, page.scrollY)
    }

    @Test
    fun theDayTabsAreFixedAtTheBottomWithTheSlider() {
        val a = open()
        val page = a.findViewById<android.widget.ScrollView>(R.id.sm_page)
        var v: android.view.View? = a.findViewById(R.id.sm_tabs)
        var inside = false
        while (v != null) { if (v === page) inside = true; v = v.parent as? android.view.View }
        assertTrue(!inside)
    }
}
