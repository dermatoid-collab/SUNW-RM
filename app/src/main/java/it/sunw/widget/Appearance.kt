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
/** The sections of the theme picker; [LEGACY] has no title: it stands first and alone. */
enum class ThemeGroup(val title: Int?) {
    LEGACY(null),
    SYSTEM(R.string.theme_group_system),
    COMMUNITY_DARK(R.string.theme_group_community_dark),
    COMMUNITY_LIGHT(R.string.theme_group_community_light),
    ORIGINAL_DARK(R.string.theme_group_original_dark),
    ORIGINAL_LIGHT(R.string.theme_group_original_light),
    MINIMALIST(R.string.theme_group_minimalist),
}

enum class Theme(val key: String, val label: Int, val isLight: Boolean = false) {
    /** The original look (kept under its old key so saved settings still match). */
    ANTHRACITE("anthracite", R.string.theme_anthracite),
    BLACK("black", R.string.theme_black),
    GLASS("glass", R.string.theme_glass),
    MATERIAL_YOU("material_you", R.string.material_you),
    TOKYO_NIGHT("tokyo_night", R.string.theme_tokyo_night),
    CATPPUCCIN("catppuccin", R.string.theme_catppuccin),
    NORD("nord", R.string.theme_nord),
    DRACULA("dracula", R.string.theme_dracula),
    ROSE_PINE("rose_pine", R.string.theme_rose_pine),
    GRUVBOX_DARK("gruvbox_dark", R.string.theme_gruvbox_dark),
    ONE_DARK("one_dark", R.string.theme_one_dark),
    SOLARIZED_DARK("solarized_dark", R.string.theme_solarized_dark),
    EVERFOREST("everforest", R.string.theme_everforest),
    NIGHT_OWL("night_owl", R.string.theme_night_owl),
    PALENIGHT("palenight", R.string.theme_palenight),
    AYU_MIRAGE("ayu_mirage", R.string.theme_ayu_mirage),
    VINO("vino", R.string.theme_vino),
    METEOBLUE("meteoblue", R.string.theme_meteoblue),
    TURCHESE("turchese", R.string.theme_turchese),
    VIOLA("viola", R.string.theme_viola),
    CORALLO("corallo", R.string.theme_corallo),
    LIME("lime", R.string.theme_lime),
    SOLARIZED_LIGHT("solarized_light", R.string.theme_solarized_light, isLight = true),
    CATPPUCCIN_LATTE("catppuccin_latte", R.string.theme_catppuccin_latte, isLight = true),
    GRUVBOX_LIGHT("gruvbox_light", R.string.theme_gruvbox_light, isLight = true),
    ARDESIA("ardesia", R.string.theme_ardesia, isLight = true),
    SALVIA("salvia", R.string.theme_salvia, isLight = true),
    DUNA("duna", R.string.theme_duna, isLight = true),
    ILMETEO("ilmeteo", R.string.theme_ilmeteo, isLight = true),
    AZZURRO("azzurro", R.string.theme_azzurro, isLight = true),
    GIALLO("giallo", R.string.theme_giallo, isLight = true),
    MIN_NEUTRAL_DARK("neutral_dark", R.string.theme_min_neutral_dark),
    MIN_NEUTRAL_LIGHT("neutral_light", R.string.theme_min_neutral_light, isLight = true),
    MIN_SEPIA_DARK("sepia_dark", R.string.theme_min_sepia_dark),
    MIN_SEPIA_LIGHT("sepia_light", R.string.theme_min_sepia_light, isLight = true),
    MIN_SAGE_DARK("sage_dark", R.string.theme_min_sage_dark),
    MIN_SAGE_LIGHT("sage_light", R.string.theme_min_sage_light, isLight = true),
    MIN_LAVENDER_DARK("lavender_dark", R.string.theme_min_lavender_dark),
    MIN_LAVENDER_LIGHT("lavender_light", R.string.theme_min_lavender_light, isLight = true);

    /** The section of the picker in Settings the theme is listed under. */
    val group: ThemeGroup
        get() = when (this) {
            ANTHRACITE -> ThemeGroup.LEGACY
            GLASS, MATERIAL_YOU -> ThemeGroup.SYSTEM
            BLACK, TOKYO_NIGHT, CATPPUCCIN, NORD, DRACULA, GRUVBOX_DARK, ONE_DARK, SOLARIZED_DARK -> ThemeGroup.COMMUNITY_DARK
            SOLARIZED_LIGHT, CATPPUCCIN_LATTE, GRUVBOX_LIGHT -> ThemeGroup.COMMUNITY_LIGHT
            EVERFOREST, NIGHT_OWL, PALENIGHT, AYU_MIRAGE, VINO, METEOBLUE, TURCHESE, VIOLA, CORALLO, LIME -> ThemeGroup.ORIGINAL_DARK
            ARDESIA, SALVIA, DUNA, ILMETEO, AZZURRO, GIALLO -> ThemeGroup.ORIGINAL_LIGHT
            else -> ThemeGroup.MINIMALIST
        }

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
            GRUVBOX_DARK -> R.drawable.widget_bg_gruvbox_dark
            ONE_DARK -> R.drawable.widget_bg_one_dark
            SOLARIZED_DARK -> R.drawable.widget_bg_solarized_dark
            SOLARIZED_LIGHT -> R.drawable.widget_bg_solarized_light
            CATPPUCCIN_LATTE -> R.drawable.widget_bg_catppuccin_latte
            GRUVBOX_LIGHT -> R.drawable.widget_bg_gruvbox_light
            EVERFOREST -> R.drawable.widget_bg_everforest
            NIGHT_OWL -> R.drawable.widget_bg_night_owl
            PALENIGHT -> R.drawable.widget_bg_palenight
            AYU_MIRAGE -> R.drawable.widget_bg_ayu_mirage
            VINO -> R.drawable.widget_bg_vino
            ARDESIA -> R.drawable.widget_bg_ardesia
            SALVIA -> R.drawable.widget_bg_salvia
            DUNA -> R.drawable.widget_bg_duna
            METEOBLUE -> R.drawable.widget_bg_meteoblue
            TURCHESE -> R.drawable.widget_bg_turchese
            VIOLA -> R.drawable.widget_bg_viola
            CORALLO -> R.drawable.widget_bg_corallo
            LIME -> R.drawable.widget_bg_lime
            ILMETEO -> R.drawable.widget_bg_ilmeteo
            AZZURRO -> R.drawable.widget_bg_azzurro
            GIALLO -> R.drawable.widget_bg_giallo
            MIN_NEUTRAL_DARK -> R.drawable.widget_bg_min_neutral_dark
            MIN_NEUTRAL_LIGHT -> R.drawable.widget_bg_min_neutral_light
            MIN_SEPIA_DARK -> R.drawable.widget_bg_min_sepia_dark
            MIN_SEPIA_LIGHT -> R.drawable.widget_bg_min_sepia_light
            MIN_SAGE_DARK -> R.drawable.widget_bg_min_sage_dark
            MIN_SAGE_LIGHT -> R.drawable.widget_bg_min_sage_light
            MIN_LAVENDER_DARK -> R.drawable.widget_bg_min_lavender_dark
            MIN_LAVENDER_LIGHT -> R.drawable.widget_bg_min_lavender_light
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
            GRUVBOX_DARK -> R.drawable.widget_bg_square_gruvbox_dark
            ONE_DARK -> R.drawable.widget_bg_square_one_dark
            SOLARIZED_DARK -> R.drawable.widget_bg_square_solarized_dark
            SOLARIZED_LIGHT -> R.drawable.widget_bg_square_solarized_light
            CATPPUCCIN_LATTE -> R.drawable.widget_bg_square_catppuccin_latte
            GRUVBOX_LIGHT -> R.drawable.widget_bg_square_gruvbox_light
            EVERFOREST -> R.drawable.widget_bg_square_everforest
            NIGHT_OWL -> R.drawable.widget_bg_square_night_owl
            PALENIGHT -> R.drawable.widget_bg_square_palenight
            AYU_MIRAGE -> R.drawable.widget_bg_square_ayu_mirage
            VINO -> R.drawable.widget_bg_square_vino
            ARDESIA -> R.drawable.widget_bg_square_ardesia
            SALVIA -> R.drawable.widget_bg_square_salvia
            DUNA -> R.drawable.widget_bg_square_duna
            METEOBLUE -> R.drawable.widget_bg_square_meteoblue
            TURCHESE -> R.drawable.widget_bg_square_turchese
            VIOLA -> R.drawable.widget_bg_square_viola
            CORALLO -> R.drawable.widget_bg_square_corallo
            LIME -> R.drawable.widget_bg_square_lime
            ILMETEO -> R.drawable.widget_bg_square_ilmeteo
            AZZURRO -> R.drawable.widget_bg_square_azzurro
            GIALLO -> R.drawable.widget_bg_square_giallo
            MIN_NEUTRAL_DARK -> R.drawable.widget_bg_square_min_neutral_dark
            MIN_NEUTRAL_LIGHT -> R.drawable.widget_bg_square_min_neutral_light
            MIN_SEPIA_DARK -> R.drawable.widget_bg_square_min_sepia_dark
            MIN_SEPIA_LIGHT -> R.drawable.widget_bg_square_min_sepia_light
            MIN_SAGE_DARK -> R.drawable.widget_bg_square_min_sage_dark
            MIN_SAGE_LIGHT -> R.drawable.widget_bg_square_min_sage_light
            MIN_LAVENDER_DARK -> R.drawable.widget_bg_square_min_lavender_dark
            MIN_LAVENDER_LIGHT -> R.drawable.widget_bg_square_min_lavender_light
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
    val isLight: Boolean get() = theme.isLight

    /**
     * [cardBackground] pushed [amount] towards the text colour: a lighter tile on a dark theme,
     * a darker one on a light theme, so raised areas and rules stay visible on both.
     */
    fun raised(amount: Float): Int = blend(cardBackground, text, amount)

    /** Colour of the unlit part of the Moon: a clear grey disc on light themes. */
    fun moonDark(): Int = raised(if (isLight) 0.45f else 0.08f)

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
        val accent = readable(accent.resolve(context), theme.isLight)
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
            // Gruvbox, One Dark and Solarized (dark), with their light siblings, as in HRV-RM.
            Theme.GRUVBOX_DARK -> Palette(
                curveStyle, theme, accent,
                background = 0xFF282828.toInt(), text = 0xFFEBDBB2.toInt(), textSecondary = 0xFFD5C4A1.toInt(),
                dim = 0xFF928374.toInt(), pageBackground = 0xFF1D2021.toInt(), cardBackground = 0xFF32302F.toInt(),
                sky = sky(0xFF3C3836, 0xFF504945, 0xFF458588, 0xFFB16286, 0xFFFB4934, 0xFFFE8019, 0xFFFABD2F, 0xFFFBF1C7),
                blueHour = 0xFF83A598.toInt(), goldenHour = 0xFFFE8019.toInt(),
                highSun = 0xFFFABD2F.toInt(), twilightBand = 0xFF83A598.toInt(),
            )
            Theme.ONE_DARK -> Palette(
                curveStyle, theme, accent,
                background = 0xFF282C34.toInt(), text = 0xFFABB2BF.toInt(), textSecondary = 0xFF9DA5B4.toInt(),
                dim = 0xFF5C6370.toInt(), pageBackground = 0xFF21252B.toInt(), cardBackground = 0xFF2C313A.toInt(),
                sky = sky(0xFF353B45, 0xFF3E4451, 0xFF61AFEF, 0xFFC678DD, 0xFFE06C75, 0xFFD19A66, 0xFFE5C07B, 0xFFABB2BF),
                blueHour = 0xFF61AFEF.toInt(), goldenHour = 0xFFD19A66.toInt(),
                highSun = 0xFFE5C07B.toInt(), twilightBand = 0xFFC678DD.toInt(),
            )
            Theme.SOLARIZED_DARK -> Palette(
                curveStyle, theme, accent,
                background = 0xFF002B36.toInt(), text = 0xFF93A1A1.toInt(), textSecondary = 0xFF839496.toInt(),
                dim = 0xFF586E75.toInt(), pageBackground = 0xFF00212B.toInt(), cardBackground = 0xFF073642.toInt(),
                sky = sky(0xFF073642, 0xFF0E4350, 0xFF268BD2, 0xFF6C71C4, 0xFFD33682, 0xFFCB4B16, 0xFFB58900, 0xFFEEE8D5),
                blueHour = 0xFF268BD2.toInt(), goldenHour = 0xFFCB4B16.toInt(),
                highSun = 0xFFB58900.toInt(), twilightBand = 0xFF6C71C4.toInt(),
            )
            // Light themes: page in the base colour, cards a step darker, deeper sky colours.
            Theme.SOLARIZED_LIGHT -> Palette(
                curveStyle, theme, accent,
                background = 0xFFFDF6E3.toInt(), text = 0xFF073642.toInt(), textSecondary = 0xFF586E75.toInt(),
                dim = 0xFF93A1A1.toInt(), pageBackground = 0xFFFDF6E3.toInt(), cardBackground = 0xFFEEE8D5.toInt(),
                sky = sky(0xFF586E75, 0xFF6C71C4, 0xFF268BD2, 0xFFD33682, 0xFFDC322F, 0xFFCB4B16, 0xFFB58900, 0xFFB58900),
                blueHour = 0xFF268BD2.toInt(), goldenHour = 0xFFCB4B16.toInt(),
                highSun = 0xFFB58900.toInt(), twilightBand = 0xFF6C71C4.toInt(),
            )
            Theme.CATPPUCCIN_LATTE -> Palette(
                curveStyle, theme, accent,
                background = 0xFFEFF1F5.toInt(), text = 0xFF4C4F69.toInt(), textSecondary = 0xFF6C6F85.toInt(),
                dim = 0xFF9CA0B0.toInt(), pageBackground = 0xFFEFF1F5.toInt(), cardBackground = 0xFFDCE0E8.toInt(),
                sky = sky(0xFF9CA0B0, 0xFF7287FD, 0xFF1E66F5, 0xFF8839EF, 0xFFD20F39, 0xFFFE640B, 0xFFDF8E1D, 0xFFDF8E1D),
                blueHour = 0xFF1E66F5.toInt(), goldenHour = 0xFFFE640B.toInt(),
                highSun = 0xFFDF8E1D.toInt(), twilightBand = 0xFF8839EF.toInt(),
            )
            Theme.GRUVBOX_LIGHT -> Palette(
                curveStyle, theme, accent,
                background = 0xFFFBF1C7.toInt(), text = 0xFF3C3836.toInt(), textSecondary = 0xFF665C54.toInt(),
                dim = 0xFF928374.toInt(), pageBackground = 0xFFFBF1C7.toInt(), cardBackground = 0xFFEBDBB2.toInt(),
                sky = sky(0xFFA89984, 0xFF076678, 0xFF458588, 0xFFB16286, 0xFF9D0006, 0xFFAF3A03, 0xFFB57614, 0xFFB57614),
                blueHour = 0xFF076678.toInt(), goldenHour = 0xFFAF3A03.toInt(),
                highSun = 0xFFB57614.toInt(), twilightBand = 0xFF8F3F71.toInt(),
            )
            // Mid-tone themes: coloured darks (green, deep blue, violet, slate, wine) and tinted lights (slate, sage, sand).
            Theme.EVERFOREST -> Palette(
                curveStyle, theme, accent,
                background = 0xFF2D353B.toInt(), text = 0xFFD3C6AA.toInt(), textSecondary = 0xFFA7A08B.toInt(),
                dim = 0xFF859289.toInt(), pageBackground = 0xFF272E33.toInt(), cardBackground = 0xFF343F44.toInt(),
                sky = sky(0xFF3D484D, 0xFF4F585E, 0xFF7FBBB3, 0xFFD699B6, 0xFFE67E80, 0xFFE69875, 0xFFDBBC7F, 0xFFD3C6AA),
                blueHour = 0xFF7FBBB3.toInt(), goldenHour = 0xFFE69875.toInt(),
                highSun = 0xFFDBBC7F.toInt(), twilightBand = 0xFFD699B6.toInt(),
            )
            Theme.NIGHT_OWL -> Palette(
                curveStyle, theme, accent,
                background = 0xFF011627.toInt(), text = 0xFFD6DEEB.toInt(), textSecondary = 0xFF8AA3BF.toInt(),
                dim = 0xFF5F7E97.toInt(), pageBackground = 0xFF01111D.toInt(), cardBackground = 0xFF0B2942.toInt(),
                sky = sky(0xFF0B2942, 0xFF1D3B53, 0xFF82AAFF, 0xFFC792EA, 0xFFFF5874, 0xFFF78C6C, 0xFFECC48D, 0xFFD6DEEB),
                blueHour = 0xFF82AAFF.toInt(), goldenHour = 0xFFF78C6C.toInt(),
                highSun = 0xFFECC48D.toInt(), twilightBand = 0xFFC792EA.toInt(),
            )
            Theme.PALENIGHT -> Palette(
                curveStyle, theme, accent,
                background = 0xFF292D3E.toInt(), text = 0xFFBFC7E8.toInt(), textSecondary = 0xFF9AA3CC.toInt(),
                dim = 0xFF676E95.toInt(), pageBackground = 0xFF232635.toInt(), cardBackground = 0xFF32364A.toInt(),
                sky = sky(0xFF32364A, 0xFF444267, 0xFF82AAFF, 0xFFC792EA, 0xFFF07178, 0xFFF78C6C, 0xFFFFCB6B, 0xFFEEFFFF),
                blueHour = 0xFF82AAFF.toInt(), goldenHour = 0xFFF78C6C.toInt(),
                highSun = 0xFFFFCB6B.toInt(), twilightBand = 0xFFC792EA.toInt(),
            )
            Theme.AYU_MIRAGE -> Palette(
                curveStyle, theme, accent,
                background = 0xFF1F2430.toInt(), text = 0xFFCCCAC2.toInt(), textSecondary = 0xFF9C9A93.toInt(),
                dim = 0xFF707A8C.toInt(), pageBackground = 0xFF171B24.toInt(), cardBackground = 0xFF2A3142.toInt(),
                sky = sky(0xFF2A3142, 0xFF34455A, 0xFF73D0FF, 0xFFDFBFFF, 0xFFF28779, 0xFFFFAD66, 0xFFFFD173, 0xFFCCCAC2),
                blueHour = 0xFF73D0FF.toInt(), goldenHour = 0xFFFFAD66.toInt(),
                highSun = 0xFFFFD173.toInt(), twilightBand = 0xFFDFBFFF.toInt(),
            )
            Theme.VINO -> Palette(
                curveStyle, theme, accent,
                background = 0xFF2A151B.toInt(), text = 0xFFF1DADA.toInt(), textSecondary = 0xFFC9A5A8.toInt(),
                dim = 0xFF8A6469.toInt(), pageBackground = 0xFF1F0F13.toInt(), cardBackground = 0xFF3A1D25.toInt(),
                sky = sky(0xFF3A1D25, 0xFF55283A, 0xFF7A5CA0, 0xFFC25B80, 0xFFE8665F, 0xFFF29A5E, 0xFFF6CB7A, 0xFFFBEFD9),
                blueHour = 0xFF7A9CE0.toInt(), goldenHour = 0xFFF29A5E.toInt(),
                highSun = 0xFFF6CB7A.toInt(), twilightBand = 0xFFC25B80.toInt(),
            )
            Theme.ARDESIA -> Palette(
                curveStyle, theme, accent,
                background = 0xFFE6EBF1.toInt(), text = 0xFF1F2937.toInt(), textSecondary = 0xFF475569.toInt(),
                dim = 0xFF8793A3.toInt(), pageBackground = 0xFFDDE3EA.toInt(), cardBackground = 0xFFCBD3DD.toInt(),
                sky = sky(0xFF8793A3, 0xFF5B6BC0, 0xFF2563EB, 0xFF7C3AED, 0xFFDC2626, 0xFFEA580C, 0xFFCA8A04, 0xFFCA8A04),
                blueHour = 0xFF2563EB.toInt(), goldenHour = 0xFFEA580C.toInt(),
                highSun = 0xFFCA8A04.toInt(), twilightBand = 0xFF7C3AED.toInt(),
            )
            Theme.SALVIA -> Palette(
                curveStyle, theme, accent,
                background = 0xFFE3EADD.toInt(), text = 0xFF22301F.toInt(), textSecondary = 0xFF425A3C.toInt(),
                dim = 0xFF8A9A84.toInt(), pageBackground = 0xFFDCE4D6.toInt(), cardBackground = 0xFFC9D6C1.toInt(),
                sky = sky(0xFF8A9A84, 0xFF5B6BA8, 0xFF2F6F8F, 0xFF8A4F9E, 0xFFB23A48, 0xFFC2571A, 0xFFA8740A, 0xFFA8740A),
                blueHour = 0xFF2F6F8F.toInt(), goldenHour = 0xFFC2571A.toInt(),
                highSun = 0xFFA8740A.toInt(), twilightBand = 0xFF8A4F9E.toInt(),
            )
            Theme.DUNA -> Palette(
                curveStyle, theme, accent,
                background = 0xFFEFE6D3.toInt(), text = 0xFF3A2F1E.toInt(), textSecondary = 0xFF5E4F37.toInt(),
                dim = 0xFF9A8B70.toInt(), pageBackground = 0xFFE8DDC8.toInt(), cardBackground = 0xFFD9CBB0.toInt(),
                sky = sky(0xFF9A8B70, 0xFF6C5FA0, 0xFF2F6B8A, 0xFF9B4F7A, 0xFFB03A2E, 0xFFC0561A, 0xFFA97612, 0xFFA97612),
                blueHour = 0xFF2F6B8A.toInt(), goldenHour = 0xFFC0561A.toInt(),
                highSun = 0xFFA97612.toInt(), twilightBand = 0xFF9B4F7A.toInt(),
            )
            // Vivid themes: MeteoBlue's navy, ilMeteo's orange, and saturated blues, yellows, teals, purples, reds and greens.
            Theme.METEOBLUE -> Palette(
                curveStyle, theme, accent,
                background = 0xFF163466.toInt(), text = 0xFFFFFFFF.toInt(), textSecondary = 0xFFBCD0F0.toInt(),
                dim = 0xFF7F9BCB.toInt(), pageBackground = 0xFF11264D.toInt(), cardBackground = 0xFF1B3F78.toInt(),
                sky = sky(0xFF1B3F78, 0xFF2C5AA8, 0xFF4DA3FF, 0xFF9B8CFF, 0xFFFF6B8A, 0xFFFFA94D, 0xFFFFE066, 0xFFFFFFFF),
                blueHour = 0xFF4DA3FF.toInt(), goldenHour = 0xFFFFA94D.toInt(),
                highSun = 0xFFFFE066.toInt(), twilightBand = 0xFF7FB6FF.toInt(),
            )
            Theme.TURCHESE -> Palette(
                curveStyle, theme, accent,
                background = 0xFF00636C.toInt(), text = 0xFFFFFFFF.toInt(), textSecondary = 0xFFCFF3F6.toInt(),
                dim = 0xFF6FB5BC.toInt(), pageBackground = 0xFF00535B.toInt(), cardBackground = 0xFF006F79.toInt(),
                sky = sky(0xFF006F79, 0xFF1F6FA8, 0xFF38C6D9, 0xFF9B7CFF, 0xFFFF6F91, 0xFFFFB74D, 0xFFFFE57F, 0xFFFFFFFF),
                blueHour = 0xFF38C6D9.toInt(), goldenHour = 0xFFFFB74D.toInt(),
                highSun = 0xFFFFE57F.toInt(), twilightBand = 0xFF9B7CFF.toInt(),
            )
            Theme.VIOLA -> Palette(
                curveStyle, theme, accent,
                background = 0xFF4A1F9F.toInt(), text = 0xFFFFFFFF.toInt(), textSecondary = 0xFFDCCCFF.toInt(),
                dim = 0xFF9C86E0.toInt(), pageBackground = 0xFF3A157F.toInt(), cardBackground = 0xFF5527B8.toInt(),
                sky = sky(0xFF5527B8, 0xFF7A4DE0, 0xFF5CC8FF, 0xFFC77DFF, 0xFFFF5FA2, 0xFFFF9F5A, 0xFFFFE066, 0xFFFFFFFF),
                blueHour = 0xFF5CC8FF.toInt(), goldenHour = 0xFFFF9F5A.toInt(),
                highSun = 0xFFFFE066.toInt(), twilightBand = 0xFFC77DFF.toInt(),
            )
            Theme.CORALLO -> Palette(
                curveStyle, theme, accent,
                background = 0xFF932420.toInt(), text = 0xFFFFF1EF.toInt(), textSecondary = 0xFFFFCFC9.toInt(),
                dim = 0xFFE08E86.toInt(), pageBackground = 0xFF85201B.toInt(), cardBackground = 0xFFA82A24.toInt(),
                sky = sky(0xFFA82A24, 0xFF8E3FA8, 0xFF4DA3FF, 0xFFC77DFF, 0xFFFFB3A7, 0xFFFFC857, 0xFFFFE066, 0xFFFFFFFF),
                blueHour = 0xFF7FB6FF.toInt(), goldenHour = 0xFFFFC857.toInt(),
                highSun = 0xFFFFE066.toInt(), twilightBand = 0xFFC77DFF.toInt(),
            )
            Theme.LIME -> Palette(
                curveStyle, theme, accent,
                background = 0xFF0F4F20.toInt(), text = 0xFFF0FFE8.toInt(), textSecondary = 0xFFBDEBB0.toInt(),
                dim = 0xFF6DB760.toInt(), pageBackground = 0xFF0B3D16.toInt(), cardBackground = 0xFF14602A.toInt(),
                sky = sky(0xFF14602A, 0xFF2D8A4A, 0xFF4DD0E1, 0xFFB39DFF, 0xFFFF8A80, 0xFFFFB74D, 0xFFFFEE58, 0xFFF0FFE8),
                blueHour = 0xFF4DD0E1.toInt(), goldenHour = 0xFFFFB74D.toInt(),
                highSun = 0xFFFFEE58.toInt(), twilightBand = 0xFFB39DFF.toInt(),
            )
            Theme.ILMETEO -> Palette(
                curveStyle, theme, accent,
                background = 0xFFFFE2B8.toInt(), text = 0xFF2B1500.toInt(), textSecondary = 0xFF5C3A10.toInt(),
                dim = 0xFFB8864A.toInt(), pageBackground = 0xFFFFD79A.toInt(), cardBackground = 0xFFFFC066.toInt(),
                sky = sky(0xFFB8864A, 0xFF5B5BC8, 0xFF1E6FB8, 0xFFB03A8A, 0xFFD32F2F, 0xFFE65100, 0xFFF57C00, 0xFFF57C00),
                blueHour = 0xFF1E6FB8.toInt(), goldenHour = 0xFFE65100.toInt(),
                highSun = 0xFFF57C00.toInt(), twilightBand = 0xFF5B5BC8.toInt(),
            )
            Theme.AZZURRO -> Palette(
                curveStyle, theme, accent,
                background = 0xFFDDF1FF.toInt(), text = 0xFF06203D.toInt(), textSecondary = 0xFF1F4A75.toInt(),
                dim = 0xFF6C9BC8.toInt(), pageBackground = 0xFFCDEBFF.toInt(), cardBackground = 0xFF9ED4FF.toInt(),
                sky = sky(0xFF6C9BC8, 0xFF4B5FD6, 0xFF0A6CE0, 0xFF8A3FD8, 0xFFD6293E, 0xFFE85D04, 0xFFE09F00, 0xFFE09F00),
                blueHour = 0xFF0A6CE0.toInt(), goldenHour = 0xFFE85D04.toInt(),
                highSun = 0xFFE09F00.toInt(), twilightBand = 0xFF8A3FD8.toInt(),
            )
            Theme.GIALLO -> Palette(
                curveStyle, theme, accent,
                background = 0xFFFFF5C2.toInt(), text = 0xFF2A2000.toInt(), textSecondary = 0xFF5C4A00.toInt(),
                dim = 0xFFB39B2E.toInt(), pageBackground = 0xFFFFF0A8.toInt(), cardBackground = 0xFFFFE066.toInt(),
                sky = sky(0xFFB39B2E, 0xFF6A5ACD, 0xFF1E70C8, 0xFFB0368F, 0xFFD32F2F, 0xFFE65100, 0xFFC77700, 0xFFC77700),
                blueHour = 0xFF1E70C8.toInt(), goldenHour = 0xFFE65100.toInt(),
                highSun = 0xFFC77700.toInt(), twilightBand = 0xFF6A5ACD.toInt(),
            )
            // Minimalist: low-saturation greys, sepia, sage and lavender; the sky ramp stays nearly colourless.
            Theme.MIN_NEUTRAL_DARK -> Palette(
                curveStyle, theme, accent,
                background = 0xFF1C1C1C.toInt(), text = 0xFFF5F5F5.toInt(), textSecondary = 0xFFBDBDBD.toInt(),
                dim = 0xFF777777.toInt(), pageBackground = 0xFF101010.toInt(), cardBackground = 0xFF1C1C1C.toInt(),
                sky = sky(0xFF2B2B2B, 0xFF3A3A3A, 0xFF6B6B6B, 0xFF8C8C8C, 0xFFAAAAAA, 0xFFC4C4C4, 0xFFDADADA, 0xFFF5F5F5),
                blueHour = 0xFF9AA7B5.toInt(), goldenHour = 0xFFC9A57A.toInt(),
                highSun = 0xFFD9C48C.toInt(), twilightBand = 0xFFA79FB8.toInt(),
            )
            Theme.MIN_NEUTRAL_LIGHT -> Palette(
                curveStyle, theme, accent,
                background = 0xFFFAFAFA.toInt(), text = 0xFF282828.toInt(), textSecondary = 0xFF707070.toInt(),
                dim = 0xFFA8A8A8.toInt(), pageBackground = 0xFFF0F0F0.toInt(), cardBackground = 0xFFFAFAFA.toInt(),
                sky = sky(0xFFA8A8A8, 0xFF8B8B8B, 0xFF777777, 0xFF686868, 0xFF5C5C5C, 0xFF525252, 0xFF484848, 0xFF484848),
                blueHour = 0xFF5F6F80.toInt(), goldenHour = 0xFF8A6A45.toInt(),
                highSun = 0xFF8A7440.toInt(), twilightBand = 0xFF6F6880.toInt(),
            )
            Theme.MIN_SEPIA_DARK -> Palette(
                curveStyle, theme, accent,
                background = 0xFF28231F.toInt(), text = 0xFFF5F0E9.toInt(), textSecondary = 0xFFC8BCAC.toInt(),
                dim = 0xFF887B6E.toInt(), pageBackground = 0xFF181512.toInt(), cardBackground = 0xFF28231F.toInt(),
                sky = sky(0xFF39322B, 0xFF3A3A3A, 0xFF6B6B6B, 0xFF8C8C8C, 0xFFAAAAAA, 0xFFC4C4C4, 0xFFDADADA, 0xFFF5F5F5),
                blueHour = 0xFF9AA7B5.toInt(), goldenHour = 0xFFC9A57A.toInt(),
                highSun = 0xFFD9C48C.toInt(), twilightBand = 0xFFA79FB8.toInt(),
            )
            Theme.MIN_SEPIA_LIGHT -> Palette(
                curveStyle, theme, accent,
                background = 0xFFFCFAF6.toInt(), text = 0xFF302A25.toInt(), textSecondary = 0xFF776D61.toInt(),
                dim = 0xFFB3A99B.toInt(), pageBackground = 0xFFF3F0EA.toInt(), cardBackground = 0xFFFCFAF6.toInt(),
                sky = sky(0xFFB3A99B, 0xFF8B8B8B, 0xFF777777, 0xFF686868, 0xFF5C5C5C, 0xFF525252, 0xFF484848, 0xFF484848),
                blueHour = 0xFF5F6F80.toInt(), goldenHour = 0xFF8A6A45.toInt(),
                highSun = 0xFF8A7440.toInt(), twilightBand = 0xFF6F6880.toInt(),
            )
            Theme.MIN_SAGE_DARK -> Palette(
                curveStyle, theme, accent,
                background = 0xFF242C25.toInt(), text = 0xFFF1F5F0.toInt(), textSecondary = 0xFFC0CDC2.toInt(),
                dim = 0xFF819084.toInt(), pageBackground = 0xFF151A16.toInt(), cardBackground = 0xFF242C25.toInt(),
                sky = sky(0xFF354037, 0xFF3A3A3A, 0xFF6B6B6B, 0xFF8C8C8C, 0xFFAAAAAA, 0xFFC4C4C4, 0xFFDADADA, 0xFFF5F5F5),
                blueHour = 0xFF9AA7B5.toInt(), goldenHour = 0xFFC9A57A.toInt(),
                highSun = 0xFFD9C48C.toInt(), twilightBand = 0xFFA79FB8.toInt(),
            )
            Theme.MIN_SAGE_LIGHT -> Palette(
                curveStyle, theme, accent,
                background = 0xFFFAFCF9.toInt(), text = 0xFF2C382D.toInt(), textSecondary = 0xFF667563.toInt(),
                dim = 0xFFACB9A9.toInt(), pageBackground = 0xFFF0F3EF.toInt(), cardBackground = 0xFFFAFCF9.toInt(),
                sky = sky(0xFFACB9A9, 0xFF8B8B8B, 0xFF777777, 0xFF686868, 0xFF5C5C5C, 0xFF525252, 0xFF484848, 0xFF484848),
                blueHour = 0xFF5F6F80.toInt(), goldenHour = 0xFF8A6A45.toInt(),
                highSun = 0xFF8A7440.toInt(), twilightBand = 0xFF6F6880.toInt(),
            )
            Theme.MIN_LAVENDER_DARK -> Palette(
                curveStyle, theme, accent,
                background = 0xFF25232E.toInt(), text = 0xFFF5F2F9.toInt(), textSecondary = 0xFFC8C3D3.toInt(),
                dim = 0xFF898397.toInt(), pageBackground = 0xFF17151D.toInt(), cardBackground = 0xFF25232E.toInt(),
                sky = sky(0xFF373442, 0xFF3A3A3A, 0xFF6B6B6B, 0xFF8C8C8C, 0xFFAAAAAA, 0xFFC4C4C4, 0xFFDADADA, 0xFFF5F5F5),
                blueHour = 0xFF9AA7B5.toInt(), goldenHour = 0xFFC9A57A.toInt(),
                highSun = 0xFFD9C48C.toInt(), twilightBand = 0xFFA79FB8.toInt(),
            )
            Theme.MIN_LAVENDER_LIGHT -> Palette(
                curveStyle, theme, accent,
                background = 0xFFFCFBFD.toInt(), text = 0xFF302B3B.toInt(), textSecondary = 0xFF6F6780.toInt(),
                dim = 0xFFB2A9C0.toInt(), pageBackground = 0xFFF2F0F6.toInt(), cardBackground = 0xFFFCFBFD.toInt(),
                sky = sky(0xFFB2A9C0, 0xFF8B8B8B, 0xFF777777, 0xFF686868, 0xFF5C5C5C, 0xFF525252, 0xFF484848, 0xFF484848),
                blueHour = 0xFF5F6F80.toInt(), goldenHour = 0xFF8A6A45.toInt(),
                highSun = 0xFF8A7440.toInt(), twilightBand = 0xFF6F6880.toInt(),
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
        /** A light accent (amber, white…) is darkened on a light theme so text in it stays readable. */
        fun readable(accent: Int, onLight: Boolean): Int {
            if (!onLight) return accent
            val luminance = (0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent)) / 255
            return if (luminance > 0.45) Palette.blend(accent, Color.BLACK, 0.45f) else accent
        }

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

/** Status and navigation bars in the page colour, with dark icons on a light theme. */
fun Palette.styleSystemBars(window: android.view.Window) {
    window.statusBarColor = pageBackground
    window.navigationBarColor = pageBackground
    if (Build.VERSION.SDK_INT >= 30) {
        val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.decorView.windowInsetsController?.setSystemBarsAppearance(if (isLight) mask else 0, mask)
    } else {
        @Suppress("DEPRECATION")
        var flags = window.decorView.systemUiVisibility
        @Suppress("DEPRECATION")
        val light = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        flags = if (isLight) flags or light else flags and light.inv()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = flags
    }
}
