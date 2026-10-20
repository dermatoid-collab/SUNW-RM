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
import it.sunw.widget.watch.Thresholds
import it.sunw.widget.watch.WatchData
import it.sunw.widget.watch.PlaceLabels
import it.sunw.widget.watch.WatchPrefs
import it.sunw.widget.watch.WeatherWatch
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

    /** The hours 14 and 15 with a thunderstorm-ish sky: [code], rain probability, CAPE and height of the 0 °C level. */
    private fun stormy(code: Int, probability: Double, cape: Double?, freezing: Double?) = WatchData(data().hours.map { h ->
        if (h.time.toLocalDate() == day && h.time.hour in 14..15) WatchData.Hour(h.time, 22.0, 3.0, probability, 0.0, 40.0, 8000.0, 80.0, code, cape, freezing) else h
    }, emptyList(), 0)

    @Test
    fun hailNeedsAHailCodeOrAThunderstormWithEnergyAndALowFreezingLevel() {
        val hail = setOf(EventType.HAIL)
        fun types(d: WatchData) = EventTracker.inMemory().update(d, at(11), hail).map { it.event.type }
        assertEquals(listOf(EventType.HAIL), types(stormy(96, 30.0, null, null))) // the hail code alone
        assertEquals(listOf(EventType.HAIL), types(stormy(95, 50.0, 1800.0, 3500.0))) // storm, energy, low freezing level
        assertEquals(emptyList<EventType>(), types(stormy(95, 50.0, 1200.0, 3500.0))) // not enough energy
        assertEquals(emptyList<EventType>(), types(stormy(95, 30.0, 1800.0, 3500.0))) // storm unlikely
        assertEquals(emptyList<EventType>(), types(stormy(95, 50.0, 1800.0, 4800.0))) // freezing level too high
        assertEquals(2, EventTracker.inMemory().update(stormy(99, 70.0, 900.0, 3000.0), at(11), hail).single().event.level) // heavy hail code
        assertEquals(2, EventTracker.inMemory().update(stormy(95, 70.0, 2600.0, 3000.0), at(11), hail).single().event.level) // CAPE >= 2500
        assertEquals(1, EventTracker.inMemory().update(stormy(96, 70.0, 2000.0, 3000.0), at(11), hail).single().event.level)
    }

    @Test
    fun hailKeepsItsStateBetweenTheThresholdsAndEndsOnlyBelowTheOffOnes() {
        val hail = setOf(EventType.HAIL)
        val tracker = EventTracker.inMemory()
        tracker.update(stormy(95, 50.0, 1800.0, 3500.0), at(11), hail)
        tracker.update(stormy(95, 50.0, 1200.0, 3500.0), at(12), hail) // under ON (1500), over OFF (1000)
        assertEquals(0, tracker.active(EventType.HAIL).single().offCount)
        tracker.update(stormy(95, 50.0, 500.0, 3500.0), at(12, 20), hail) // OFF once
        assertEquals(1, tracker.active(EventType.HAIL).single().offCount)
        tracker.update(stormy(94, 50.0, 400.0, 3500.0), at(12, 50), hail) // different data: the second
        assertEquals(EventTracker.State.RESOLVED, tracker.all.single().state)
    }

    @Test
    fun hailTakesTheStormsNotification() {
        val both = EventTracker.inMemory().update(stormy(96, 80.0, 1800.0, 3000.0), at(11), setOf(EventType.STORM, EventType.HAIL))
        assertEquals(listOf(EventType.HAIL), both.map { it.event.type })
        // A storm without hail is still a storm.
        val storm = EventTracker.inMemory().update(stormy(95, 80.0, 800.0, 3000.0), at(11), setOf(EventType.STORM, EventType.HAIL))
        assertEquals(listOf(EventType.STORM), storm.map { it.event.type })
    }

    @Test
    fun theWatchCoversTheActivePlaceAndTheDevicePositionWhenTheyDiffer() {
        val parma = Place(44.80, 10.33, automatic = false, name = "Parma")
        val device = Place(45.46, 9.19, automatic = true)
        assertEquals(listOf(parma, device), WeatherWatch.placesToWatch(parma, device))
        assertEquals(listOf(parma), WeatherWatch.placesToWatch(parma, null))
        assertEquals(listOf(parma), WeatherWatch.placesToWatch(parma, Place(44.805, 10.335, automatic = true))) // the same place
    }

    @Test
    fun theNotificationHeaderSaysWhetherItIsThePlaceSetOrTheDevice() {
        assertEquals("📍 Parma", PlaceLabels.subText(context, Place(44.80, 10.33, automatic = false, name = "Parma")))
        assertEquals("📍 44.80, 10.33", PlaceLabels.subText(context, Place(44.80, 10.33, automatic = false)))
        // The device: its city when the geocoder knows it, otherwise the coordinates.
        val device = PlaceLabels.subText(context, Place(45.46, 9.19, automatic = true))
        assertTrue(device, device.startsWith("Device · "))
    }

    @Test
    fun eachPlaceHasItsOwnStateFileAndThoseNoLongerWatchedAreDeleted() {
        val a = EventTracker.placeKey(44.80, 10.33)
        val b = EventTracker.placeKey(45.46, 9.19)
        EventTracker.load(context, a).update(gustsAt(60.0, 12, 13), at(10), wind)
        EventTracker.load(context, b).update(gustsAt(60.0, 12, 13), at(10), wind)
        assertEquals(1, EventTracker.load(context, a).active(EventType.WIND).size)
        assertEquals(1, EventTracker.load(context, b).active(EventType.WIND).size)
        EventTracker.prune(context, listOf(a))
        assertEquals(1, EventTracker.load(context, a).active(EventType.WIND).size)
        assertTrue(EventTracker.load(context, b).all.isEmpty())
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

    // --- the thresholds the user can change ---

    @Test
    fun theThresholdsStartAtTheValuesOfTheDesign() {
        val th = Thresholds.DEFAULTS
        assertEquals(50.0, th.value(EventType.WIND, "on"), 0.0)
        assertEquals(40.0, th.value(EventType.WIND, "off"), 0.0)
        assertEquals(70.0, th.value(EventType.WIND, "strong"), 0.0)
        assertEquals(360L, th.lead(EventType.WIND))
        assertEquals(1440L, th.lead(EventType.COLD))
        assertEquals(-5.0, th.value(EventType.COLD, "on"), 0.0)
        assertEquals(1500.0, th.value(EventType.HAIL, "cape_on"), 0.0)
        assertEquals(2, th.offConfirmations)
        assertEquals(15 * 60_000L, th.minIntervalMs)
        assertEquals(6 * 3_600_000L, th.dedupMs)
        // Every event's own starting values are acceptable ones.
        for (type in EventType.values()) assertEquals(type.name, null, type.invalid(th.valuesOf(type)))
    }

    @Test
    fun aChangedThresholdChangesWhatTheWatchReports() {
        val gusts = gustsAt(45.0, 12, 13)
        assertEquals(0, EventTracker.inMemory().update(gusts, at(10), wind).size) // 45 km/h is under the 50 of the design
        val lower = Thresholds(mapOf("wind.on" to 40.0, "wind.off" to 30.0))
        assertEquals(1, EventTracker.inMemory().update(gusts, at(10), wind, lower).size)
        // The lead time too: a gale 10 hours ahead is announced 6 hours before, or only 1 hour with a shorter lead.
        val later = gustsAt(60.0, 20, 21)
        assertEquals(1, EventTracker.inMemory().update(later, at(15), wind).size)
        assertEquals(0, EventTracker.inMemory().update(later, at(15), wind, Thresholds(mapOf("wind.lead" to 60.0))).size)
        // And the strong level: 55 km/h is a normal level, strong with the threshold at 50.
        val strong = Thresholds(mapOf("wind.strong" to 50.0))
        assertEquals(2, EventTracker.inMemory().update(gustsAt(55.0, 12, 13), at(10), wind, strong).single().event.level)
    }

    @Test
    fun aSingleOffConfirmationEndsTheEventWhenTheGeneralParameterSaysSo() {
        val one = Thresholds(mapOf("global.confirmations" to 1.0))
        val tracker = EventTracker.inMemory()
        tracker.update(gustsAt(60.0, 14, 15), at(10), wind, one)
        tracker.update(gustsAt(30.0, 14, 15), at(10, 20), wind, one)
        assertEquals(EventTracker.State.RESOLVED, tracker.all.single().state)
        assertEquals(3 * 3_600_000L, Thresholds(mapOf("global.dedup" to 3.0)).dedupMs)
    }

    @Test
    fun thresholdsOutOfRangeOrOutOfOrderAreRefused() {
        val wind = EventType.WIND
        fun with(vararg change: Pair<String, Double>) = Thresholds.DEFAULTS.valuesOf(wind) + change
        assertEquals("off", wind.invalid(with("off" to 60.0))) // OFF above ON: the hysteresis would be upside down
        assertEquals("strong", wind.invalid(with("strong" to 45.0))) // strong under ON
        assertEquals("lead", wind.invalid(with("lead" to 5000.0))) // out of range
        assertEquals(null, wind.invalid(with("on" to 45.0, "off" to 35.0)))
        // Cold, ice and fog are the other way round: the lower, the stronger.
        val cold = EventType.COLD
        assertEquals("off", cold.invalid(Thresholds.DEFAULTS.valuesOf(cold) + ("off" to -8.0)))
        assertEquals("strong", cold.invalid(Thresholds.DEFAULTS.valuesOf(cold) + ("strong" to -2.0)))
        assertEquals("prob_off", EventType.RAIN_SOON.invalid(Thresholds.DEFAULTS.valuesOf(EventType.RAIN_SOON) + ("prob_off" to 80.0)))
        assertEquals("cape_strong", EventType.HAIL.invalid(Thresholds.DEFAULTS.valuesOf(EventType.HAIL) + ("cape_strong" to 1200.0)))
    }

    @Test
    fun thresholdsAreKeptAndAValueEqualToTheStartingOneIsNotStored() {
        Thresholds.save(context, EventType.WIND, Thresholds.DEFAULTS.valuesOf(EventType.WIND) + ("on" to 45.0))
        assertEquals(45.0, Thresholds.load(context).value(EventType.WIND, "on"), 0.0)
        assertEquals(40.0, Thresholds.load(context).value(EventType.WIND, "off"), 0.0) // untouched: still the design's
        assertTrue(context.getSharedPreferences("weather_watch_thresholds", Context.MODE_PRIVATE).all.keys == setOf("wind.on"))
        // A decimal comes back exactly, not as a float's approximation.
        Thresholds.save(context, EventType.RAIN_SOON, Thresholds.DEFAULTS.valuesOf(EventType.RAIN_SOON) + ("on" to 0.3))
        assertEquals(0.3, Thresholds.load(context).value(EventType.RAIN_SOON, "on"), 0.0)
        Thresholds.reset(context, EventType.WIND)
        assertEquals(50.0, Thresholds.load(context).value(EventType.WIND, "on"), 0.0)
        assertEquals(0.3, Thresholds.load(context).value(EventType.RAIN_SOON, "on"), 0.0) // the other event keeps its own
        Thresholds.reset(context, null)
        assertEquals(0.2, Thresholds.load(context).value(EventType.RAIN_SOON, "on"), 0.0)
    }

    @Test
    fun theThresholdsOfAnEventAreEditedInADialogFromSettings() {
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val events = settings.findViewById<LinearLayout>(R.id.watch_events)
        events.findViewWithTag<Button>("thresholds_wind").performClick()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.AlertDialog
        val box = dialog.findViewById<LinearLayout>(R.id.watch_params)
        fun field(key: String) = box.findViewWithTag<android.widget.EditText>(key)
        assertEquals(EventType.WIND.params.size, EventType.WIND.params.count { field(it.key) != null })
        assertEquals("50", field("on").text.toString())
        // A value out of order is refused and the dialog stays.
        field("off").setText("60")
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertEquals(40.0, Thresholds.load(context).value(EventType.WIND, "off"), 0.0)
        // A good one is kept and the dialog closes.
        field("off").setText("35")
        field("on").setText("45")
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(!dialog.isShowing)
        assertEquals(45.0, Thresholds.load(context).value(EventType.WIND, "on"), 0.0)
        assertEquals(35.0, Thresholds.load(context).value(EventType.WIND, "off"), 0.0)
        // The general parameters have their own dialog; "Reset all" puts everything back.
        settings.findViewById<Button>(R.id.watch_general).performClick()
        val general = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.AlertDialog
        assertEquals("2", general.findViewById<LinearLayout>(R.id.watch_params).findViewWithTag<android.widget.EditText>("confirmations").text.toString())
        general.dismiss()
        settings.findViewById<Button>(R.id.watch_reset).performClick()
        assertEquals(50.0, Thresholds.load(context).value(EventType.WIND, "on"), 0.0)
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
