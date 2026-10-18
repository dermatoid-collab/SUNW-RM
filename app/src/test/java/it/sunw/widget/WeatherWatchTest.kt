package it.sunw.widget

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import it.sunw.widget.watch.DigestNotifier
import it.sunw.widget.watch.DigestReceiver
import it.sunw.widget.watch.DigestScheduler
import it.sunw.widget.watch.EventTracker
import it.sunw.widget.watch.EventType
import it.sunw.widget.watch.WatchData
import it.sunw.widget.watch.WatchPrefs
import it.sunw.widget.weather.Condition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** The weather watch: events with two thresholds, the states between updates, notifications and the recurring summaries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherWatchTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2026, 10, 20)
    private val wind = setOf(EventType.WIND)

    /** Yesterday to the day after tomorrow, every hour, calm and dry except for the gusts [gust] gives. */
    private fun data(gust: (LocalDateTime) -> Double? = { 10.0 }) = WatchData(
        (-24..48).map { h ->
            val t = day.atStartOfDay().plusHours(h.toLong())
            WatchData.Hour(t, 12.0, 0.0, 10.0, 0.0, gust(t), 10000.0, 60.0, 1)
        },
        emptyList(), 0,
    )

    private fun gustsAt(value: Double, vararg hours: Int) = data { t -> if (t.toLocalDate() == day && t.hour in hours) value else 10.0 }
    private fun at(h: Int, m: Int = 0): Instant = day.atTime(h, m).toInstant(ZoneOffset.UTC)

    @Test
    fun anEventIsNotifiedOnceItsLeadTimeComes_notBeforeNorTwice() {
        val tracker = EventTracker.inMemory()
        val gusts = gustsAt(60.0, 20, 21)
        assertEquals(0, tracker.update(gusts, at(10), wind).size) // 10 hours ahead: followed, not yet notified
        assertEquals(1, tracker.active(EventType.WIND).size)
        assertEquals(0, tracker.update(gusts, at(12), wind).size)
        assertEquals(1, tracker.update(gusts, at(15), wind).size) // within 6 hours of its start
        assertEquals(0, tracker.update(gusts, at(15, 30), wind).size) // never twice
        assertEquals(1, tracker.active(EventType.WIND).size) // the sliding horizon does not make a second event
    }

    @Test
    fun betweenTheTwoThresholdsTheStateIsKept() {
        val tracker = EventTracker.inMemory()
        tracker.update(gustsAt(60.0, 14, 15), at(10), wind)
        for (minutes in listOf(30, 60, 90, 120)) tracker.update(gustsAt(45.0, 14, 15), at(10).plusSeconds(minutes * 60L), wind) // under ON, over OFF
        val event = tracker.active(EventType.WIND).single()
        assertEquals(0, event.offCount)
    }

    @Test
    fun itEndsAfterTwoDifferentOffUpdatesNotTwoCopiesOfTheSame() {
        val tracker = EventTracker.inMemory()
        tracker.update(gustsAt(60.0, 14, 15), at(10), wind)
        tracker.update(gustsAt(30.0, 14, 15), at(10, 20), wind) // first OFF
        assertEquals(1, tracker.active(EventType.WIND).single().offCount)
        tracker.update(gustsAt(30.0, 14, 15), at(10, 25), wind) // 5 minutes later, same data: not a new update
        assertEquals(1, tracker.active(EventType.WIND).single().offCount)
        tracker.update(gustsAt(30.0, 14, 15), at(10, 50), wind) // 30 minutes later, same data: still the same update
        assertEquals(1, tracker.active(EventType.WIND).single().offCount)
        tracker.update(gustsAt(31.0, 14, 15), at(10, 55), wind) // different data, 35 minutes after the first OFF: the second confirmation
        assertEquals(EventTracker.State.RESOLVED, tracker.all.single().state)
    }

    @Test
    fun aReadingThatIsNotOffResetsTheCounter() {
        val tracker = EventTracker.inMemory()
        tracker.update(gustsAt(60.0, 14, 15), at(10), wind)
        tracker.update(gustsAt(30.0, 14, 15), at(10, 20), wind) // OFF once
        tracker.update(gustsAt(45.0, 14, 15), at(11), wind) // back in the band: counter to zero
        assertEquals(0, tracker.active(EventType.WIND).single().offCount)
        tracker.update(gustsAt(30.0, 14, 15), at(12), wind)
        assertEquals(EventTracker.State.ACTIVE, tracker.all.single().state) // one OFF is not enough again
    }

    @Test
    fun missingDataChangesNothing() {
        val tracker = EventTracker.inMemory()
        tracker.update(gustsAt(60.0, 14, 15), at(10), wind)
        tracker.update(gustsAt(30.0, 14, 15), at(10, 20), wind)
        val missing = data { t -> if (t.toLocalDate() == day && t.hour in 14..15) null else 10.0 }
        tracker.update(missing, at(12), wind) // the hours of the event have no gusts
        val event = tracker.active(EventType.WIND).single()
        assertEquals(1, event.offCount) // neither counted nor reset
    }

    @Test
    fun aStrongerLevelNotifiesAtOnce() {
        val tracker = EventTracker.inMemory()
        assertEquals(1, tracker.update(gustsAt(55.0, 12, 13), at(10), wind).size)
        val escalation = tracker.update(gustsAt(75.0, 12, 13), at(10, 30), wind)
        assertEquals(1, escalation.size)
        assertTrue(escalation.single().escalation)
        assertEquals(0, tracker.update(gustsAt(75.0, 12, 13), at(11), wind).size)
    }

    @Test
    fun aNewEventSoonAfterANotificationOfTheSameTypeIsNotRepeated() {
        val tracker = EventTracker.inMemory()
        tracker.update(gustsAt(60.0, 11, 12), at(10), wind) // notified
        tracker.update(gustsAt(30.0, 11, 12), at(10, 20), wind)
        tracker.update(gustsAt(31.0, 11, 12), at(10, 45), wind) // resolved
        assertEquals(EventTracker.State.RESOLVED, tracker.all.single().state)
        val again = tracker.update(gustsAt(60.0, 15, 16), at(11), wind) // a new episode within 6 hours
        assertEquals(0, again.size)
        assertEquals(1, tracker.active(EventType.WIND).size) // followed all the same
        assertEquals(1, tracker.active(EventType.WIND).single().notifiedLevel)
    }

    @Test
    fun theWindowEndingIsExpiryNotConfirmation() {
        val tracker = EventTracker.inMemory()
        tracker.update(gustsAt(60.0, 12, 13), at(10), wind)
        tracker.update(gustsAt(60.0, 12, 13), at(16), wind) // two hours past the end
        assertEquals(EventTracker.State.EXPIRED, tracker.all.single().state)
    }

    @Test
    fun eachTypeAndEachEventKeepsItsOwnState() {
        val tracker = EventTracker.inMemory()
        val both = data { t -> if (t.toLocalDate() == day && t.hour in 13..14) 60.0 else 10.0 }
        val hot = WatchData(both.hours.map { h -> WatchData.Hour(h.time, if (h.time.hour in 13..14 && h.time.toLocalDate() == day) 36.0 else 12.0, 0.0, 10.0, 0.0, h.gust, 10000.0, 60.0, 1) }, emptyList(), 0)
        val alerts = tracker.update(hot, at(10), setOf(EventType.WIND, EventType.HEAT))
        assertEquals(setOf(EventType.WIND, EventType.HEAT), alerts.map { it.event.type }.toSet())
        // Switching one type off drops only its events.
        tracker.update(hot, at(10, 30), setOf(EventType.HEAT))
        assertEquals(listOf(EventType.HEAT), tracker.all.map { it.type })
    }

    @Test
    fun thresholdsOfTheOtherEvents() {
        val tracker = EventTracker.inMemory()
        fun withHour(change: (WatchData.Hour) -> WatchData.Hour) = WatchData(data().hours.map { h ->
            if (h.time.toLocalDate() == day && h.time.hour in 12..13) change(h) else h
        }, emptyList(), 0)
        fun one(type: EventType, d: WatchData) = EventTracker.inMemory().update(d, at(10), setOf(type)).map { it.event.type }
        fun h(t: WatchData.Hour, temperature: Double? = t.temperature, precipitation: Double? = t.precipitation, probability: Double? = t.probability,
              snowfall: Double? = t.snowfall, visibility: Double? = t.visibility, humidity: Double? = t.humidity, code: Int? = t.code) =
            WatchData.Hour(t.time, temperature, precipitation, probability, snowfall, t.gust, visibility, humidity, code)
        assertEquals(listOf(EventType.RAIN_HEAVY), one(EventType.RAIN_HEAVY, withHour { h(it, precipitation = 6.0) }))
        assertEquals(emptyList<EventType>(), one(EventType.RAIN_HEAVY, withHour { h(it, precipitation = 4.0) }))
        assertEquals(listOf(EventType.STORM), one(EventType.STORM, withHour { h(it, probability = 70.0, code = 95) }))
        assertEquals(emptyList<EventType>(), one(EventType.STORM, withHour { h(it, probability = 70.0, code = 61) })) // no thunderstorm code
        assertEquals(listOf(EventType.SNOW), one(EventType.SNOW, withHour { h(it, snowfall = 0.5) })) // 1 cm over 3 hours
        assertEquals(listOf(EventType.COLD), one(EventType.COLD, withHour { h(it, temperature = -6.0) }))
        assertEquals(listOf(EventType.FOG), one(EventType.FOG, withHour { h(it, visibility = 150.0) }))
        assertEquals(listOf(EventType.ICE), one(EventType.ICE, withHour { h(it, temperature = -1.0, humidity = 95.0) })) // wet surface
        assertEquals(emptyList<EventType>(), one(EventType.ICE, withHour { h(it, temperature = -1.0, humidity = 50.0) })) // dry
        // A change of 8 °C in 12 hours: the temperature 12 hours later.
        val swing = WatchData(data().hours.map { h ->
            if (h.time == day.atTime(20, 0)) WatchData.Hour(h.time, 21.0, 0.0, 10.0, 0.0, h.gust, 10000.0, 60.0, 1) else h
        }, emptyList(), 0)
        assertEquals(listOf(EventType.CHANGE), one(EventType.CHANGE, swing))
    }

    @Test
    fun rainSoonUsesTheFifteenMinuteDataWhenThereAreAnd_isSupersededByHeavyRain() {
        val base = data()
        val fine = (0 until 96).map { i ->
            val t = day.atStartOfDay().plusMinutes(15L * (i + 1))
            WatchData.Fine(t, if (t.hour == 11 && t.minute > 0) 0.5 else 0.0) // 2 mm/h at 11:15–12:00
        }
        val withRain = WatchData(base.hours.map { h -> WatchData.Hour(h.time, h.temperature, h.precipitation, 80.0, h.snowfall, h.gust, h.visibility, h.humidity, h.code) }, fine, 0)
        val alerts = EventTracker.inMemory().update(withRain, at(10, 30), setOf(EventType.RAIN_SOON))
        assertEquals(listOf(EventType.RAIN_SOON), alerts.map { it.event.type })
        assertEquals(day.atTime(11, 0), alerts.single().event.start.let { it.withMinute(0) }) // starts in the 11:00 hour
        // Heavy rain over the same hours: only its notification is posted.
        val heavy = WatchData(withRain.hours.map { h ->
            if (h.time.toLocalDate() == day && h.time.hour in 11..12) WatchData.Hour(h.time, h.temperature, 6.0, 80.0, h.snowfall, h.gust, h.visibility, h.humidity, h.code) else h
        }, fine, 0)
        val both = EventTracker.inMemory().update(heavy, at(10, 30), setOf(EventType.RAIN_SOON, EventType.RAIN_HEAVY))
        assertEquals(listOf(EventType.RAIN_HEAVY), both.map { it.event.type })
    }

    @Test
    fun theStateIsSavedAndLoadedAgainAndBelongsToOnePlace() {
        val key = EventTracker.placeKey(44.80, 10.33)
        EventTracker.load(context, key).update(gustsAt(60.0, 12, 13), at(10), wind)
        val again = EventTracker.load(context, key)
        assertEquals(1, again.active(EventType.WIND).size)
        assertEquals(1, again.active(EventType.WIND).single().notifiedLevel)
        assertEquals(0, again.update(gustsAt(60.0, 12, 13), at(10, 30), wind).size) // remembered: not notified again
        assertTrue(EventTracker.load(context, EventTracker.placeKey(45.0, 9.0)).all.isEmpty()) // another place starts from nothing
    }

    @Test
    fun openMeteoIsParsedForTheWatch() {
        val json = """{"utc_offset_seconds":7200,"hourly":{"time":["2026-10-20T12:00","2026-10-20T13:00"],"temperature_2m":[16.0,null],
            "precipitation":[0.2,0.0],"precipitation_probability":[80,10],"snowfall":[0.0,0.0],"wind_gusts_10m":[55.5,20.0],
            "visibility":[9000,150],"relative_humidity_2m":[70,95],"weather_code":[61,45]},
            "minutely_15":{"time":["2026-10-20T12:15","2026-10-20T12:30"],"precipitation":[0.1,null]}}"""
        val data = WatchData.parse(json)
        assertEquals(2, data.hours.size)
        assertEquals(55.5, data.hourAt(LocalDateTime.of(2026, 10, 20, 12, 0))!!.gust!!, 0.001)
        assertEquals(null, data.hourAt(LocalDateTime.of(2026, 10, 20, 13, 0))!!.temperature)
        assertEquals(2, data.fine.size)
        assertEquals(LocalDateTime.of(2026, 10, 20, 14, 0), data.localNow(Instant.parse("2026-10-20T12:00:00Z")))
        assertTrue(runCatching { WatchData.parse("""{"error":true,"reason":"nope"}""") }.isFailure)
        assertTrue(WatchData.url(44.8, 10.33).contains("minutely_15=precipitation") && WatchData.url(44.8, 10.33).contains("wind_gusts_10m"))
    }

    // --- the recurring summaries and the settings ---

    @Test
    fun theSummaryTimesStartAtFourTwelveAndTwentyAndCanBeAddedAndRemoved() {
        assertEquals(listOf(LocalTime.of(4, 0), LocalTime.of(12, 0), LocalTime.of(20, 0)), DigestScheduler.times(context))
        assertTrue(DigestScheduler.add(context, LocalTime.of(8, 30)))
        assertTrue(!DigestScheduler.add(context, LocalTime.of(8, 30)))
        assertEquals(listOf("04:00", "08:30", "12:00", "20:00"), DigestScheduler.times(context).map { it.toString() })
        DigestScheduler.remove(context, LocalTime.of(4, 0))
        DigestScheduler.remove(context, LocalTime.of(8, 30))
        DigestScheduler.remove(context, LocalTime.of(12, 0))
        DigestScheduler.remove(context, LocalTime.of(20, 0))
        assertTrue(DigestScheduler.times(context).isEmpty()) // all removed stays empty (the defaults come back only before any change)
    }

    @Test
    fun theNextTriggerIsTheFirstTimeAfterNowAndRollsOverToTomorrow() {
        val times = DigestScheduler.DEFAULTS
        val noon = ZonedDateTime.of(2026, 10, 20, 13, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(noon.withHour(20), DigestScheduler.nextTrigger(times, noon))
        val late = noon.withHour(21)
        assertEquals(late.plusDays(1).withHour(4), DigestScheduler.nextTrigger(times, late))
        assertEquals(null, DigestScheduler.nextTrigger(emptyList(), noon))
    }

    @Test
    fun theAlarmIsSetForTheNextSummaryAndAgainAfterABoot() {
        DigestScheduler.scheduleNext(context)
        val alarm = shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm
        assertNotNull(alarm)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm!!.type)
        DigestReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNotNull(shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
        for (t in DigestScheduler.times(context)) DigestScheduler.remove(context, t)
        // With no time left there is nothing to wake for.
        assertEquals(null, shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
    }

    @Test
    fun theSummaryTellsTemperatureRainAndGusts() {
        fun slot(h: Int, t: Double, rain: Double, gust: Double?, c: Condition) = DigestNotifier.Slot(day.atTime(h, 0), t, rain, gust, c)
        val text = DigestNotifier.summary(
            context,
            listOf(slot(13, 14.0, 0.0, 20.0, Condition.PARTLY_CLOUDY), slot(15, 16.0, 1.4, 30.0, Condition.RAIN), slot(17, 18.0, 1.0, 52.0, Condition.LIGHT_RAIN)),
        )
        assertTrue(text, text.contains("14–18"))
        assertTrue(text, text.contains("15:00") && text.contains("2.4"))
        assertTrue(text, text.contains("52"))
        // A dry, calm stretch says no more than the weather and the temperature.
        val calm = DigestNotifier.summary(context, listOf(slot(13, 14.0, 0.0, 10.0, Condition.CLEAR), slot(14, 15.0, 0.0, 12.0, Condition.CLEAR)))
        assertTrue(calm, !calm.contains("mm") && !calm.contains("km/h"))
    }

    @Test
    fun settingsListTheTimesWithADeleteButtonAndTheEvents() {
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val box = settings.findViewById<LinearLayout>(R.id.digest_times)
        assertEquals(3, box.childCount)
        ((box.getChildAt(0) as LinearLayout).getChildAt(1) as Button).performClick() // delete 04:00
        assertEquals(2, box.childCount)
        assertEquals(listOf("12:00", "20:00"), DigestScheduler.times(context).map { it.toString() })
        assertEquals(EventType.values().size, settings.findViewById<LinearLayout>(R.id.watch_events).childCount)
        assertTrue(WatchPrefs.isEnabled(context))
        repeat(2) { ((box.getChildAt(0) as LinearLayout).getChildAt(1) as Button).performClick() }
        assertEquals(1, box.childCount) // the "no time" line
    }
}
