package it.sunw.widget

import android.app.AlarmManager
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The widget's redraw triggers (alarm, 15-minute job, screen on) and the line in Settings that reports them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRefreshTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun placeAndWidget() {
        LocationStore(context).save(44.80, 10.33, automatic = false, name = "Parma")
        shadowOf(AppWidgetManager.getInstance(context)).createWidget(SunWidgetProvider::class.java, R.layout.widget_sun)
    }

    private fun shownAfterUpdate(source: WidgetRefresh.Source = WidgetRefresh.Source.APP): WidgetRefresh.Diagnostics {
        SunWidgetProvider.updateAll(context, source)
        return WidgetRefresh.diagnostics(context)!!
    }

    @Test
    fun nothingIsRecordedBeforeTheFirstRedraw() {
        assertNull(WidgetRefresh.diagnostics(context))
    }

    @Test
    fun aRedrawRecordsItsSourceAndWhenTheNextOneIsDue() {
        val d = shownAfterUpdate(WidgetRefresh.Source.JOB)
        assertEquals(WidgetRefresh.Source.JOB, d.source)
        assertNotNull(d.next)
        assertTrue(d.next!!.isAfter(d.at))
    }

    @Test
    fun theAlarmWakesTheDeviceAndIsNeverMoreThanAQuarterOfAnHourAway() {
        shownAfterUpdate()
        val alarm = shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm
        assertNotNull(alarm)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm!!.type)
        val wait = alarm.triggerAtTime - System.currentTimeMillis()
        assertTrue("wait ${wait / 1000}s", wait in 0..(16 * 60 * 1000L))
    }

    @Test
    fun aFifteenMinuteJobBacksTheAlarmUpAndSurvivesReboots() {
        shownAfterUpdate()
        assertTrue(WidgetRefreshJobService.isScheduled(context))
        val job = context.getSystemService(JobScheduler::class.java).allPendingJobs.single { it.service.className == WidgetRefreshJobService::class.java.name }
        assertTrue(job.isPeriodic)
        assertEquals(WidgetRefreshJobService.PERIOD_MS, job.intervalMillis)
        assertTrue(job.isPersisted)
    }

    @Test
    fun theAlarmBroadcastRedrawsAndSetsTheNextAlarm() {
        SunWidgetProvider().onReceive(context, Intent(SunWidgetProvider.ACTION_TICK))
        assertEquals(WidgetRefresh.Source.ALARM, WidgetRefresh.diagnostics(context)!!.source)
        assertNotNull(shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
    }

    @Test
    fun turningTheScreenOnRedrawsOnceAMinuteAtMost() {
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(WidgetRefresh.Source.SCREEN, WidgetRefresh.diagnostics(context)?.source)

        // A redraw just done by something else: unlocking right after does not redraw again.
        shownAfterUpdate(WidgetRefresh.Source.APP)
        context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(WidgetRefresh.Source.APP, WidgetRefresh.diagnostics(context)?.source)
    }

    @Test
    fun settingsReportsEachCauseWithItsLastTimeAndCount() {
        val before = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertEquals(before.getString(R.string.wr_diag_none), before.findViewById<TextView>(R.id.widget_diag).text.toString())

        shownAfterUpdate(WidgetRefresh.Source.ALARM)
        shownAfterUpdate(WidgetRefresh.Source.ALARM)
        shownAfterUpdate(WidgetRefresh.Source.JOB)
        val after = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val text = after.findViewById<TextView>(R.id.widget_diag).text.toString()
        assertTrue(text, text.contains(after.getString(R.string.wr_alarm)) && text.contains("×2"))
        assertTrue(text, text.contains(after.getString(R.string.wr_job)) && text.contains("×1"))
        assertEquals(text, 4, text.lines().size) // heading, the two causes, and the next check
    }

    @Test
    fun openingTheAppDoesNotHideWhatTheOtherCausesDid() {
        shownAfterUpdate(WidgetRefresh.Source.ALARM)
        shownAfterUpdate(WidgetRefresh.Source.JOB)
        shownAfterUpdate(WidgetRefresh.Source.APP) // opening the app redraws the widgets too
        val entries = WidgetRefresh.entries(context)
        assertEquals(setOf(WidgetRefresh.Source.ALARM, WidgetRefresh.Source.JOB, WidgetRefresh.Source.APP), entries.map { it.source }.toSet())
        assertEquals(WidgetRefresh.Source.APP, WidgetRefresh.diagnostics(context)!!.source) // the last one overall
        assertTrue(entries.all { it.count == 1 })
        val first = entries.first()
        assertTrue(entries.all { first.at >= it.at }) // most recent first
    }
}
