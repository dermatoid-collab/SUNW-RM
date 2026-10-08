package it.sunw.widget.weather

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import it.sunw.widget.Palette
import it.sunw.widget.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Fills the weather card of the main page from a [Forecast] (or shows a status message). */
class WeatherCard(private val activity: Activity) {

    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).roundToInt()
    private fun <T : View> find(id: Int): T = activity.findViewById(id)

    fun showStatus(palette: Palette, message: String) {
        style(palette)
        find<View>(R.id.weather_now).visibility = View.GONE
        find<View>(R.id.weather_today_facts).visibility = View.GONE
        find<View>(R.id.weather_divider).visibility = View.GONE
        find<LinearLayout>(R.id.weather_days).removeAllViews()
        find<TextView>(R.id.weather_status).apply { text = message; textSize = 13f }
    }

    fun show(palette: Palette, forecast: Forecast, now: Instant, zone: ZoneId, stale: Boolean) {
        style(palette)
        val local = forecast.localTime(now)
        val hour = forecast.hourAt(local)
        val today = forecast.days.firstOrNull { it.date == local.toLocalDate() } ?: forecast.days.firstOrNull()
        if (hour == null || today == null) {
            showStatus(palette, activity.getString(R.string.weather_unavailable))
            return
        }
        find<View>(R.id.weather_now).visibility = View.VISIBLE
        find<View>(R.id.weather_today_facts).visibility = View.VISIBLE
        find<View>(R.id.weather_divider).visibility = View.VISIBLE

        find<ImageView>(R.id.weather_icon).setImageBitmap(WeatherIcons.draw(dp(76), hour.condition, !hour.isDaylight))
        find<TextView>(R.id.weather_temp).apply { text = deg(hour.temperature); setTextColor(palette.text) }
        find<TextView>(R.id.weather_desc).apply {
            text = activity.getString(hour.condition.label)
            setTextColor(palette.text)
        }
        find<TextView>(R.id.weather_sub).apply {
            text = activity.getString(R.string.weather_felt_uv, deg(hour.feltTemperature), hour.uvIndex)
            setTextColor(palette.textSecondary)
        }
        find<LinearLayout>(R.id.weather_today_pills).apply {
            removeAllViews()
            addView(pill(today.temperatureMax, 17f, 46))
            addView(pill(today.temperatureMin, 17f, 46).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(5) })
        }
        find<TextView>(R.id.weather_today_facts).apply {
            text = activity.getString(
                R.string.weather_today_facts,
                today.precipitationProbability, mm(today.precipitation),
                windArrow(today.windDirection), today.windSpeedMax.roundToInt(), today.uvIndex,
            )
            setTextColor(palette.textSecondary)
        }

        val rows = find<LinearLayout>(R.id.weather_days)
        rows.removeAllViews()
        val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM")
        val divider = Palette.blend(palette.cardBackground, Color.WHITE, 0.06f)
        forecast.days.filter { it.date.isAfter(today.date) }.forEachIndexed { i, day ->
            if (i > 0) rows.addView(View(activity).apply {
                setBackgroundColor(divider)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            })
            rows.addView(dayRow(palette, day, forecast.nightCondition(day.date) ?: day.condition, dayFormat, dateFormat))
        }

        val updated = DateTimeFormatter.ofPattern("HH:mm").format(forecast.fetchedAt.atZone(zone))
        find<TextView>(R.id.weather_status).apply {
            text = activity.getString(if (stale) R.string.weather_source_stale else R.string.weather_source, updated)
            textSize = 11f
        }
    }

    private fun dayRow(
        palette: Palette,
        day: Forecast.Day,
        night: Condition,
        dayFormat: DateTimeFormatter,
        dateFormat: DateTimeFormatter,
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(text(dayFormat.format(day.date).trimEnd('.').replaceFirstChar { it.titlecase() }, 16f, palette.text))
            addView(text(dateFormat.format(day.date), 12f, palette.textSecondary))
        })
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                setImageBitmap(WeatherIcons.draw(dp(42), day.condition, night = false))
            })
            addView(text("${day.precipitationProbability}%", 11f, RAIN_TEXT, Gravity.CENTER_HORIZONTAL))
        })
        row.addView(ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(6) }
            setImageBitmap(WeatherIcons.draw(dp(38), night, night = true))
            contentDescription = activity.getString(night.label)
        })
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(pill(day.temperatureMax, 14f, 40))
            addView(pill(day.temperatureMin, 14f, 40).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(4) })
        })
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(text("${windArrow(day.windDirection)} ${day.windSpeedMax.roundToInt()} km/h", 12f, palette.text, Gravity.END))
            addView(text(mm(day.precipitation), 12f, palette.text, Gravity.END))
            addView(text("UV ${day.uvIndex}", 12f, palette.textSecondary, Gravity.END))
        })
        return row
    }

    private fun style(palette: Palette) {
        find<LinearLayout>(R.id.weather_card).background =
            (activity.getDrawable(R.drawable.card_background)!!.mutate() as GradientDrawable).apply { setColor(palette.cardBackground) }
        find<View>(R.id.weather_divider).setBackgroundColor(Palette.blend(palette.cardBackground, Color.WHITE, 0.08f))
        find<TextView>(R.id.weather_status).setTextColor(palette.textSecondary)
    }

    private fun text(value: String, sizeSp: Float, color: Int, gravity: Int = Gravity.START) = TextView(activity).apply {
        text = value
        textSize = sizeSp
        maxLines = 1
        this.gravity = gravity
        setTextColor(color)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    /** Temperature "pill" coloured like Meteoblue's: blue (cold) → green → yellow → red (hot). */
    private fun pill(celsius: Double, sizeSp: Float, widthDp: Int) = TextView(activity).apply {
        text = deg(celsius)
        textSize = sizeSp
        gravity = Gravity.CENTER
        setTextColor(PILL_TEXT)
        setPadding(0, dp(2), 0, dp(2))
        background = GradientDrawable().apply { cornerRadius = dp(7).toFloat(); setColor(temperatureColor(celsius)) }
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    companion object {
        private const val RAIN_TEXT = 0xFF8FB8F2.toInt()
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
