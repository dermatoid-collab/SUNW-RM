package it.sunw.widget

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Moon phase, illumination and rise/set, computed on the device.
 *
 * Lunar position from Paul Schlyter's "How to compute planetary positions" (main periodic
 * terms, about 2' accuracy); illumination from the true Sun–Moon phase angle. Checked against
 * PyEphem: illumination within 0.1 %, rise/set within ~1 minute, quarters within ~10 minutes.
 */
object MoonCalculator {

    data class Phase(
        /** Illuminated fraction of the disc, 0..1. */
        val illumination: Double,
        /** Moon − Sun ecliptic longitude, 0..360°: 0 new, 90 first quarter, 180 full, 270 last quarter. */
        val elongation: Double,
    ) {
        val waxing: Boolean get() = elongation < 180
        val name: Name get() = Name.of(elongation)
    }

    enum class Name {
        NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT;

        companion object {
            /** Principal phases get a ±10° window around their exact elongation. */
            fun of(elongation: Double): Name = when {
                elongation < 10 || elongation >= 350 -> NEW
                elongation < 80 -> WAXING_CRESCENT
                elongation < 100 -> FIRST_QUARTER
                elongation < 170 -> WAXING_GIBBOUS
                elongation < 190 -> FULL
                elongation < 260 -> WANING_GIBBOUS
                elongation < 280 -> LAST_QUARTER
                else -> WANING_CRESCENT
            }
        }
    }

    enum class Quarter(val elongation: Double) { NEW(0.0), FIRST_QUARTER(90.0), FULL(180.0), LAST_QUARTER(270.0) }

    /** Rise and set within the local calendar day; either may be missing (or both, near the poles). */
    data class RiseSet(val rise: Instant?, val set: Instant?)

    fun phase(instant: Instant): Phase {
        val d = dayNumber(instant)
        val moon = moonEcliptic(d)
        val sun = sunTrue(d)
        val cosElong = cos(rad(moon.lon - sun.lon)) * cos(rad(moon.lat))
        val elong = kotlin.math.acos(cosElong.coerceIn(-1.0, 1.0))
        val sunDistEarthRadii = sun.distanceAu * EARTH_RADII_PER_AU
        val phaseAngle = atan2(sunDistEarthRadii * sin(elong), moon.distance - sunDistEarthRadii * cosElong)
        return Phase((1 + cos(phaseAngle)) / 2, norm360(moon.lon - sun.lon))
    }

    /** Topocentric altitude of the Moon's centre in degrees, without refraction. */
    fun altitude(instant: Instant, latitude: Double, longitude: Double): Double = topocentric(instant, latitude, longitude).first

    fun riseSet(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): RiseSet {
        val start = date.atStartOfDay(zone).toInstant().epochSecond
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
        var rise: Instant? = null
        var set: Instant? = null
        var t = start
        var f = horizonOffset(t, latitude, longitude)
        while (t < end) {
            val t2 = minOf(t + SCAN_STEP_S, end)
            val f2 = horizonOffset(t2, latitude, longitude)
            if ((f < 0) != (f2 < 0)) {
                val crossing = bisect(t, t2, latitude, longitude)
                if (f < 0) { if (rise == null) rise = crossing } else { if (set == null) set = crossing }
            }
            t = t2
            f = f2
        }
        return RiseSet(rise, set)
    }

    /** How long the Moon is above the horizon during a local day, and its highest point then. */
    data class DayStats(val minutesAbove: Double, val maxAltitude: Double)

    /**
     * Scans the local day of [date] every [SCAN_STEP_S] s: minutes with the Moon above the
     * rise/set threshold (crossings interpolated) and the highest topocentric altitude.
     */
    fun dayStats(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): DayStats {
        val start = date.atStartOfDay(zone).toInstant().epochSecond
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
        var above = 0.0
        var best = -90.0
        var t = start
        var f = horizonOffset(t, latitude, longitude)
        while (t < end) {
            val t2 = minOf(t + SCAN_STEP_S, end)
            val f2 = horizonOffset(t2, latitude, longitude)
            val span = (t2 - t).toDouble()
            when {
                (f < 0) != (f2 < 0) -> {
                    val crossing = t + span * (-f) / (f2 - f)
                    above += if (f < 0) t2 - crossing else crossing - t
                }
                f > 0 -> above += span
            }
            best = maxOf(best, altitude(Instant.ofEpochSecond(t2), latitude, longitude))
            t = t2
            f = f2
        }
        return DayStats(above / 60, best)
    }

    /** Every principal phase between [from] and [to], in chronological order. */
    fun quartersBetween(from: Instant, to: Instant): List<Pair<Quarter, Instant>> {
        val out = mutableListOf<Pair<Quarter, Instant>>()
        var t = from
        while (t.isBefore(to)) {
            val next = nextQuarters(t).first()
            if (!next.second.isBefore(to)) break
            out += next
            t = next.second.plusSeconds(3600)
        }
        return out
    }

    /** The next occurrence of each principal phase after [from], in chronological order. */
    fun nextQuarters(from: Instant): List<Pair<Quarter, Instant>> =
        Quarter.values().map { it to nextQuarter(it, from) }.sortedBy { it.second }

    fun nextQuarter(quarter: Quarter, from: Instant): Instant {
        fun offset(t: Long) = norm360(phase(Instant.ofEpochSecond(t)).elongation - quarter.elongation + 180) - 180
        var a = from.epochSecond
        // Elongation grows ~12°/day; step 6 h until it passes the target from below.
        while (!(offset(a) < 0 && offset(a + QUARTER_STEP_S) >= 0)) a += QUARTER_STEP_S
        var b = a + QUARTER_STEP_S
        while (b - a > 30) {
            val m = (a + b) / 2
            if (offset(m) < 0) a = m else b = m
        }
        return Instant.ofEpochSecond(b)
    }

    // --- internals ---------------------------------------------------------------------------

    private const val SCAN_STEP_S = 600L
    private const val QUARTER_STEP_S = 6 * 3600L
    private const val EARTH_RADII_PER_AU = 23454.8
    private const val EARTH_RADIUS_KM = 6378.14

    /** Distance from the Earth's centre in km. */
    fun distanceKm(instant: Instant): Double = moonEcliptic(dayNumber(instant)).distance * EARTH_RADIUS_KM

    private data class Ecliptic(val lon: Double, val lat: Double, val distance: Double)
    private data class Sun(val lon: Double, val distanceAu: Double, val meanLon: Double)

    /** Days since 2000 Jan 0.0 UT (Schlyter's epoch). */
    private fun dayNumber(instant: Instant) = instant.epochSecond / 86_400.0 + 2_440_587.5 - 2_451_543.5

    private fun moonEcliptic(d: Double): Ecliptic {
        val n = norm360(125.1228 - 0.0529538083 * d)
        val i = 5.1454
        val w = norm360(318.0634 + 0.1643573223 * d)
        val a = 60.2666
        val e = 0.054900
        val m = norm360(115.3654 + 13.0649929509 * d)

        var ecc = m + deg(e * sin(rad(m)) * (1 + e * cos(rad(m))))
        repeat(3) { ecc -= (ecc - deg(e * sin(rad(ecc))) - m) / (1 - e * cos(rad(ecc))) }
        val xv = a * (cos(rad(ecc)) - e)
        val yv = a * sqrt(1 - e * e) * sin(rad(ecc))
        val v = deg(atan2(yv, xv))
        var r = hypot(xv, yv)

        val xh = r * (cos(rad(n)) * cos(rad(v + w)) - sin(rad(n)) * sin(rad(v + w)) * cos(rad(i)))
        val yh = r * (sin(rad(n)) * cos(rad(v + w)) + cos(rad(n)) * sin(rad(v + w)) * cos(rad(i)))
        val zh = r * sin(rad(v + w)) * sin(rad(i))
        var lon = deg(atan2(yh, xh))
        var lat = deg(atan2(zh, hypot(xh, yh)))

        // Perturbations
        val sunMean = sunTrue(d)
        val ms = norm360(356.0470 + 0.9856002585 * d)
        val lm = norm360(n + w + m)
        val dm = norm360(lm - sunMean.meanLon)
        val f = norm360(lm - n)
        lon += -1.274 * sin(rad(m - 2 * dm)) + 0.658 * sin(rad(2 * dm)) - 0.186 * sin(rad(ms)) -
            0.059 * sin(rad(2 * m - 2 * dm)) - 0.057 * sin(rad(m - 2 * dm + ms)) + 0.053 * sin(rad(m + 2 * dm)) +
            0.046 * sin(rad(2 * dm - ms)) + 0.041 * sin(rad(m - ms)) - 0.035 * sin(rad(dm)) -
            0.031 * sin(rad(m + ms)) - 0.015 * sin(rad(2 * f - 2 * dm)) + 0.011 * sin(rad(m - 4 * dm))
        lat += -0.173 * sin(rad(f - 2 * dm)) - 0.055 * sin(rad(m - f - 2 * dm)) - 0.046 * sin(rad(m + f - 2 * dm)) +
            0.033 * sin(rad(f + 2 * dm)) + 0.017 * sin(rad(2 * m + f))
        r += -0.58 * cos(rad(m - 2 * dm)) - 0.46 * cos(rad(2 * dm))
        return Ecliptic(norm360(lon), lat, r)
    }

    private fun sunTrue(d: Double): Sun {
        val ms = norm360(356.0470 + 0.9856002585 * d)
        val ws = 282.9404 + 4.70935e-5 * d
        val e = 0.016709 - 1.151e-9 * d
        val ecc = ms + deg(e * sin(rad(ms)) * (1 + e * cos(rad(ms))))
        val xv = cos(rad(ecc)) - e
        val yv = sqrt(1 - e * e) * sin(rad(ecc))
        return Sun(norm360(deg(atan2(yv, xv)) + ws), hypot(xv, yv), norm360(ms + ws))
    }

    /** (altitude°, horizontal parallax°), topocentric, no refraction. */
    private fun topocentric(instant: Instant, latitude: Double, longitude: Double): Pair<Double, Double> {
        val d = dayNumber(instant)
        val moon = moonEcliptic(d)
        val ecl = rad(23.4393 - 3.563e-7 * d)
        val x = cos(rad(moon.lon)) * cos(rad(moon.lat))
        val y = sin(rad(moon.lon)) * cos(rad(moon.lat))
        val z = sin(rad(moon.lat))
        val ye = y * cos(ecl) - z * sin(ecl)
        val ze = y * sin(ecl) + z * cos(ecl)
        val ra = deg(atan2(ye, x))
        val dec = deg(atan2(ze, hypot(x, ye)))

        // Sidereal time: the Sun's mean longitude already advances 0.9856°/day, so UT counts at 15°/h.
        val gmst0 = sunTrue(d).meanLon + 180
        val ut = Math.floorMod(instant.epochSecond, 86_400L) / 3600.0
        val ha = rad(gmst0 + ut * 15 + longitude - ra)
        val alt = deg(asin(sin(rad(latitude)) * sin(rad(dec)) + cos(rad(latitude)) * cos(rad(dec)) * cos(ha)))
        val parallax = deg(asin(1 / moon.distance))
        return (alt - parallax * cos(rad(alt))) to parallax
    }

    /**
     * Altitude above the rise/set threshold: upper limb on the horizon with standard refraction
     * (−34') and the Moon's semi-diameter (0.2725 × parallax).
     */
    private fun horizonOffset(t: Long, latitude: Double, longitude: Double): Double {
        val (alt, parallax) = topocentric(Instant.ofEpochSecond(t), latitude, longitude)
        return alt - (-0.5667 - 0.2725 * parallax)
    }

    private fun bisect(t0: Long, t1: Long, latitude: Double, longitude: Double): Instant {
        var a = t0
        var b = t1
        val fa = horizonOffset(a, latitude, longitude) < 0
        while (b - a > 5) {
            val m = (a + b) / 2
            if ((horizonOffset(m, latitude, longitude) < 0) == fa) a = m else b = m
        }
        return Instant.ofEpochSecond((a + b) / 2)
    }

    private fun rad(x: Double) = Math.toRadians(x)
    private fun deg(x: Double) = Math.toDegrees(x)
    private fun norm360(x: Double) = x - 360 * floor(x / 360)
}
