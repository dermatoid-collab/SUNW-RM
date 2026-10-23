package it.sunw.widget

import it.sunw.widget.alerts.AlertNotifier
import it.sunw.widget.watch.DigestScheduler
import it.sunw.widget.watch.EventType
import it.sunw.widget.watch.Thresholds
import it.sunw.widget.watch.WatchPrefs
import it.sunw.widget.weather.WeatherDayActivity
import java.time.LocalTime
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsBackupTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun clear() = listOf(
        "place", "favorites", "appearance", "alert_notifications", "weather_watch_prefs", "weather_watch_thresholds",
        "weather_digest", "weather_day", "recent_places",
    ).forEach {
        context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Before
    fun setUp() = clear()

    @Test
    fun exportThenImportRestoresEverything() {
        LocationStore(context).save(46.5287, 10.4527, automatic = false, name = "Passo dello Stelvio")
        FavoritesStore(context).add(Favorite("Livigno", 46.5386, 10.1357))
        FavoritesStore(context).add(Favorite("🏠", 44.73, 10.37))
        AppearanceStore(context).apply {
            curveStyle = CurveStyle.TWILIGHT
            accent = Accent.TOKYO_BLUE
            theme = Theme.TOKYO_NIGHT
        }
        AlertNotifier.setEnabled(context, false)
        val json = SettingsBackup.export(context)

        clear() // as after a reinstall
        SettingsBackup.import(context, json)

        val place = LocationStore(context).current()
        assertFalse(place.automatic)
        assertEquals("Passo dello Stelvio", place.name)
        assertEquals(46.5287, place.latitude, 1e-9)
        assertEquals(listOf("Livigno", "🏠"), FavoritesStore(context).all().map { it.name })
        AppearanceStore(context).let {
            assertEquals(CurveStyle.TWILIGHT, it.curveStyle)
            assertEquals(Accent.TOKYO_BLUE, it.accent)
            assertEquals(Theme.TOKYO_NIGHT, it.theme)
        }
        assertFalse(AlertNotifier.isEnabled(context))
    }

    @Test
    fun theWeatherNotificationsRecentsAndHourStepAreInTheBackupToo() {
        WatchPrefs.setEnabled(context, false)
        WatchPrefs.setOn(context, EventType.HAIL, false)
        val heat = EventType.HEAT.params.associate { it.key to it.default }.toMutableMap().also { it["on"] = 36.0; it["off"] = 34.0; it["lead"] = 600.0 }
        Thresholds.save(context, EventType.HEAT, heat)
        Thresholds.saveGeneral(context, mapOf("confirmations" to 3.0, "interval" to 20.0, "dedup" to 8.0))
        DigestScheduler.setTimes(context, listOf(LocalTime.of(6, 30), LocalTime.of(18, 0)))
        WeatherDayActivity.setStepHours(context, 3)
        RecentPlaces(context).add(Favorite("Livigno", 46.5386, 10.1357))
        RecentPlaces(context).add(Favorite("Bormio", 46.4669, 10.3705))
        val json = SettingsBackup.export(context)

        clear()
        SettingsBackup.import(context, json)

        assertFalse(WatchPrefs.isEnabled(context))
        assertFalse(WatchPrefs.isOn(context, EventType.HAIL))
        assertTrue(WatchPrefs.isOn(context, EventType.HEAT))
        val thresholds = Thresholds.load(context)
        assertEquals(36.0, thresholds.value(EventType.HEAT, "on"), 1e-9)
        assertEquals(34.0, thresholds.value(EventType.HEAT, "off"), 1e-9)
        assertEquals(600L, thresholds.lead(EventType.HEAT))
        assertEquals(3, thresholds.offConfirmations)
        assertEquals(20L * 60_000L, thresholds.minIntervalMs)
        assertEquals(8L * 3_600_000L, thresholds.dedupMs)
        assertEquals(listOf(LocalTime.of(6, 30), LocalTime.of(18, 0)), DigestScheduler.times(context))
        assertEquals(3, WeatherDayActivity.stepHours(context))
        assertEquals(listOf("Bormio", "Livigno"), RecentPlaces(context).all().map { it.name })
    }

    @Test
    fun anOutOfOrderThresholdSetInAFileIsLeftOutAndAnOlderFileStillImports() {
        // OFF above ON for the heat: not acceptable, so the starting values stay.
        val broken = """{"app":"it.sunw.widget","version":2,"watch":{"thresholds":{"heat":{"on":36,"off":40,"strong":38,"lead":60}}}}"""
        SettingsBackup.import(context, broken)
        assertEquals(35.0, Thresholds.load(context).value(EventType.HEAT, "on"), 1e-9)
        // Version 1 had none of the newer sections: it imports and changes nothing of them.
        SettingsBackup.import(context, """{"app":"it.sunw.widget","version":1,"favorites":[]}""")
        assertTrue(WatchPrefs.isEnabled(context))
        assertEquals(DigestScheduler.DEFAULTS, DigestScheduler.times(context))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherFiles() {
        SettingsBackup.import(context, """{"hello":"world"}""")
    }
}
