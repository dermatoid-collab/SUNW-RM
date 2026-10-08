package it.sunw.widget.weather

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import it.sunw.widget.AppearanceStore
import it.sunw.widget.Formatters
import it.sunw.widget.LocationStore
import it.sunw.widget.MainActivity
import it.sunw.widget.MoonCalculator
import it.sunw.widget.Palette
import it.sunw.widget.R
import it.sunw.widget.SunCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One forecast day in detail, laid out like the Meteoblue app's day view: day tabs (or swipe
 * left/right between days), a hero with icon and max/min, sun / moon / rain / wind with the
 * rainspot target, then the hour-by-hour table (1 h or 3 h) scrolled to the current hour.
 * Works from the cached MeteoBlue forecast; Sun and Moon come from the on-device calculators.
 */
class WeatherDayActivity : Activity() {

    private lateinit var palette: Palette
    private lateinit var forecast: Forecast
    private lateinit var selected: LocalDate
    private var stepHours = 1
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).roundToInt()

    /** Horizontal flings anywhere on the page (except the tab strip) move to the next/previous day. */
    private val swipe by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                if (abs(dx) < dp(SWIPE_MIN_DP) || abs(dx) < 1.5f * abs(dy) || abs(velocityX) < SWIPE_MIN_VELOCITY) return false
                if (isOverTabs(start)) return false
                return showDay(if (dx < 0) 1 else -1)
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_day)
        palette = AppearanceStore(this).palette()
        window.decorView.setBackgroundColor(palette.pageBackground)
        window.statusBarColor = palette.pageBackground
        window.navigationBarColor = palette.pageBackground

        val place = LocationStore(this).current()
        findViewById<TextView>(R.id.day_back).apply {
            text = getString(R.string.detail_back, MainActivity.placeLabel(this@WeatherDayActivity, place))
            setTextColor(palette.text)
            compoundDrawableTintList = ColorStateList.valueOf(palette.text)
            setOnClickListener { finish() }
        }
        for (id in listOf(R.id.day_hero, R.id.day_info_card, R.id.day_hourly_card)) {
            findViewById<View>(id).background =
                (getDrawable(R.drawable.card_background)!!.mutate() as GradientDrawable).apply { setColor(palette.cardBackground) }
        }
        findViewById<TextView>(R.id.day_hourly_title).setTextColor(palette.text)
        findViewById<TextView>(R.id.day_status).setTextColor(palette.textSecondary)

        val cached = WeatherRepository(this).cached(place)
        if (cached == null || cached.days.isEmpty()) {
            findViewById<TextView>(R.id.day_status).text = getString(R.string.detail_no_data)
            listOf(R.id.day_hero, R.id.day_info_card, R.id.day_hourly_card).forEach { findViewById<View>(it).visibility = View.GONE }
            return
        }
        forecast = cached
        stepHours = savedInstanceState?.getInt(STATE_STEP) ?: 1
        selected = (savedInstanceState?.getString(STATE_DATE) ?: intent.getStringExtra(EXTRA_DATE))
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.takeIf { d -> forecast.days.any { it.date == d } }
            ?: forecast.days.first().date
        findViewById<TextView>(R.id.day_step).apply {
            setTextColor(palette.accent)
            setOnClickListener {
                stepHours = if (stepHours == 1) 3 else 1
                showHours()
                if (focusedRow != null) scrollToFocusedHour() // other days keep their position
            }
        }
        render()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::selected.isInitialized && swipe.onTouchEvent(ev)) {
            // Let the page know the gesture ended so it doesn't keep a half-finished scroll.
            ev.action = MotionEvent.ACTION_CANCEL
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isOverTabs(e: MotionEvent): Boolean {
        val tabs = findViewById<HorizontalScrollView>(R.id.day_tabs_scroll) ?: return false
        val loc = IntArray(2).also { tabs.getLocationOnScreen(it) }
        return e.rawY >= loc[1] && e.rawY <= loc[1] + tabs.height
    }

    /**
     * Slides the new day in from the side it comes from (from the right for a later day), like
     * turning a page; [delta] 0 means no movement.
     */
    private fun slideIn(delta: Int) {
        if (delta == 0) return
        val content = findViewById<View>(R.id.day_content)
        content.animate().cancel()
        content.translationX = delta * content.width * SLIDE_FRACTION
        content.alpha = 0f
        content.animate().translationX(0f).alpha(1f).setDuration(SLIDE_MS)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    /** Moves [delta] days (±1); false at either end of the forecast. */
    internal fun showDay(delta: Int): Boolean {
        val index = forecast.days.indexOfFirst { it.date == selected } + delta
        if (index !in forecast.days.indices) return false
        selected = forecast.days[index].date
        render()
        slideIn(delta)
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::selected.isInitialized) outState.putString(STATE_DATE, selected.toString())
        outState.putInt(STATE_STEP, stepHours)
    }

    private fun render() {
        showTabs()
        showHero()
        showInfo()
        showHours()
        val updated = DateTimeFormatter.ofPattern("HH:mm").format(forecast.fetchedAt.atZone(zone))
        findViewById<TextView>(R.id.day_status).text = getString(R.string.weather_source, updated)
        scrollToFocusedHour()
    }

    private fun showTabs() {
        val tabs = findViewById<LinearLayout>(R.id.day_tabs)
        tabs.removeAllViews()
        val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM")
        for (day in forecast.days) {
            val isSelected = day.date == selected
            tabs.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) }
                if (isSelected) background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(Palette.blend(palette.cardBackground, palette.accent, 0.22f))
                }
                setOnClickListener {
                    val delta = day.date.compareTo(selected).coerceIn(-1, 1)
                    selected = day.date
                    render()
                    slideIn(delta)
                }
                addView(text(dayFormat.format(day.date).trimEnd('.').replaceFirstChar { it.titlecase() }, 15.5f,
                    if (isSelected) palette.text else palette.textSecondary, Gravity.CENTER_HORIZONTAL))
                addView(ImageView(this@WeatherDayActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                    WeatherIcons.show(this, day.condition, night = false, color = if (isSelected) palette.text else palette.textSecondary)
                })
                addView(text(dateFormat.format(day.date), 12f, palette.textSecondary, Gravity.CENTER_HORIZONTAL))
            })
        }
        val index = forecast.days.indexOfFirst { it.date == selected }
        tabs.post {
            tabs.getChildAt(index)?.let { findViewById<HorizontalScrollView>(R.id.day_tabs_scroll)?.smoothScrollTo(it.left - dp(40), 0) }
        }
    }

    private fun day() = forecast.days.first { it.date == selected }

    private fun showHero() {
        val day = day()
        findViewById<ImageView>(R.id.day_icon).setImageBitmap(WeatherPictograms.draw(dp(84), day.condition, night = false))
        findViewById<TextView>(R.id.day_title).apply {
            text = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(day.date)
                .replaceFirstChar { it.titlecase() }
            setTextColor(palette.text)
        }
        findViewById<TextView>(R.id.day_desc).apply {
            val night = forecast.nightCondition(day.date)
            text = if (night != null && night != day.condition) {
                getString(R.string.detail_day_night, getString(day.condition.label), getString(night.label).lowercase())
            } else {
                getString(day.condition.label)
            }
            setTextColor(palette.text)
        }
        findViewById<LinearLayout>(R.id.day_pills).apply {
            removeAllViews()
            addView(WeatherCard.temperaturePill(this@WeatherDayActivity, day.temperatureMax, 17f, 46))
            addView(WeatherCard.temperaturePill(this@WeatherDayActivity, day.temperatureMin, 17f, 46).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(5)
            })
        }
    }

    /** Sun and Moon from the on-device calculators; rain and wind from MeteoBlue. */
    private fun showInfo() {
        val day = day()
        val place = LocationStore(this).current()
        val info = findViewById<TextView>(R.id.day_info)
        val iconPx = (info.textSize * 1.15f).roundToInt()
        val text = SpannableStringBuilder()
        when (val sun = SunCalculator.day(day.date, place.latitude, place.longitude, zone)) {
            // Sunrise, sunset and day length as icons instead of words.
            is SunCalculator.Day.Normal -> text
                .icon(R.drawable.ic_sunrise, iconPx).append(" ${Formatters.time(this, sun.sunrise, zone)}   ")
                .icon(R.drawable.ic_sunset, iconPx).append(" ${Formatters.time(this, sun.sunset, zone)}   ")
                .icon(R.drawable.wx_sunny, iconPx, getColor(R.color.widget_accent)).append(" ${Formatters.length(sun.length)}")
            is SunCalculator.Day.PolarDay -> text.append(getString(R.string.polar_day))
            is SunCalculator.Day.PolarNight -> text.append(getString(R.string.polar_night))
        }
        val lines = mutableListOf<String>()
        val moon = MoonCalculator.riseSet(day.date, place.latitude, place.longitude, zone)
        val phase = MoonCalculator.phase(day.date.atTime(12, 0).atZone(zone).toInstant())
        lines += getString(
            R.string.detail_moon,
            moon.rise?.let { Formatters.time(this, it, zone) } ?: "—",
            moon.set?.let { Formatters.time(this, it, zone) } ?: "—",
            (phase.illumination * 100).roundToInt(),
        )
        lines += getString(R.string.detail_rain, day.precipitationProbability, WeatherCard.mm(day.precipitation)) +
            " · " + getString(R.string.detail_uv, day.uvIndex)
        val wind = getString(R.string.detail_wind, WeatherCard.windArrow(day.windDirection), day.windSpeedMax.roundToInt())
        text.append((lines + wind).joinToString("\n", prefix = "\n"))
        info.text = text
        info.setTextColor(palette.text)
        val spot = day.rainspot
        findViewById<View>(R.id.day_rainspot_box).visibility = if (spot != null) View.VISIBLE else View.GONE
        if (spot != null) {
            findViewById<ImageView>(R.id.day_rainspot).setImageBitmap(Rainspot.draw(dp(84), spot))
            findViewById<TextView>(R.id.day_rainspot_label).setTextColor(palette.textSecondary)
        }
    }

    /** Appends [res] as an inline icon of [sizePx], sitting on the text baseline. */
    private fun SpannableStringBuilder.icon(res: Int, sizePx: Int, tint: Int? = null): SpannableStringBuilder {
        val drawable = getDrawable(res)!!.mutate().apply {
            setBounds(0, 0, sizePx, sizePx)
            tint?.let { setTint(it) }
        }
        val start = length
        append("\u00A0")
        setSpan(ImageSpan(drawable, DynamicDrawableSpan.ALIGN_BASELINE), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return this
    }

    private fun hoursOf(date: LocalDate): List<Forecast.Hour> = forecast.hours.filter { it.time.toLocalDate() == date }

    /** Current hour's row (today only): highlighted and brought into view. */
    private var focusedRow: View? = null

    /** Hour-by-hour table: time + temperature pill · icon · feels like · wind · rain mm · probability. */
    private fun showHours() {
        findViewById<TextView>(R.id.day_step).text = getString(if (stepHours == 1) R.string.detail_step_1h else R.string.detail_step_3h)
        val list = findViewById<LinearLayout>(R.id.day_hours)
        list.removeAllViews()
        focusedRow = null
        val hours = hoursOf(selected).filter { it.time.hour % stepHours == 0 }
        val nowLocal = forecast.localTime(Instant.now())
        val isToday = selected == nowLocal.toLocalDate()
        val focusHour = hours.lastOrNull { it.time.hour <= nowLocal.hour }?.time?.hour
        if (hours.isEmpty()) {
            list.addView(text(getString(R.string.detail_no_hours), 15.5f, palette.textSecondary))
            return
        }
        val divider = Palette.blend(palette.cardBackground, Color.WHITE, 0.18f)
        hours.forEachIndexed { i, h ->
            if (i > 0) list.addView(View(this).apply {
                setBackgroundColor(divider)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            })
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(8), dp(6), dp(8))
                val isNow = isToday && h.time.hour == focusHour
                if (isNow) {
                    focusedRow = this
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(Palette.blend(palette.cardBackground, palette.accent, 0.22f))
                        setStroke(dp(2), palette.accent)
                    }
                }
                addView(LinearLayout(this@WeatherDayActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.WRAP_CONTENT)
                    addView(text("%02d:00".format(h.time.hour), 14.5f, if (isNow) palette.accent else palette.textSecondary, Gravity.CENTER_HORIZONTAL).apply {
                        if (isNow) setTypeface(typeface, Typeface.BOLD)
                    })
                    addView(WeatherCard.temperaturePill(this@WeatherDayActivity, h.temperature, 17f, 53).apply {
                        (layoutParams as LinearLayout.LayoutParams).topMargin = dp(2)
                    })
                })
                // The other columns share the remaining width evenly, each centred in its cell,
                // so there are no large gaps; a row without rainspot keeps an empty cell.
                addView(ImageView(this@WeatherDayActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    WeatherIcons.show(this, h.condition, !h.isDaylight, palette.text)
                    contentDescription = getString(h.condition.label)
                })
                addView(text(getString(R.string.detail_felt, WeatherCard.deg(h.feltTemperature)), 12f, palette.textSecondary, Gravity.CENTER_HORIZONTAL).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(LinearLayout(this@WeatherDayActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
                    addView(text("${WeatherCard.windArrow(h.windDirection)} ${h.windSpeed.roundToInt()} km/h", 12f, palette.text, Gravity.CENTER_HORIZONTAL))
                    addView(text(WeatherCard.mm(h.precipitation), 14.5f, palette.text, Gravity.CENTER_HORIZONTAL))
                    addView(text("${h.precipitationProbability}%", 14.5f, RAIN_TEXT, Gravity.CENTER_HORIZONTAL))
                })
                addView(ImageView(this@WeatherDayActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
                    scaleType = ImageView.ScaleType.CENTER
                    h.rainspot?.let { spot ->
                        setImageBitmap(Rainspot.draw(dp(42), spot))
                        contentDescription = getString(R.string.rainspot_label)
                    }
                })
            }
            list.addView(row)
        }
    }

    /**
     * After the next layout pass (so the new rows have their positions), shows today's current
     * hour near the top of the page; any other day starts from the top.
     */
    private fun scrollToFocusedHour() {
        val page = findViewById<ScrollView>(R.id.day_page)
        page.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                page.viewTreeObserver.removeOnGlobalLayoutListener(this)
                page.scrollTo(0, focusedRowOffset(page)?.let { (it - dp(90)).coerceAtLeast(0) } ?: 0)
            }
        })
        page.requestLayout()
    }

    /** Top of the focused row inside the page, or null when there is none. */
    internal fun focusedRowOffset(page: View = findViewById(R.id.day_page)): Int? {
        var y = 0
        var v: View = focusedRow ?: return null
        while (v !== page) {
            y += v.top
            v = v.parent as? View ?: return null
        }
        return y
    }

    private fun text(value: String, sizeSp: Float, color: Int, gravity: Int = Gravity.START) = TextView(this).apply {
        text = value
        textSize = sizeSp
        maxLines = 1
        this.gravity = gravity
        setTextColor(color)
    }

    companion object {
        const val EXTRA_DATE = "date"
        private const val STATE_DATE = "date"
        private const val STATE_STEP = "step"
        private const val RAIN_TEXT = 0xFF8FB8F2.toInt()
        private const val SWIPE_MIN_DP = 80
        private const val SLIDE_MS = 260L

        /** How far (share of the width) the new day starts from. */
        private const val SLIDE_FRACTION = 0.6f
        private const val SWIPE_MIN_VELOCITY = 600f
    }
}
