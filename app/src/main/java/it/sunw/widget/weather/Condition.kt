package it.sunw.widget.weather

import it.sunw.widget.R

/**
 * Weather category used for icons and descriptions, mapped from MeteoBlue pictocodes.
 * Declared roughly by increasing significance ([severity]), used to pick night icons.
 */
enum class Condition(val label: Int) {
    CLEAR(R.string.wx_clear),
    MOSTLY_CLEAR(R.string.wx_mostly_clear),
    PARTLY_CLOUDY(R.string.wx_partly_cloudy),
    MOSTLY_CLOUDY(R.string.wx_mostly_cloudy),
    OVERCAST(R.string.wx_overcast),
    FOG(R.string.wx_fog),
    LIGHT_RAIN(R.string.wx_light_rain),
    SHOWERS(R.string.wx_showers),
    RAIN(R.string.wx_rain),
    HEAVY_RAIN(R.string.wx_heavy_rain),
    SLEET(R.string.wx_sleet),
    SNOW_SHOWERS(R.string.wx_snow_showers),
    SNOW(R.string.wx_snow),
    THUNDERSTORM(R.string.wx_thunderstorm);

    val severity: Int get() = ordinal

    val hasSunOrMoon: Boolean get() = this in setOf(CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, MOSTLY_CLOUDY, SHOWERS, SNOW_SHOWERS, THUNDERSTORM)

    companion object {
        /** Hourly pictocodes 1–35 (meteoblue "pictocode" for data_1h). */
        fun fromHourlyPictocode(code: Int): Condition = when (code) {
            1 -> CLEAR
            2, 3, 4, 5, 6, 13, 14, 15 -> MOSTLY_CLEAR // few cirrus / few low clouds / hazy
            7, 8, 9 -> PARTLY_CLOUDY
            10, 11, 12 -> THUNDERSTORM // mixed, thunderstorm clouds possible
            16, 17, 18 -> FOG
            19, 20, 21 -> MOSTLY_CLOUDY
            22 -> OVERCAST
            23 -> RAIN
            24, 26, 29, 34 -> SNOW
            25 -> HEAVY_RAIN
            27, 28, 30 -> THUNDERSTORM
            31 -> SHOWERS
            32 -> SNOW_SHOWERS
            33 -> LIGHT_RAIN
            35 -> SLEET
            else -> PARTLY_CLOUDY
        }

        /** Daily pictocodes 1–17 (meteoblue "pictocode" for data_day). */
        fun fromDailyPictocode(code: Int): Condition = when (code) {
            1 -> CLEAR
            2 -> MOSTLY_CLEAR
            3 -> PARTLY_CLOUDY
            4 -> OVERCAST
            5 -> FOG
            6, 14 -> RAIN
            7 -> SHOWERS
            8 -> THUNDERSTORM
            9, 13, 15, 17 -> SNOW
            10 -> SNOW_SHOWERS
            11 -> SLEET
            12, 16 -> LIGHT_RAIN
            else -> PARTLY_CLOUDY
        }
    }
}
