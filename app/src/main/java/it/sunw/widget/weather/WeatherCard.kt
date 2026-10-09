package it.sunw.widget.weather

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import it.sunw.widget.Palette
import it.sunw.widget.R
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Fills the weather card of the main page: today fixed on top (now, rain/wind/UV for the day,
 * min and max), then the following days on one row (icon, max/min, weekday, dd/MM) in the same
 * style as the Moon week. Tapping today or a day opens [WeatherDayActivity]. The status line is
 * used only while loading or on errors.
 */
class WeatherCard(private val activity: Activity) {

    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).roundToInt()
    private fun <T : View> find(id: Int): T = activity.findViewById(id)

    fun showStatus(palette: Palette, message: String) {
        style(palette)
        find<View>(R.id.weather_now).visibility = View.GONE
        find<View>(R.id.weather_divider).visibility = View.GONE
        find<LinearLayout>(R.id.weather_days).removeAllViews()
        find<TextView>(R.id.weather_status).apply { text = message; visibility = View.VISIBLE }
    }

    fun show(palette: Palette, forecast: Forecast, now: Instant) {
        style(palette)
        val local = forecast.localTime(now)
        val hour = forecast.hourAt(local)
        val today = forecast.days.firstOrNull { it.date == local.toLocalDate() } ?: forecast.days.firstOrNull()
        if (hour == null || today == null) {
            showStatus(palette, activity.getString(R.string.weather_unavailable))
            return
        }
        find<View>(R.id.weather_now).apply {
            visibility = View.VISIBLE
            setOnClickListener { openDay(today.date) }
        }
        find<View>(R.id.weather_divider).visibility = View.VISIBLE
        find<View>(R.id.weather_status).visibility = View.GONE

        WeatherIcons.show(find(R.id.weather_icon), hour.condition, !hour.isDaylight, palette.text)
        find<TextView>(R.id.weather_temp).apply { text = deg(hour.temperature); setTextColor(palette.text) }
        find<TextView>(R.id.weather_desc).apply {
            text = activity.getString(hour.condition.label)
            setTextColor(palette.text)
        }
        find<TextView>(R.id.weather_sub).apply {
            text = activity.getString(
                R.string.weather_today_facts,
                today.precipitationProbability, mm(today.precipitation),
                windArrow(today.windDirection), today.windSpeedMax.roundToInt(), today.uvIndex,
            )
            setTextColor(palette.textSecondary)
        }
        // Max (yellow) above min (light blue), to the right.
        find<LinearLayout>(R.id.weather_today_pills).apply {
            removeAllViews()
            addView(temperaturePill(activity, today.temperatureMax, 15f, 40, MAX_PILL))
            addView(temperaturePill(activity, today.temperatureMin, 15f, 40, MIN_PILL).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(5)
            })
        }

        showTodaySpot(palette, today.rainspot)
        showWeek(palette, forecast.days.filter { it.date.isAfter(today.date) }.take(DAYS))
    }

    /**
     * Today's rainspot beside the pills, sized to their height (a radar icon when MeteoBlue sends
     * none); tap → radar and meteogram.
     */
    private fun showTodaySpot(palette: Palette, spot: String?) {
        val view = find<ImageView>(R.id.weather_today_spot)
        view.visibility = View.VISIBLE
        view.setOnClickListener { activity.startActivity(Intent(activity, MeteogramActivity::class.java)) }
        val pills = find<View>(R.id.weather_today_pills)
        pills.post {
            val size = pills.height.takeIf { it > 0 } ?: dp(52)
            view.layoutParams = view.layoutParams.apply { width = size; height = size }
            if (spot != null) {
                view.clearColorFilter()
                view.setPadding(0, 0, 0, 0)
                view.setImageBitmap(Rainspot.draw(size, spot))
            } else {
                view.setPadding(size / 10, size / 10, size / 10, size / 10)
                view.scaleType = ImageView.ScaleType.FIT_CENTER
                view.setImageResource(R.drawable.ic_radar)
                view.setColorFilter(palette.text)
            }
        }
    }

    /** Following days on one row: icon, max/min, weekday, dd/MM. Each column opens the day. */
    private fun showWeek(palette: Palette, days: List<Forecast.Day>) {
        val row = find<LinearLayout>(R.id.weather_days)
        row.removeAllViews()
        row.orientation = LinearLayout.HORIZONTAL
        val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM")
        val ripple = TypedValue().also {
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }.resourceId
        for (day in days) {
            val cell = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(0, dp(4), 0, dp(4))
                setBackgroundResource(ripple)
                isClickable = true
                contentDescription = activity.getString(day.condition.label)
                setOnClickListener { openDay(day.date) }
            }
            cell.addView(ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                WeatherIcons.show(this, day.condition, night = false, color = palette.text)
            })
            cell.addView(label("${deg(day.temperatureMax)} ${deg(day.temperatureMin)}", 12f, palette.text, dp(4)))
            cell.addView(label(dayFormat.format(day.date).trimEnd('.').replaceFirstChar { it.titlecase() }, 11f, palette.textSecondary))
            cell.addView(label(dateFormat.format(day.date), 10f, palette.textSecondary).apply { alpha = 0.75f })
            row.addView(cell)
        }
    }

    private fun openDay(date: LocalDate) {
        activity.startActivity(Intent(activity, WeatherDayActivity::class.java).putExtra(WeatherDayActivity.EXTRA_DATE, date.toString()))
    }

    private fun style(palette: Palette) {
        find<LinearLayout>(R.id.weather_card).background =
            (activity.getDrawable(R.drawable.card_background)!!.mutate() as GradientDrawable).apply { setColor(palette.cardBackground) }
        find<View>(R.id.weather_divider).setBackgroundColor(Palette.blend(palette.cardBackground, Color.WHITE, 0.08f))
        find<TextView>(R.id.weather_status).setTextColor(palette.textSecondary)
    }

    /** Full-width centred label, so it lines up under the icon. */
    private fun label(value: String, sizeSp: Float, color: Int, topPadPx: Int = 0) = TextView(activity).apply {
        text = value
        textSize = sizeSp
        gravity = Gravity.CENTER_HORIZONTAL
        maxLines = 1
        setTextColor(color)
        setPadding(0, topPadPx, 0, 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    companion object {
        /** Days shown after today (the basic-day package covers today plus 6). */
        const val DAYS = 7
        private const val PILL_TEXT = 0xFF1C1D22.toInt()
        private val TEMPERATURE_STOPS = listOf(
            0.0 to 0xFF7FB2FF.toInt(), 8.0 to 0xFF8FDCD0.toInt(), 14.0 to 0xFFA6E39A.toInt(),
            19.0 to 0xFFE4F39A.toInt(), 24.0 to 0xFFFFE27A.toInt(), 29.0 to 0xFFFFB36B.toInt(), 34.0 to 0xFFFF7B6B.toInt(),
        )

        fun temperatureColor(celsius: Double): Int {
            if (celsius <= TEMPERATURE_STOPS.first().first) return TEMPERATURE_STOPS.first().second
            for ((a, b) in TEMPERATURE_STOPS.zipWithNext()) {
                if (celsius <= b.first) return Palette.blend(a.second, b.second, ((celsius - a.first) / (b.first - a.first)).toFloat())
            }
            return TEMPERATURE_STOPS.last().second
        }

        /** Temperature "pill" coloured like Meteoblue's: blue (cold) → green → yellow → red (hot). */
        /** Fixed pill colours for today's max and min on the main page. */
        const val MAX_PILL = 0xFFFFE27A.toInt()
        const val MIN_PILL = 0xFF9FD4FF.toInt()

        /** [color] overrides the temperature scale (null: coloured by temperature). */
        fun temperaturePill(context: Context, celsius: Double, sizeSp: Float, widthDp: Int, color: Int? = null): TextView {
            val density = context.resources.displayMetrics.density
            return TextView(context).apply {
                text = deg(celsius)
                textSize = sizeSp
                gravity = Gravity.CENTER
                setTextColor(PILL_TEXT)
                setPadding(0, (2 * density).roundToInt(), 0, (2 * density).roundToInt())
                background = GradientDrawable().apply { cornerRadius = 7 * density; setColor(color ?: temperatureColor(celsius)) }
                layoutParams = LinearLayout.LayoutParams((widthDp * density).roundToInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        }

        fun deg(celsius: Double) = "${celsius.roundToInt()}°"

        fun mm(amount: Double): String =
            if (amount < 0.05) "—" else String.format(Locale.getDefault(), "%.1f mm", amount)

        /** Arrow pointing where the wind blows to (MeteoBlue gives the direction it comes from). */
        fun windArrow(fromDegrees: Int): String {
            val to = (fromDegrees + 180) % 360
            return listOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")[((to + 22) % 360) / 45]
        }
    }
}
