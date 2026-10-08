package it.sunw.widget

import android.content.Context
import android.text.format.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Formatters {

    fun time(context: Context, instant: Instant, zone: ZoneId): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(instant.atZone(zone))
    }

    /** "11h 42m" */
    fun length(duration: Duration): String =
        "%dh %02dm".format(Locale.ROOT, duration.toHours(), duration.toMinutes() % 60)

    /** Day-length change vs. yesterday, e.g. "−2m 51s" or "+0m 07s". */
    fun delta(seconds: Long): String {
        val sign = if (seconds < 0) "−" else "+"
        val s = abs(seconds)
        return "%s%dm %02ds".format(Locale.ROOT, sign, s / 60, s % 60)
    }

    /** Time left as "HH:MM:SS" (hours keep counting past 24). */
    fun countdown(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(Locale.ROOT, s / 3600, s / 60 % 60, s % 60)
    }
}
