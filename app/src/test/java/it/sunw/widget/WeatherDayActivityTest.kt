package it.sunw.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import it.sunw.widget.weather.Condition
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
    fun everyConditionHasALucideIconByDayAndByNight() {
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
