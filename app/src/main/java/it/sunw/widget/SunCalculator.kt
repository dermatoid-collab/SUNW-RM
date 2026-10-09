package it.sunw.widget

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Solar position and sunrise/sunset times based on the NOAA Solar Calculator
 * equations (Meeus, "Astronomical Algorithms"). Accuracy is well under a minute
 * for latitudes within ±72°; no network access needed.
 */
object SunCalculator {

    /** Apparent altitude of the Sun's upper limb at rise/set: refraction (34') + semi-diameter (16'). */
    const val HORIZON_DEG = -0.833

    sealed interface Day {
        val date: LocalDate
        val solarNoon: Instant

        data class Normal(
            override val date: LocalDate,
            override val solarNoon: Instant,
            val sunrise: Instant,
            val sunset: Instant,
        ) : Day {
            val length: Duration get() = Duration.between(sunrise, sunset)
        }

        /** Sun never rises (polar night). */
        data class PolarNight(override val date: LocalDate, override val solarNoon: Instant) : Day

        /** Sun never sets (midnight sun). */
        data class PolarDay(override val date: LocalDate, override val solarNoon: Instant) : Day
    }

    private data class SolarState(val declinationRad: Double, val equationOfTimeMin: Double)

    fun day(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): Day {
        val localNoon = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
        val noon = solarNoon(localNoon, longitude)
        val latRad = Math.toRadians(latitude)

        val cosH0AtNoon = cosHourAngle(latRad, solarState(noon).declinationRad, HORIZON_DEG)
        if (cosH0AtNoon > 1) return Day.PolarNight(date, noon)
        if (cosH0AtNoon < -1) return Day.PolarDay(date, noon)

        val rise = refineEvent(noon, latRad, longitude, rising = true, HORIZON_DEG)
        val set = refineEvent(noon, latRad, longitude, rising = false, HORIZON_DEG)
        if (rise == null || set == null) {
            // Borderline polar case: the event vanishes while refining.
            return if (cosH0AtNoon > 0) Day.PolarNight(date, noon) else Day.PolarDay(date, noon)
        }
        return Day.Normal(date, noon, rise, set)
    }

    /** Morning and evening times the Sun's centre crosses [altitudeDeg]; null when it doesn't that day. */
    data class Crossing(val morning: Instant?, val evening: Instant?)

    /**
     * When the Sun passes [altitudeDeg] on [date]: −6/−12/−18 for the civil/nautical/astronomical
     * twilights, −4 and +6 for the blue and golden hours.
     */
    fun crossing(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId, altitudeDeg: Double): Crossing {
        val localNoon = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
        val noon = solarNoon(localNoon, longitude)
        val latRad = Math.toRadians(latitude)
        if (cosHourAngle(latRad, solarState(noon).declinationRad, altitudeDeg) !in -1.0..1.0) return Crossing(null, null)
        return Crossing(
            refineEvent(noon, latRad, longitude, rising = true, altitudeDeg),
            refineEvent(noon, latRad, longitude, rising = false, altitudeDeg),
        )
    }

    /** Solar azimuth in degrees clockwise from north (90 = east), at [instant]. */
    fun azimuth(instant: Instant, latitude: Double, longitude: Double): Double {
        val s = solarState(instant)
        val latRad = Math.toRadians(latitude)
        val ha = Math.toRadians(hourAngleDeg(instant, longitude, s))
        val az = Math.toDegrees(
            kotlin.math.atan2(sin(ha), cos(ha) * sin(latRad) - tan(s.declinationRad) * cos(latRad))
        )
        return norm360(az + 180)
    }

    /** Equinoxes and solstices, by the Sun's apparent ecliptic longitude. */
    enum class Season(val longitude: Double) { MARCH_EQUINOX(0.0), JUNE_SOLSTICE(90.0), SEPTEMBER_EQUINOX(180.0), DECEMBER_SOLSTICE(270.0) }

    /** The first time after [from] the Sun reaches [season]'s longitude (within a minute). */
    fun nextSeason(season: Season, from: Instant): Instant {
        fun offset(t: Long) = wrap180(apparentLongitude(Instant.ofEpochSecond(t)) - season.longitude)
        var a = from.epochSecond
        // About 1°/day: step a day at a time until the target is passed from below.
        while (!(offset(a) < 0 && offset(a + DAY_S) >= 0)) a += DAY_S
        var b = a + DAY_S
        while (b - a > 30) {
            val m = (a + b) / 2
            if (offset(m) < 0) a = m else b = m
        }
        return Instant.ofEpochSecond(b)
    }

    /** Geometric solar elevation (degrees, no refraction) at [instant]. */
    fun elevation(instant: Instant, latitude: Double, longitude: Double): Double {
        val s = solarState(instant)
        val latRad = Math.toRadians(latitude)
        val ha = Math.toRadians(hourAngleDeg(instant, longitude, s))
        val sinEl = sin(latRad) * sin(s.declinationRad) + cos(latRad) * cos(s.declinationRad) * cos(ha)
        return Math.toDegrees(asin(sinEl.coerceIn(-1.0, 1.0)))
    }

    private fun solarNoon(near: Instant, longitude: Double): Instant {
        var t = near
        repeat(3) {
            val ha = hourAngleDeg(t, longitude, solarState(t))
            t = t.plusSeconds((-ha * 240).toLong()) // 1° of hour angle = 4 min = 240 s
        }
        return t
    }

    private fun refineEvent(noon: Instant, latRad: Double, longitude: Double, rising: Boolean, altitudeDeg: Double): Instant? {
        var t = noon.plusSeconds(if (rising) -6 * 3600L else 6 * 3600L)
        repeat(5) {
            val s = solarState(t)
            val cosH0 = cosHourAngle(latRad, s.declinationRad, altitudeDeg)
            if (cosH0 !in -1.0..1.0) return null
            val h0 = Math.toDegrees(acos(cosH0))
            val target = if (rising) -h0 else h0
            val delta = target - hourAngleDeg(t, longitude, s)
            t = t.plusSeconds((delta * 240).toLong())
            if (abs(delta) < 0.001) return t
        }
        return t
    }

    private fun cosHourAngle(latRad: Double, declRad: Double, altitudeDeg: Double): Double =
        (sin(Math.toRadians(altitudeDeg)) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))

    /** Local hour angle in degrees, wrapped to [-180, 180). */
    private fun hourAngleDeg(instant: Instant, longitude: Double, s: SolarState): Double {
        val utcMinutes = Math.floorMod(instant.epochSecond, 86_400L) / 60.0
        val trueSolarMinutes = utcMinutes + s.equationOfTimeMin + 4 * longitude
        return wrap180(trueSolarMinutes / 4 - 180)
    }

    private fun apparentLongitude(instant: Instant): Double {
        val t = julianCenturies(instant)
        val l0 = norm360(280.46646 + t * (36_000.76983 + t * 0.0003032))
        val m = Math.toRadians(357.52911 + t * (35_999.05029 - 0.0001537 * t))
        val center = sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(2 * m) * (0.019993 - 0.000101 * t) + sin(3 * m) * 0.000289
        return norm360(l0 + center - 0.00569 - 0.00478 * sin(Math.toRadians(125.04 - 1934.136 * t)))
    }

    private fun julianCenturies(instant: Instant): Double =
        (instant.epochSecond / 86_400.0 + 2_440_587.5 - 2_451_545.0) / 36_525.0

    private fun solarState(instant: Instant): SolarState {
        val jd = instant.epochSecond / 86_400.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36_525.0 // Julian centuries since J2000.0

        val l0 = norm360(280.46646 + t * (36_000.76983 + t * 0.0003032))
        val m = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val mRad = Math.toRadians(m)
        val center = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3 * mRad) * 0.000289
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val apparentLong = Math.toRadians(l0 + center - 0.00569 - 0.00478 * sin(omega))

        val meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(omega))
        val declination = asin(sin(obliquity) * sin(apparentLong))

        val y = tan(obliquity / 2).let { it * it }
        val l0Rad = Math.toRadians(l0)
        val eqTime = 4 * Math.toDegrees(
            y * sin(2 * l0Rad) - 2 * e * sin(mRad) + 4 * e * y * sin(mRad) * cos(2 * l0Rad) -
                0.5 * y * y * sin(4 * l0Rad) - 1.25 * e * e * sin(2 * mRad)
        )
        return SolarState(declination, eqTime)
    }

    private const val DAY_S = 86_400L

    private fun norm360(x: Double) = x - 360 * floor(x / 360)
    private fun wrap180(x: Double) = norm360(x + 180) - 180
}
