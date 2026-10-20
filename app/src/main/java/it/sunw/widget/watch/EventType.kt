package it.sunw.widget.watch

import it.sunw.widget.R
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One value of an event's series, valid for [minutes] minutes from [time]; [value] null means the data is missing. */
class Point(val time: LocalDateTime, val value: Double?, val aux: Double? = null, val minutes: Long = 60, val flag: Boolean = false)

/** A run of points over the ON threshold: when it starts and ends, how strong it gets and its strongest value. */
class Episode(val start: LocalDateTime, val end: LocalDateTime, val level: Int, val peak: Double)

/**
 * The weather events the watch reports, each with its two thresholds (ON to start, OFF to end), how long
 * before it starts it is reported ([Thresholds.lead]) and its icon. [level] 2 is the strong version of the event
 * (an escalation notifies at once). The thresholds are starting values to calibrate on the provider.
 */
enum class EventType(val key: String, val title: Int, val icon: Int, val thresholds: String) {
    RAIN_SOON("rain_soon", R.string.we_rain_soon, R.drawable.wx_rainy_light, "ON p>=70% & >=0.2 mm/h; OFF p<50% | <0.1 mm/h"),
    RAIN_HEAVY("rain_heavy", R.string.we_rain_heavy, R.drawable.wx_rainy_heavy, "ON >=5 mm/h; OFF <3 mm/h"),
    STORM("storm", R.string.we_storm, R.drawable.wx_thunderstorm, "ON p>=60% & thunderstorm code; OFF p<40% | no thunderstorm code"),
    HAIL("hail", R.string.we_hail, R.drawable.wx_hail, "ON code 96/99 | (thunderstorm p>=40% & CAPE>=1500 & 0 C level<=4200 m); OFF code<96 & CAPE<1000"),
    SNOW("snow", R.string.we_snow, R.drawable.wx_cloudy_snowing, "ON >=1 cm in 3 h; OFF <0.5 cm in 3 h"),
    WIND("wind", R.string.we_wind, R.drawable.wx_air, "ON gusts>=50 km/h; OFF <40 km/h"),
    HEAT("heat", R.string.we_heat, R.drawable.wx_sunny, "ON >=35 C; OFF <33 C"),
    COLD("cold", R.string.we_cold, R.drawable.wx_thermostat, "ON <=-5 C; OFF >-3 C"),
    ICE("ice", R.string.we_ice, R.drawable.wx_snowflake, "ON <=0 C & wet surface; OFF >2 C | dry"),
    FOG("fog", R.string.we_fog, R.drawable.wx_foggy, "ON visibility<=200 m; OFF >500 m"),
    CHANGE("change", R.string.we_change, R.drawable.wx_swap_vert, "ON |dT 12 h|>=8 C; OFF <5 C");

    /** What the user can adjust for this event: thresholds (in the order they are shown) and the lead time. */
    val params: List<Param> by lazy { paramsOf(this) }

    fun param(key: String): Param = params.first { it.key == key }

    /** Whether the OFF and strong thresholds lie below the ON one (cold, ice, fog: the lower, the stronger). */
    val lowerIsStronger: Boolean get() = this == COLD || this == ICE || this == FOG

    /**
     * Null when [values] are acceptable: every value in its range and the thresholds in order, OFF on the milder side of
     * ON and ON on the milder side of strong (OFF ≤ ON ≤ strong; the reverse for cold, ice and fog), so the hysteresis
     * band is never upside down.
     */
    fun invalid(values: Map<String, Double>): String? {
        for (p in params) {
            val v = values[p.key] ?: return p.key
            if (v < p.min || v > p.max) return p.key
        }
        fun v(key: String) = values[key]!!
        fun ordered(off: String, on: String, strong: String?): String? {
            val up = !lowerIsStronger
            if (if (up) v(off) > v(on) else v(off) < v(on)) return off
            if (strong != null && (if (up) v(on) > v(strong) else v(on) < v(strong))) return strong
            return null
        }
        return when (this) {
            RAIN_SOON -> ordered("prob_off", "prob_on", null) ?: ordered("off", "on", "strong")
            HAIL -> ordered("cape_off", "cape_on", "cape_strong")
            else -> ordered("off", "on", "strong")
        }
    }

    /** The values to judge, in time order, from Open-Meteo's data. */
    fun series(d: WatchData, th: Thresholds = Thresholds.DEFAULTS): List<Point> = when (this) {
        RAIN_SOON -> rainSeries(d)
        RAIN_HEAVY -> d.hours.map { Point(it.time, it.precipitation) }
        STORM -> d.hours.map { h -> Point(h.time, if (h.probability != null && h.code != null) h.probability else null, h.code?.toDouble()) }
        HAIL -> d.hours.map { h ->
            val code = h.code
            val onFlag = code != null && (code == 96 || code == 99 ||
                (code >= 95 && (h.probability ?: 0.0) >= th.value(HAIL, "prob_min") && (h.cape ?: 0.0) >= th.value(HAIL, "cape_on") &&
                    (h.freezingLevel ?: Double.MAX_VALUE) <= th.value(HAIL, "freezing")))
            Point(h.time, if (code == null) null else h.cape ?: 0.0, code?.toDouble(), 60, onFlag)
        }
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
    fun isOn(p: Point, th: Thresholds = Thresholds.DEFAULTS): Boolean {
        val v = p.value ?: return false
        return when (this) {
            RAIN_SOON -> v >= th.value(this, "on") && (p.aux ?: 0.0) >= th.value(this, "prob_on")
            RAIN_HEAVY, SNOW, WIND, HEAT, CHANGE -> v >= th.value(this, "on")
            STORM -> v >= th.value(this, "on") && (p.aux ?: 0.0) >= 95
            HAIL -> p.flag
            COLD, FOG -> v <= th.value(this, "on")
            ICE -> v <= th.value(this, "on") && p.aux == 1.0
        }
    }

    /** Whether the (valid) point meets the OFF condition (not merely "not ON": between the two the state is kept). */
    fun isOff(p: Point, th: Thresholds = Thresholds.DEFAULTS): Boolean {
        val v = p.value ?: return false
        return when (this) {
            RAIN_SOON -> (p.aux ?: 0.0) < th.value(this, "prob_off") || v < th.value(this, "off")
            RAIN_HEAVY, SNOW, WIND, HEAT, CHANGE -> v < th.value(this, "off")
            STORM -> v < th.value(this, "off") || (p.aux ?: 0.0) < 95
            HAIL -> (p.aux ?: 0.0) < 96 && v < th.value(this, "cape_off")
            COLD, FOG -> v > th.value(this, "off")
            ICE -> v > th.value(this, "off") || p.aux != 1.0
        }
    }

    /** 1 for the event, 2 for its strong version. */
    fun level(p: Point, th: Thresholds = Thresholds.DEFAULTS): Int {
        val v = p.value ?: return 1
        val strong = when (this) {
            RAIN_SOON, RAIN_HEAVY, STORM, SNOW, WIND, HEAT, CHANGE -> v >= th.value(this, "strong")
            HAIL -> p.aux == 99.0 || v >= th.value(this, "cape_strong")
            COLD, ICE, FOG -> v <= th.value(this, "strong")
        }
        return if (strong) 2 else 1
    }

    /** The more extreme of two values: the lower for cold and fog, the higher for the rest. */
    fun stronger(a: Double, b: Double) = if (this == COLD || this == FOG) min(a, b) else max(a, b)

    /** Groups the points at ON into runs; points up to two steps apart (at most 2 hours) belong to the same run. */
    fun episodes(points: List<Point>, th: Thresholds = Thresholds.DEFAULTS): List<Episode> {
        val runs = ArrayList<MutableList<Point>>()
        for (p in points.sortedBy { it.time }) {
            if (!isOn(p, th)) continue
            val run = runs.lastOrNull()
            val last = run?.last()
            if (run != null && last != null && !p.time.isAfter(last.time.plusMinutes(minOf(last.minutes * 2, 120L)))) run.add(p)
            else runs.add(mutableListOf(p))
        }
        return runs.map { run ->
            Episode(
                start = run.first().time,
                end = run.last().time.plusMinutes(run.last().minutes),
                level = run.maxOf { level(it, th) },
                peak = run.mapNotNull { it.value }.reduce { a, b -> stronger(a, b) },
            )
        }
    }

    companion object {
        private val FREEZING_RAIN = setOf(56, 57, 66, 67)

        private fun lead(minutes: Double) = Param("lead", R.string.pr_lead, "min", minutes, 0.0, 2880.0)

        /** The starting values (the thresholds of the design) and the ranges allowed, per event. */
        private fun paramsOf(type: EventType): List<Param> = when (type) {
            RAIN_SOON -> listOf(
                Param("prob_on", R.string.pr_prob_on, "%", 70.0, 0.0, 100.0), Param("prob_off", R.string.pr_prob_off, "%", 50.0, 0.0, 100.0),
                Param("on", R.string.pr_int_on, "mm/h", 0.2, 0.0, 50.0, 1), Param("off", R.string.pr_int_off, "mm/h", 0.1, 0.0, 50.0, 1),
                Param("strong", R.string.pr_strong, "mm/h", 2.5, 0.0, 100.0, 1), lead(60.0),
            )
            RAIN_HEAVY -> listOf(
                Param("on", R.string.pr_on, "mm/h", 5.0, 0.0, 200.0, 1), Param("off", R.string.pr_off, "mm/h", 3.0, 0.0, 200.0, 1),
                Param("strong", R.string.pr_strong, "mm/h", 10.0, 0.0, 300.0, 1), lead(180.0),
            )
            STORM -> listOf(
                Param("on", R.string.pr_prob_on, "%", 60.0, 0.0, 100.0), Param("off", R.string.pr_prob_off, "%", 40.0, 0.0, 100.0),
                Param("strong", R.string.pr_strong, "%", 85.0, 0.0, 100.0), lead(180.0),
            )
            HAIL -> listOf(
                Param("cape_on", R.string.pr_cape_on, "J/kg", 1500.0, 0.0, 6000.0), Param("cape_off", R.string.pr_cape_off, "J/kg", 1000.0, 0.0, 6000.0),
                Param("cape_strong", R.string.pr_cape_strong, "J/kg", 2500.0, 0.0, 8000.0),
                Param("prob_min", R.string.pr_prob_min, "%", 40.0, 0.0, 100.0), Param("freezing", R.string.pr_freezing, "m", 4200.0, 0.0, 8000.0),
                lead(180.0),
            )
            SNOW -> listOf(
                Param("on", R.string.pr_on, "cm", 1.0, 0.0, 100.0, 1), Param("off", R.string.pr_off, "cm", 0.5, 0.0, 100.0, 1),
                Param("strong", R.string.pr_strong, "cm", 5.0, 0.0, 200.0, 1), lead(360.0),
            )
            WIND -> listOf(
                Param("on", R.string.pr_on, "km/h", 50.0, 10.0, 250.0), Param("off", R.string.pr_off, "km/h", 40.0, 10.0, 250.0),
                Param("strong", R.string.pr_strong, "km/h", 70.0, 10.0, 300.0), lead(360.0),
            )
            HEAT -> listOf(
                Param("on", R.string.pr_on, "°C", 35.0, 20.0, 60.0), Param("off", R.string.pr_off, "°C", 33.0, 20.0, 60.0),
                Param("strong", R.string.pr_strong, "°C", 38.0, 20.0, 60.0), lead(1440.0),
            )
            COLD -> listOf(
                Param("on", R.string.pr_on, "°C", -5.0, -60.0, 10.0), Param("off", R.string.pr_off, "°C", -3.0, -60.0, 10.0),
                Param("strong", R.string.pr_strong, "°C", -10.0, -60.0, 10.0), lead(1440.0),
            )
            ICE -> listOf(
                Param("on", R.string.pr_on, "°C", 0.0, -30.0, 10.0), Param("off", R.string.pr_off, "°C", 2.0, -30.0, 10.0),
                Param("strong", R.string.pr_strong, "°C", -2.0, -30.0, 10.0), lead(360.0),
            )
            FOG -> listOf(
                Param("on", R.string.pr_on, "m", 200.0, 0.0, 5000.0), Param("off", R.string.pr_off, "m", 500.0, 0.0, 10000.0),
                Param("strong", R.string.pr_strong, "m", 100.0, 0.0, 5000.0), lead(180.0),
            )
            CHANGE -> listOf(
                Param("on", R.string.pr_on, "°C", 8.0, 1.0, 40.0), Param("off", R.string.pr_off, "°C", 5.0, 1.0, 40.0),
                Param("strong", R.string.pr_strong, "°C", 12.0, 1.0, 40.0), lead(720.0),
            )
        }

        fun fromKey(key: String): EventType? = values().firstOrNull { it.key == key }
    }
}
