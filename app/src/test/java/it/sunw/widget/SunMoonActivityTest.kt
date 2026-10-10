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
    }

    private fun open(date: LocalDate? = null) = Robolectric.buildActivity(
        SunMoonActivity::class.java,
        Intent(context, SunMoonActivity::class.java).apply { date?.let { putExtra(SunMoonActivity.EXTRA_DATE, it.toString()) } },
    ).setup().get()

    @Test
    fun sunAndMoonTilesOpenThePage() {
        for (id in listOf(R.id.widget_container, R.id.moon_card)) {
            val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            main.findViewById<android.view.View>(id).performClick()
            val next = shadowOf(main).nextStartedActivity
            assertEquals(SunMoonActivity::class.java.name, next.component?.className)
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
        val strip = activity.findViewById<YearStripView>(R.id.sm_year)
        // The same call the strip makes while a finger moves along it.
        strip.onPick!!.invoke(60)
        assertEquals(60, activity.offset)
        assertEquals(SunMoonActivity.RANGE_DAYS + 60, activity.findViewById<SeekBar>(R.id.sm_slider).progress)
        val title = activity.findViewById<TextView>(R.id.sm_date).text.toString()
        strip.onPick!!.invoke(-100)
        assertEquals(-100, activity.offset)
        assertTrue("the date in the bar follows", activity.findViewById<TextView>(R.id.sm_date).text.toString() != title)
    }
}
