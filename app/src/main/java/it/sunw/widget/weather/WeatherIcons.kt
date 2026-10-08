package it.sunw.widget.weather

import android.widget.ImageView
import it.sunw.widget.R

/**
 * Monochrome weather icons from Google's Material Symbols Rounded, weight 300 (Apache 2.0, see
 * `licenses/MATERIAL-SYMBOLS.txt`), converted to vector drawables and tinted at runtime with
 * the theme's text colour.
 */
object WeatherIcons {

    fun icon(condition: Condition, night: Boolean): Int = when (condition) {
        Condition.CLEAR, Condition.MOSTLY_CLEAR -> if (night) R.drawable.wx_clear_night else R.drawable.wx_sunny
        Condition.PARTLY_CLOUDY -> if (night) R.drawable.wx_partly_cloudy_night else R.drawable.wx_partly_cloudy_day
        Condition.MOSTLY_CLOUDY -> R.drawable.wx_filter_drama
        Condition.OVERCAST -> R.drawable.wx_cloud
        Condition.FOG -> R.drawable.wx_foggy
        Condition.LIGHT_RAIN -> R.drawable.wx_rainy_light
        Condition.SHOWERS, Condition.RAIN -> R.drawable.wx_rainy
        Condition.HEAVY_RAIN -> R.drawable.wx_rainy_heavy
        Condition.SLEET -> R.drawable.wx_weather_mix
        Condition.SNOW_SHOWERS -> R.drawable.wx_cloudy_snowing
        Condition.SNOW -> R.drawable.wx_snowflake
        Condition.THUNDERSTORM -> R.drawable.wx_thunderstorm
    }

    /** Shows the icon for [condition] in [view], tinted with [color]. */
    fun show(view: ImageView, condition: Condition, night: Boolean, color: Int) {
        view.setImageResource(icon(condition, night))
        view.setColorFilter(color)
    }
}
