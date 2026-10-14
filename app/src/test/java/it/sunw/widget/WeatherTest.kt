package it.sunw.widget

import it.sunw.widget.weather.Condition
import it.sunw.widget.weather.Forecast
import it.sunw.widget.weather.ForecastException
import it.sunw.widget.weather.Rainspot
import it.sunw.widget.weather.WeatherCard
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Robolectric runner because org.json is part of the Android framework. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherTest {

    private fun sampleJson() = Companion.sampleJson()

    companion object {
    /** A response shaped like MeteoBlue's basic-1h_basic-day: 2 days of hours, 3 days from [first]. */
    fun sampleJson(first: LocalDate = LocalDate.of(2026, 10, 8)): String {
        val start = first.atStartOfDay()
        val hours = (0 until 48).map { start.plusHours(it.toLong()) }
        fun arr(values: List<Any>) = JSONArray(values)
        val hourlyPicto = hours.map { t ->
            when {
                t.toLocalDate() == first && t.hour >= 21 -> 27 // thunderstorm in the evening
                t.toLocalDate() == first.plusDays(1) && t.hour < 5 -> 33 // light rain overnight
                else -> 7
            }
        }
        val data1h = JSONObject()
            .put("time", arr(hours.map { "%04d-%02d-%02d %02d:00".format(it.year, it.monthValue, it.dayOfMonth, it.hour) }))
            .put("temperature", arr(hours.map { 15.0 + (it.hour % 12) }))
            .put("felttemperature", arr(hours.map { 14.0 + (it.hour % 12) }))
            .put("precipitation", arr(hours.map { 0.0 }))
            .put("precipitation_probability", arr(hours.map { 10 }))
            .put("windspeed", arr(hours.map { 9.0 }))
            .put("winddirection", arr(hours.map { 0 }))
            .put("pictocode", arr(hourlyPicto))
            .put("uvindex", arr(hours.map { 3 }))
            .put("isdaylight", arr(hours.map { if (it.hour in 7..18) 1 else 0 }))
        val dataDay = JSONObject()
            .put("time", arr((0L..2L).map { first.plusDays(it).toString() }))
            .put("pictocode", arr(listOf(8, 6, 3)))
            .put("temperature_max", arr(listOf(20.4, 18.0, 19.6)))
            .put("temperature_min", arr(listOf(15.0, 14.6, 13.8)))
            .put("precipitation", arr(listOf(4.2, 3.1, 0.0)))
            .put("precipitation_probability", arr(listOf(27, 72, 17)))
            .put("windspeed_max", arr(listOf(9.0, 7.0, 5.0)))
            .put("winddirection", arr(listOf(0, 180, 315)))
            .put("uvindex", arr(listOf(3, 2, 4)))
        return JSONObject()
            .put("metadata", JSONObject().put("latitude", 44.80).put("longitude", 10.33).put("modelrun_utc", "2026-10-08 06:00").put("utc_timeoffset", 2.0))
            .put("data_1h", data1h)
            .put("data_day", dataDay)
            .toString()
    }
    }

    @Test
    fun parsesHoursAndDays() {
        val forecast = Forecast.parse(sampleJson(), Instant.parse("2026-10-08T11:59:00Z"))
        assertEquals(48, forecast.hours.size)
        assertEquals(3, forecast.days.size)
        assertEquals(2.0, forecast.utcOffsetHours, 0.0)

        val today = forecast.days[0]
        assertEquals(Condition.THUNDERSTORM, today.condition)
        assertEquals(20.4, today.temperatureMax, 1e-9)
        assertEquals(27, today.precipitationProbability)
        assertEquals(Condition.RAIN, forecast.days[1].condition)
        assertEquals(Condition.PARTLY_CLOUDY, forecast.days[2].condition)
    }

    @Test
    fun currentHourUsesLocationTime() {
        val forecast = Forecast.parse(sampleJson(), Instant.parse("2026-10-08T11:59:00Z"))
        // 11:59 UTC is 13:59 at UTC+2 → the 13:00 slot.
        val local = forecast.localTime(Instant.parse("2026-10-08T11:59:00Z"))
        assertEquals(LocalDateTime.of(2026, 10, 8, 13, 59), local)
        val hour = forecast.hourAt(local)!!
        assertEquals(13, hour.time.hour)
        assertTrue(hour.isDaylight)
        assertEquals(Condition.PARTLY_CLOUDY, hour.condition)
    }

    @Test
    fun nightIconIsTheMostSignificantNightCondition() {
        val forecast = Forecast.parse(sampleJson(), Instant.now())
        assertEquals(Condition.THUNDERSTORM, forecast.nightCondition(LocalDate.of(2026, 10, 8)))
    }

    @Test(expected = ForecastException::class)
    fun apiErrorsAreReported() {
        Forecast.parse("""{"error_message":"API key invalid"}""", Instant.now())
    }

    @Test
    fun pictocodeTables() {
        assertEquals(Condition.CLEAR, Condition.fromHourlyPictocode(1))
        assertEquals(Condition.FOG, Condition.fromHourlyPictocode(16))
        assertEquals(Condition.OVERCAST, Condition.fromHourlyPictocode(22))
        assertEquals(Condition.SNOW, Condition.fromHourlyPictocode(24))
        assertEquals(Condition.THUNDERSTORM, Condition.fromHourlyPictocode(30))
        assertEquals(Condition.SHOWERS, Condition.fromHourlyPictocode(31))
        assertEquals(Condition.SLEET, Condition.fromHourlyPictocode(35))
        assertEquals(Condition.SHOWERS, Condition.fromDailyPictocode(7))
        assertEquals(Condition.SLEET, Condition.fromDailyPictocode(11))
        assertFalse(Condition.OVERCAST.hasSunOrMoon)
    }

    @Test
    fun predictabilityIsParsedWhenTheDayHasIt() {
        val root = JSONObject(sampleJson())
        assertEquals(null, Forecast.parse(root.toString(), java.time.Instant.now()).days[0].predictability)
        root.getJSONObject("data_day").put("predictability", JSONArray(listOf(78, 55, 120)))
        val days = Forecast.parse(root.toString(), java.time.Instant.now()).days
        assertEquals(listOf<Int?>(78, 55, null), days.map { it.predictability }) // out of range → none
    }

    @Test
    fun openMeteoHoursAreParsedPerHourAndToleratedWhenPartial() {
        val json = """{"hourly":{"time":["2026-10-18T13:00","2026-10-18T14:00","2026-10-18T15:00"],
            "temperature_2m":[16.4,null,17.0],"apparent_temperature":[14.0,14.5,15.0],"precipitation":[0.0,0.3,null],
            "wind_speed_10m":[12.2,13.0,14.0],"wind_direction_10m":[200,210,220],"wind_gusts_10m":[30.2,null,36.5],
            "weather_code":[3,61,95]}}"""
        val hours = it.sunw.widget.weather.OpenMeteoHours.parse(json)
        val first = hours[java.time.LocalDateTime.of(2026, 10, 18, 13, 0)]!!
        assertEquals(16.4, first.temperature!!, 0.001)
        assertEquals(14.0, first.feltTemperature!!, 0.001)
        assertEquals(12.2, first.windSpeed!!, 0.001)
        assertEquals(200, first.windDirection)
        assertEquals(30.2, first.gust!!, 0.001)
        assertEquals(3, first.weatherCode)
        val second = hours[java.time.LocalDateTime.of(2026, 10, 18, 14, 0)]!!
        assertEquals(null, second.temperature) // a missing value stays missing
        assertEquals(null, second.gust)
        assertEquals(0.3, second.precipitation!!, 0.001)
        assertEquals(null, hours[java.time.LocalDateTime.of(2026, 10, 18, 16, 0)])
        // Only some variables are enough.
        val gustsOnly = it.sunw.widget.weather.OpenMeteoHours.parse("""{"hourly":{"time":["2026-10-18T13:00"],"wind_gusts_10m":[30.2]}}""")
        assertEquals(30.2, gustsOnly[java.time.LocalDateTime.of(2026, 10, 18, 13, 0)]!!.gust!!, 0.001)
        assertEquals(null, gustsOnly[java.time.LocalDateTime.of(2026, 10, 18, 13, 0)]!!.temperature)
        val url = it.sunw.widget.weather.OpenMeteoHoursRepository.url(44.8, 10.33)
        assertTrue(url, url.contains("wind_gusts_10m") && url.contains("past_days=1") && url.contains("weather_code") && url.contains("apparent_temperature"))
        assertTrue(runCatching { it.sunw.widget.weather.OpenMeteoHours.parse("""{"error":true,"reason":"nope"}""") }.isFailure)
    }

    @Test
    fun wmoWeatherCodesBecomeOurConditions() {
        val expected = mapOf(
            0 to Condition.CLEAR, 1 to Condition.MOSTLY_CLEAR, 2 to Condition.PARTLY_CLOUDY, 3 to Condition.OVERCAST,
            45 to Condition.FOG, 51 to Condition.LIGHT_RAIN, 61 to Condition.LIGHT_RAIN, 63 to Condition.RAIN,
            65 to Condition.HEAVY_RAIN, 80 to Condition.SHOWERS, 82 to Condition.HEAVY_RAIN, 71 to Condition.SNOW,
            85 to Condition.SNOW_SHOWERS, 66 to Condition.SLEET, 95 to Condition.THUNDERSTORM, 99 to Condition.THUNDERSTORM,
        )
        for ((code, condition) in expected) assertEquals("WMO $code", condition, Condition.fromWmoCode(code))
        assertEquals(Condition.PARTLY_CLOUDY, Condition.fromWmoCode(1234)) // unknown: neutral
    }

    @Test
    fun rainspotRunsSouthToNorth() {
        // Decoded from a Meteoblue screenshot (Pannocchia, 21:00): rain to the south, dry north.
        val spot = "1111111111111111191119119900009000000000000000000"
        assertEquals('0', Rainspot.cell(spot, 0, 0)) // north-west
        assertEquals('1', Rainspot.cell(spot, 6, 0)) // south-west
        assertEquals('9', Rainspot.cell(spot, 3, 0)) // the place's row, west edge
        assertTrue(Rainspot.hasRain(spot))
        assertFalse(Rainspot.hasRain("0".repeat(49)))
    }

    @Test
    fun rainspotCellsMeetOnWholePixels() {
        for (size in listOf(42, 118, 146, 200)) {
            val edges = Rainspot.edges(size * 0.006f, size * 0.988f)
            assertEquals(8, edges.size)
            // Strictly increasing, and every cell within a pixel of the exact width: no gaps, no overlaps.
            assertTrue(edges.toList().zipWithNext().all { (a, b) -> b > a })
            val exact = size * 0.988f / 7
            assertTrue(edges.toList().zipWithNext().all { (a, b) -> Math.abs((b - a) - exact) <= 1f })
        }
    }

    @Test
    fun rainspotIsParsedPerHourAndDay() {
        val json = JSONObject(sampleJson())
        val spot = "0".repeat(42) + "1111111"
        json.getJSONObject("data_1h").put("rainspot", JSONArray(List(48) { spot }))
        json.getJSONObject("data_day").put("rainspot", JSONArray(listOf(spot, "bad", spot)))
        val forecast = Forecast.parse(json.toString(), Instant.now())
        assertEquals(spot, forecast.hours[0].rainspot)
        assertEquals(spot, forecast.days[0].rainspot)
        assertEquals(null, forecast.days[1].rainspot) // malformed → ignored
    }

    @Test
    fun windArrowPointsDownwind() {
        assertEquals("↓", WeatherCard.windArrow(0))   // from north → blowing south
        assertEquals("↑", WeatherCard.windArrow(180))
        assertEquals("↘", WeatherCard.windArrow(315)) // from north-west
        assertEquals("←", WeatherCard.windArrow(90))
    }

    @Test
    fun formatting() {
        assertEquals("20°", WeatherCard.deg(20.4))
        assertEquals("—", WeatherCard.mm(0.0))
    }
}
