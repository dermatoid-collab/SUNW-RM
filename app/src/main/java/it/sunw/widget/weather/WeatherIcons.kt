package it.sunw.widget.weather

import android.widget.ImageView
import it.sunw.widget.R

/**
 * Monochrome weather icons from Lucide (ISC licence, see `licenses/LUCIDE.txt`), converted to
 * vector drawables with white strokes and tinted at runtime with the theme's text colour.
 */
object WeatherIcons {

    fun icon(condition: Condition, night: Boolean): Int = when (condition) {
        Condition.CLEAR -> if (night) R.drawable.wx_moon else R.drawable.wx_sun
        Condition.MOSTLY_CLEAR -> if (night) R.drawable.wx_moon_star else R.drawable.wx_sun_medium
        Condition.PARTLY_CLOUDY -> if (night) R.drawable.wx_cloud_moon else R.drawable.wx_cloud_sun
        Condition.MOSTLY_CLOUDY -> R.drawable.wx_cloudy
        Condition.OVERCAST -> R.drawable.wx_cloud
        Condition.FOG -> R.drawable.wx_cloud_fog
        Condition.LIGHT_RAIN -> R.drawable.wx_cloud_drizzle
        Condition.SHOWERS -> if (night) R.drawable.wx_cloud_moon_rain else R.drawable.wx_cloud_sun_rain
        Condition.RAIN -> R.drawable.wx_cloud_rain
        Condition.HEAVY_RAIN -> R.drawable.wx_cloud_rain_wind
        Condition.SLEET -> R.drawable.wx_cloud_hail
        Condition.SNOW_SHOWERS -> R.drawable.wx_cloud_snow
        Condition.SNOW -> R.drawable.wx_snowflake
        Condition.THUNDERSTORM -> R.drawable.wx_cloud_lightning
    }

    /** Shows the icon for [condition] in [view], tinted with [color]. */
    fun show(view: ImageView, condition: Condition, night: Boolean, color: Int) {
        view.setImageResource(icon(condition, night))
        view.setColorFilter(color)
    }
}
