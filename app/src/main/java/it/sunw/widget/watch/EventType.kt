package it.sunw.widget.watch

import it.sunw.widget.R
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One value of an event's series, valid for [minutes] minutes from [time]; [value] null means the data is missing. */
class Point(val time: LocalDateTime, val value: Double?, val aux: Double? = null, val minutes: Long = 60)

/** A run of points over the ON threshold: when it starts and ends, how strong it gets and its strongest value. */
class Episode(val start: LocalDateTime, val end: LocalDateTime, val level: Int, val peak: Double)

/**
 * The weather events the watch reports, each with its two thresholds (ON to start, OFF to end), how long
 * before it starts it is reported ([leadMinutes]) and its icon. [level] 2 is the strong version of the event
 * (an escalation notifies at once). The thresholds are starting values to calibrate on the provider.
 */
enum class EventType(val key: String, val title: Int, val icon: Int, val leadMinutes: Long, val thresholds: String) {
    RAIN_SOON("rain_soon", R.string.we_rain_soon, R.drawable.wx_rainy_light, 60, "ON p>=70% & >=0.2 mm/h; OFF p<50% | <0.1 mm/h"),
    RAIN_HEAVY("rain_heavy", R.string.we_rain_heavy, R.drawable.wx_rainy_heavy, 180, "ON >=5 mm/h; OFF <3 mm/h"),
    STORM("storm", R.string.we_storm, R.drawable.wx_thunderstorm, 180, "ON p>=60% & thunderstorm code; OFF p<40% | no thunderstorm code"),
    SNOW("snow", R.string.we_snow, R.drawable.wx_cloudy_snowing, 360, "ON >=1 cm in 3 h; OFF <0.5 cm in 3 h"),
    WIND("wind", R.string.we_wind, R.drawable.wx_air, 360, "ON gusts>=50 km/h; OFF <40 km/h"),
    HEAT("heat", R.string.we_heat, R.drawable.wx_sunny, 1440, "ON >=35 C; OFF <33 C"),
    COLD("cold", R.string.we_cold, R.drawable.wx_thermostat, 1440, "ON <=-5 C; OFF >-3 C"),
    ICE("ice", R.string.we_ice, R.drawable.wx_snowflake, 360, "ON <=0 C & wet surface; OFF >2 C | dry"),
    FOG("fog", R.string.we_fog, R.drawable.wx_foggy, 180, "ON visibility<=200 m; OFF >500 m"),
    CHANGE("change", R.string.we_change, R.drawable.wx_swap_vert, 720, "ON |dT 12 h|>=8 C; OFF <5 C");

    /** The values to judge, in time order, from Open-Meteo's data. */
    fun series(d: WatchData): List<Point> = when (this) {
        RAIN_SOON -> rainSeries(d)
        RAIN_HEAVY -> d.hours.map { Point(it.time, it.precipitation) }
        STORM -> d.hours.map { h -> Point(h.time, if (h.probability != null && h.code != null) h.probability else null, h.code?.toDouble()) }
        SNOW -> d.hours.mapIndexed { i, h ->
            val window = d.hours.subList(max(0, i - 2), i + 1)
            Point(h.time, if (window.size == 3 && window.all { it.snowfall != null }) window.sumOf { it.snowfall!! } else null)
        }
        WIND -> d.hours.map { Point(it.time, it.gust) }
        HEAT, COLD -> d.hours.map { Point(it.time, it.temperature) }
        ICE -> d.hours.mapIndexed { i, h ->
            val recent = d.hours.subList(max(0, i - 2), i + 1)
            val missing = h.temperature == null || (h.precipitation == null && h.humidity == null)
            val wet = recent.sumOf { it.precipitation ?: 0.0 } >= 0.1 || (h.humidity ?: 0.0) >= 90.0 || h.code in FREEZING_RAIN
            Point(h.time, if (missing) null else h.temperature, if (wet) 1.0 else 0.0)
        }
        FOG -> d.hours.map { h ->
            Point(h.time, h.visibility ?: when (h.code) {
                null -> null
                45, 48 -> 150.0
                else -> 5000.0
            })
        }
        CHANGE -> d.hours.map { h ->
            val later = d.hourAt(h.time.plusHours(12))
            Point(h.time, if (h.temperature != null && later?.temperature != null) abs(later.temperature - h.temperature) else null, null, 720)
        }
    }

    /** Rain: every 15 minutes where Open-Meteo has them (a quarter-hour's mm × 4 = mm/h), then by the hour. */
    private fun rainSeries(d: WatchData): List<Point> {
        fun probability(t: LocalDateTime) = d.hourAt(t.withMinute(0))?.probability
        val fine = d.fine.map { f -> Point(f.time, if (f.precipitation != null && probability(f.time) != null) f.precipitation * 4 else null, probability(f.time), 15) }
        val after = fine.lastOrNull()?.time
        val hourly = d.hours.filter { after == null || it.time.isAfter(after) }
            .map { h -> Point(h.time, if (h.precipitation != null && h.probability != null) h.precipitation else null, h.probability) }
        return fine + hourly
    }

    /** Whether the (valid) point reaches the ON threshold. */
    fun isOn(p: Point): Boolean {
        val v = p.value ?: return false
        return when (this) {
            RAIN_SOON -> v >= 0.2 && (p.aux ?: 0.0) >= 70
            RAIN_HEAVY -> v >= 5
            STORM -> v >= 60 && (p.aux ?: 0.0) >= 95
            SNOW -> v >= 1.0
            WIND -> v >= 50
            HEAT -> v >= 35
            COLD -> v <= -5
            ICE -> v <= 0 && p.aux == 1.0
            FOG -> v <= 200
            CHANGE -> v >= 8
        }
    }

    /** Whether the (valid) point meets the OFF condition (not merely "not ON": between the two the state is kept). */
    fun isOff(p: Point): Boolean {
        val v = p.value ?: return false
        return when (this) {
            RAIN_SOON -> (p.aux ?: 0.0) < 50 || v < 0.1
            RAIN_HEAVY -> v < 3
            STORM -> v < 40 || (p.aux ?: 0.0) < 95
            SNOW -> v < 0.5
            WIND -> v < 40
            HEAT -> v < 33
            COLD -> v > -3
            ICE -> v > 2 || p.aux != 1.0
            FOG -> v > 500
            CHANGE -> v < 5
        }
    }

    /** 1 for the event, 2 for its strong version. */
    fun level(p: Point): Int {
        val v = p.value ?: return 1
        val strong = when (this) {
            RAIN_SOON -> v >= 2.5
            RAIN_HEAVY -> v >= 10
            STORM -> v >= 85
            SNOW -> v >= 5
            WIND -> v >= 70
            HEAT -> v >= 38
            COLD -> v <= -10
            ICE -> v <= -2
            FOG -> v <= 100
            CHANGE -> v >= 12
        }
        return if (strong) 2 else 1
    }

    /** The more extreme of two values: the lower for cold and fog, the higher for the rest. */
    fun stronger(a: Double, b: Double) = if (this == COLD || this == FOG) min(a, b) else max(a, b)

    /** Groups the points at ON into runs; points up to two steps apart (at most 2 hours) belong to the same run. */
    fun episodes(points: List<Point>): List<Episode> {
        val runs = ArrayList<MutableList<Point>>()
        for (p in points.sortedBy { it.time }) {
            if (!isOn(p)) continue
            val run = runs.lastOrNull()
            val last = run?.last()
            if (run != null && last != null && !p.time.isAfter(last.time.plusMinutes(minOf(last.minutes * 2, 120L)))) run.add(p)
            else runs.add(mutableListOf(p))
        }
        return runs.map { run ->
            Episode(
                start = run.first().time,
                end = run.last().time.plusMinutes(run.last().minutes),
                level = run.maxOf { level(it) },
                peak = run.mapNotNull { it.value }.reduce { a, b -> stronger(a, b) },
            )
        }
    }

    companion object {
        private val FREEZING_RAIN = setOf(56, 57, 66, 67)

        fun fromKey(key: String): EventType? = values().firstOrNull { it.key == key }
    }
}
