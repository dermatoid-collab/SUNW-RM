package it.sunw.widget

import android.content.Context
import android.graphics.Color
import android.os.Build

/** How the elevation curve is coloured. */
enum class CurveStyle(val key: String, val label: Int) {
    /** Accent above the horizon, dimmed below. */
    ACCENT("accent", R.string.curve_accent),

    /** Colour follows the Sun's elevation: night, twilights, golden hour, high Sun. */
    SKY("sky", R.string.curve_sky),

    /** Highlights only blue hour (−6°…−4°) and golden hour (−4°…+6°). */
    GOLDEN("golden", R.string.curve_golden),

    /** Accent curve plus shaded civil / nautical / astronomical twilight bands. */
    TWILIGHT("twilight", R.string.curve_twilight),
}

enum class Accent(val key: String, val label: Int, private val color: Int?) {
    AMBER("amber", R.string.accent_amber, 0xFFFFB547.toInt()),
    MATERIAL_YOU("material_you", R.string.material_you, null),
    ICE("ice", R.string.accent_ice, 0xFF8FD3FF.toInt()),
    ALPINE("alpine", R.string.accent_alpine, 0xFF8FD48A.toInt()),
    CORAL("coral", R.string.accent_coral, 0xFFFF8A7A.toInt()),
    WHITE("white", R.string.accent_white, 0xFFEDEDED.toInt()),
    TOKYO_YELLOW("tokyo_yellow", R.string.accent_tokyo_yellow, 0xFFE0AF68.toInt()),
    TOKYO_BLUE("tokyo_blue", R.string.accent_tokyo_blue, 0xFF7AA2F7.toInt()),
    CATPPUCCIN_MAUVE("catppuccin_mauve", R.string.accent_catppuccin_mauve, 0xFFCBA6F7.toInt()),
    NORD_FROST("nord_frost", R.string.accent_nord_frost, 0xFF88C0D0.toInt()),
    DRACULA_PINK("dracula_pink", R.string.accent_dracula_pink, 0xFFFF79C6.toInt()),
    ROSE_PINE("rose_pine", R.string.accent_rose_pine, 0xFFEBBCBA.toInt()),

    /** Pantone colour of the year 2024. */
    PEACH_FUZZ("peach_fuzz", R.string.accent_peach_fuzz, 0xFFFFBE98.toInt());

    fun resolve(context: Context): Int = color
        ?: if (Build.VERSION.SDK_INT >= 31) context.getColor(android.R.color.system_accent1_200) else AMBER.color!!
}

/** Widget background plus the matching text colours and, for the app pages, page/card colours. */
enum class Theme(val key: String, val label: Int) {
    ANTHRACITE("anthracite", R.string.theme_anthracite),
    BLACK("black", R.string.theme_black),
    GLASS("glass", R.string.theme_glass),
    MATERIAL_YOU("material_you", R.string.material_you),
    TOKYO_NIGHT("tokyo_night", R.string.theme_tokyo_night),
    CATPPUCCIN("catppuccin", R.string.theme_catppuccin),
    NORD("nord", R.string.theme_nord),
    DRACULA("dracula", R.string.theme_dracula),
    ROSE_PINE("rose_pine", R.string.theme_rose_pine);

    /** Rounded (system radius) and square-ish (1×1 "big") background drawables. */
    val background: Int
        get() = when (this) {
            ANTHRACITE -> R.drawable.widget_bg_anthracite
            BLACK -> R.drawable.widget_bg_black
            GLASS -> R.drawable.widget_bg_glass
            MATERIAL_YOU -> R.drawable.widget_background
            TOKYO_NIGHT -> R.drawable.widget_bg_tokyo
            CATPPUCCIN -> R.drawable.widget_bg_catppuccin
            NORD -> R.drawable.widget_bg_nord
            DRACULA -> R.drawable.widget_bg_dracula
            ROSE_PINE -> R.drawable.widget_bg_rose_pine
        }
    val backgroundSquare: Int
        get() = when (this) {
            ANTHRACITE -> R.drawable.widget_bg_square_anthracite
            BLACK -> R.drawable.widget_bg_square_black
            GLASS -> R.drawable.widget_bg_square_glass
            MATERIAL_YOU -> R.drawable.widget_background_tiny
            TOKYO_NIGHT -> R.drawable.widget_bg_square_tokyo
            CATPPUCCIN -> R.drawable.widget_bg_square_catppuccin
            NORD -> R.drawable.widget_bg_square_nord
            DRACULA -> R.drawable.widget_bg_square_dracula
            ROSE_PINE -> R.drawable.widget_bg_square_rose_pine
        }
}

/** Every colour the widget and the app pages need, resolved for the current device. */
data class Palette(
    val curveStyle: CurveStyle,
    val theme: Theme,
    val accent: Int,
    /** Swatch colour of the widget background, for previews. */
    val background: Int,
    val text: Int,
    val textSecondary: Int,
    /** Below-horizon curve and horizon line. */
    val dim: Int,
    val pageBackground: Int,
    val cardBackground: Int,
    /** (elevation°, colour) stops for [CurveStyle.SKY]. */
    val sky: List<Pair<Double, Int>>,
    val blueHour: Int,
    val goldenHour: Int,
    val highSun: Int,
    val twilightBand: Int,
) {
    /** Colour of the curve (and the Sun dot) at a given solar elevation. */
    fun curveColor(elevation: Double): Int = when (curveStyle) {
        CurveStyle.ACCENT, CurveStyle.TWILIGHT -> if (elevation > SunCalculator.HORIZON_DEG) accent else dim
        CurveStyle.SKY -> ramp(elevation)
        CurveStyle.GOLDEN -> when {
            elevation >= 6 -> highSun
            elevation >= -4 -> goldenHour
            elevation >= -6 -> blueHour
            else -> dim
        }
    }

    /** Whether the segment at this elevation is drawn at reduced opacity. */
    fun isDimmed(elevation: Double): Boolean = when (curveStyle) {
        CurveStyle.SKY -> false
        CurveStyle.GOLDEN -> elevation < -6
        else -> elevation <= SunCalculator.HORIZON_DEG
    }

    private fun ramp(elevation: Double): Int {
        if (elevation <= sky.first().first) return sky.first().second
        for ((a, b) in sky.zipWithNext()) {
            if (elevation <= b.first) return blend(a.second, b.second, ((elevation - a.first) / (b.first - a.first)).toFloat())
        }
        return sky.last().second
    }

    companion object {
        fun blend(from: Int, to: Int, t: Float): Int = Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
        )
    }
}

/** User's appearance choices; defaults: "Cielo" curve, amber accent, anthracite background. */
class AppearanceStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    var curveStyle: CurveStyle
        get() = CurveStyle.values().firstOrNull { it.key == prefs.getString(KEY_CURVE, null) } ?: CurveStyle.SKY
        set(value) = prefs.edit().putString(KEY_CURVE, value.key).apply()

    var accent: Accent
        get() = Accent.values().firstOrNull { it.key == prefs.getString(KEY_ACCENT, null) } ?: Accent.AMBER
        set(value) = prefs.edit().putString(KEY_ACCENT, value.key).apply()

    var theme: Theme
        get() = Theme.values().firstOrNull { it.key == prefs.getString(KEY_THEME, null) } ?: Theme.ANTHRACITE
        set(value) = prefs.edit().putString(KEY_THEME, value.key).apply()

    fun palette(theme: Theme = this.theme): Palette {
        val accent = accent.resolve(context)
        val materialYou = Build.VERSION.SDK_INT >= 31
        fun sys(id: Int, fallback: Int) = if (materialYou) context.getColor(id) else fallback
        return when (theme) {
            Theme.TOKYO_NIGHT -> Palette(
                curveStyle, theme, accent,
                background = 0xFF1A1B26.toInt(), text = 0xFFC0CAF5.toInt(), textSecondary = 0xFFA9B1D6.toInt(),
                dim = 0xFF565F89.toInt(), pageBackground = 0xFF16161E.toInt(), cardBackground = 0xFF1F2335.toInt(),
                sky = TOKYO_SKY, blueHour = 0xFF7AA2F7.toInt(), goldenHour = 0xFFFF9E64.toInt(),
                highSun = 0xFFE0AF68.toInt(), twilightBand = 0xFF7AA2F7.toInt(),
            )
            // Catppuccin Mocha: crust page, mantle widget, base cards.
            Theme.CATPPUCCIN -> Palette(
                curveStyle, theme, accent,
                background = 0xFF181825.toInt(), text = 0xFFCDD6F4.toInt(), textSecondary = 0xFFA6ADC8.toInt(),
                dim = 0xFF6C7086.toInt(), pageBackground = 0xFF11111B.toInt(), cardBackground = 0xFF1E1E2E.toInt(),
                sky = sky(0xFF313244, 0xFF45475A, 0xFF89B4FA, 0xFFCBA6F7, 0xFFF38BA8, 0xFFFAB387, 0xFFF9E2AF, 0xFFF5E0DC),
                blueHour = 0xFF89B4FA.toInt(), goldenHour = 0xFFFAB387.toInt(),
                highSun = 0xFFF9E2AF.toInt(), twilightBand = 0xFFB4BEFE.toInt(),
            )
            // Nord: Polar Night backgrounds, Snow Storm text, Frost and Aurora for the sky.
            Theme.NORD -> Palette(
                curveStyle, theme, accent,
                background = 0xFF2E3440.toInt(), text = 0xFFECEFF4.toInt(), textSecondary = 0xFFB4BCCB.toInt(),
                dim = 0xFF4C566A.toInt(), pageBackground = 0xFF242933.toInt(), cardBackground = 0xFF3B4252.toInt(),
                sky = sky(0xFF3B4252, 0xFF434C5E, 0xFF5E81AC, 0xFFB48EAD, 0xFFBF616A, 0xFFD08770, 0xFFEBCB8B, 0xFFECEFF4),
                blueHour = 0xFF81A1C1.toInt(), goldenHour = 0xFFD08770.toInt(),
                highSun = 0xFFEBCB8B.toInt(), twilightBand = 0xFF88C0D0.toInt(),
            )
            Theme.DRACULA -> Palette(
                curveStyle, theme, accent,
                background = 0xFF282A36.toInt(), text = 0xFFF8F8F2.toInt(), textSecondary = 0xFFBFC1D3.toInt(),
                dim = 0xFF6272A4.toInt(), pageBackground = 0xFF21222C.toInt(), cardBackground = 0xFF2F3241.toInt(),
                sky = sky(0xFF343746, 0xFF44475A, 0xFF6272A4, 0xFFBD93F9, 0xFFFF79C6, 0xFFFFB86C, 0xFFF1FA8C, 0xFFF8F8F2),
                blueHour = 0xFF8BE9FD.toInt(), goldenHour = 0xFFFFB86C.toInt(),
                highSun = 0xFFF1FA8C.toInt(), twilightBand = 0xFFBD93F9.toInt(),
            )
            // Rosé Pine (main variant).
            Theme.ROSE_PINE -> Palette(
                curveStyle, theme, accent,
                background = 0xFF191724.toInt(), text = 0xFFE0DEF4.toInt(), textSecondary = 0xFF908CAA.toInt(),
                dim = 0xFF6E6A86.toInt(), pageBackground = 0xFF12101B.toInt(), cardBackground = 0xFF1F1D2E.toInt(),
                sky = sky(0xFF26233A, 0xFF31748F, 0xFF9CCFD8, 0xFFC4A7E7, 0xFFEB6F92, 0xFFEBBCBA, 0xFFF6C177, 0xFFE0DEF4),
                blueHour = 0xFF9CCFD8.toInt(), goldenHour = 0xFFEB6F92.toInt(),
                highSun = 0xFFF6C177.toInt(), twilightBand = 0xFFC4A7E7.toInt(),
            )
            else -> {
                val (bg, text, secondary) = when (theme) {
                    Theme.MATERIAL_YOU -> Triple(
                        sys(android.R.color.system_neutral1_900, ANTHRACITE_BG),
                        sys(android.R.color.system_neutral1_50, DEFAULT_TEXT),
                        sys(android.R.color.system_neutral2_300, DEFAULT_SECONDARY),
                    )
                    Theme.BLACK -> Triple(Color.BLACK, DEFAULT_TEXT, DEFAULT_SECONDARY)
                    Theme.GLASS -> Triple(0x80141518.toInt(), DEFAULT_TEXT, DEFAULT_SECONDARY)
                    else -> Triple(ANTHRACITE_BG, DEFAULT_TEXT, DEFAULT_SECONDARY)
                }
                Palette(
                    curveStyle, theme, accent, background = bg, text = text, textSecondary = secondary,
                    dim = 0xFF7D7B77.toInt(),
                    pageBackground = if (theme == Theme.BLACK) Color.BLACK else 0xFF121316.toInt(),
                    cardBackground = 0xFF1B1C20.toInt(),
                    sky = SKY, blueHour = 0xFF5B8CFF.toInt(), goldenHour = 0xFFFF8A2A.toInt(),
                    highSun = 0xFFFFE3A0.toInt(), twilightBand = 0xFF7F8CFF.toInt(),
                )
            }
        }
    }

    companion object {
        private const val KEY_CURVE = "curve"
        private const val KEY_ACCENT = "accent"
        private const val KEY_THEME = "theme"
        private const val ANTHRACITE_BG = 0xE6141518.toInt()
        private const val DEFAULT_TEXT = 0xFFF2F1EE.toInt()
        private const val DEFAULT_SECONDARY = 0xFFA8A6A1.toInt()

        /** Night → astronomical / nautical / civil twilight → horizon → golden hour → high Sun. */
        private val SKY = listOf(
            -18.0 to 0xFF2C3352.toInt(), -12.0 to 0xFF40458A.toInt(), -6.0 to 0xFF7A55B0.toInt(),
            -2.0 to 0xFFD0578A.toInt(), 1.0 to 0xFFFF6A3D.toInt(), 6.0 to 0xFFFF9A3C.toInt(),
            15.0 to 0xFFFFC85A.toInt(), 30.0 to 0xFFFFE9A8.toInt(),
        )

        /** Sky ramp at the usual elevations (−18° … 30°) from eight theme colours. */
        private fun sky(vararg colors: Long): List<Pair<Double, Int>> =
            listOf(-18.0, -12.0, -6.0, -2.0, 1.0, 6.0, 15.0, 30.0).zip(colors.map { it.toInt() })

        /** The same progression drawn from the Tokyo Night palette. */
        private val TOKYO_SKY = listOf(
            -18.0 to 0xFF3B4261.toInt(), -12.0 to 0xFF414868.toInt(), -6.0 to 0xFF7AA2F7.toInt(),
            -2.0 to 0xFFBB9AF7.toInt(), 1.0 to 0xFFF7768E.toInt(), 6.0 to 0xFFFF9E64.toInt(),
            15.0 to 0xFFE0AF68.toInt(), 30.0 to 0xFFC0CAF5.toInt(),
        )
    }
}
