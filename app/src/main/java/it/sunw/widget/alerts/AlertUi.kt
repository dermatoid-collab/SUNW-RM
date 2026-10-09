package it.sunw.widget.alerts

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.widget.TextView
import it.sunw.widget.Place
import it.sunw.widget.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How the Civil Protection alerts look in the app: coloured strip, short text, details. */
object AlertUi {

    /** Alert colours as on the bulletin map (yellow, orange, red), with dark text on them. */
    fun color(level: Bulletin.Level): Int = when (level) {
        Bulletin.Level.YELLOW -> 0xFFF8E45C.toInt()
        Bulletin.Level.ORANGE -> 0xFFFF9F43.toInt()
        Bulletin.Level.RED -> 0xFFE5484D.toInt()
    }

    const val TEXT_ON_COLOR = 0xFF1C1D22.toInt()

    fun levelName(context: Context, level: Bulletin.Level): String = context.getString(
        when (level) {
            Bulletin.Level.YELLOW -> R.string.alert_yellow
            Bulletin.Level.ORANGE -> R.string.alert_orange
            Bulletin.Level.RED -> R.string.alert_red
        },
    )

    fun riskName(context: Context, risk: Bulletin.Risk): String = context.getString(
        when (risk) {
            Bulletin.Risk.HYDROGEOLOGICAL -> R.string.risk_hydrogeological
            Bulletin.Risk.THUNDERSTORMS -> R.string.risk_thunderstorms
            Bulletin.Risk.HYDRAULIC -> R.string.risk_hydraulic
        },
    )

    /** "Orange alert · hydrogeological, thunderstorms" (risks of the highest level first). */
    fun summary(context: Context, warnings: List<Bulletin.Warning>): String {
        val top = warnings.maxOf { it.level }
        val risks = warnings.sortedByDescending { it.level }.map { riskName(context, it.risk) }.distinct()
        return context.getString(R.string.alert_summary, levelName(context, top), risks.joinToString(", "))
    }

    /** The place's alert zone, or null outside Italy. */
    fun zone(context: Context, place: Place): String? = AlertZones.get(context).zoneAt(place.latitude, place.longitude)

    /**
     * Fills [view] with the place's alert for today (or else tomorrow) and makes it open the
     * details; hides it when there is none. Returns whether it is shown.
     */
    fun bind(view: TextView, activity: Activity, place: Place, bulletin: Bulletin?, today: LocalDate): Boolean {
        val zone = if (bulletin != null) zone(activity, place) else null
        val day = if (bulletin != null && zone != null) {
            listOf(today, today.plusDays(1)).firstOrNull { bulletin.forZone(zone, it).isNotEmpty() }
        } else {
            null
        }
        if (bulletin == null || zone == null || day == null) {
            view.visibility = android.view.View.GONE
            return false
        }
        val warnings = bulletin.forZone(zone, day)
        view.text = "⚠ " + activity.getString(
            if (day == today) R.string.alert_today else R.string.alert_tomorrow,
            summary(activity, warnings),
        )
        view.setTextColor(TEXT_ON_COLOR)
        view.background = GradientDrawable().apply {
            cornerRadius = 10 * activity.resources.displayMetrics.density
            setColor(color(warnings.maxOf { it.level }))
        }
        view.visibility = android.view.View.VISIBLE
        view.setOnClickListener { showDetails(activity, bulletin, zone, today) }
        return true
    }

    /** Zone, today and tomorrow with every risk, bulletin time and source. */
    fun showDetails(activity: Activity, bulletin: Bulletin, zone: String, today: LocalDate) {
        val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
        val lines = mutableListOf(activity.getString(R.string.alert_zone, zone), "")
        for (day in listOf(today, today.plusDays(1))) {
            val warnings = bulletin.forZone(zone, day)
            lines += dayFormat.format(day).replaceFirstChar { it.titlecase() } + ":"
            lines += if (warnings.isEmpty()) {
                "  " + activity.getString(R.string.alert_none)
            } else {
                warnings.joinToString("\n") { "  • " + levelName(activity, it.level) + " · " + riskName(activity, it.risk) }
            }
        }
        bulletin.sent?.let {
            lines += ""
            lines += activity.getString(
                R.string.alert_issued,
                DateTimeFormatter.ofPattern("d MMMM HH:mm", Locale.getDefault()).format(it.atZoneSameInstant(ZoneId.systemDefault())),
            )
        }
        lines += ""
        lines += activity.getString(R.string.alert_source)
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.alert_title)
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.alert_open) { _, _ ->
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MAP_URL))) }
            }
            .show()
    }

    private const val MAP_URL = "https://mappe.protezionecivile.gov.it/"
}
