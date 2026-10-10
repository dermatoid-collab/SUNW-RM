package it.sunw.widget

import android.app.Activity
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.graphics.Rect
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Sun and Moon on any date within ±6 months of today: the sun tile in detail (curve with a
 * cursor, directions, twilights, blue and golden hours, comparisons), the Moon, the year's
 * day length, day tabs and a slider. Opened by tapping the sun or moon tile on the main page.
 */
class SunMoonActivity : Activity() {

    private lateinit var palette: Palette
    private lateinit var place: Place
    private val zone: ZoneId = ZoneId.systemDefault()
    private lateinit var today: LocalDate

    /** Days from today, −[RANGE_DAYS]..+[RANGE_DAYS]. */
    internal var offset = 0
        private set

    /** Moment the user touched on the curve; null shows "now" (today) or nothing. */
    private var touched: Instant? = null

    private val date: LocalDate get() = today.plusDays(offset.toLong())
    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).roundToInt()
    private val points by lazy { resources.getStringArray(R.array.compass_points) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sun_moon)
        palette = AppearanceStore(this).palette()
        place = LocationStore(this).current()
        today = LocalDate.now(zone)
        offset = savedInstanceState?.getInt(STATE_OFFSET)
            ?: intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { ChronoUnit.DAYS.between(today, it).toInt() }
            ?: 0
        offset = offset.coerceIn(-RANGE_DAYS, RANGE_DAYS)

        window.decorView.setBackgroundColor(palette.pageBackground)
        window.statusBarColor = palette.pageBackground
        window.navigationBarColor = palette.pageBackground
        styleStaticParts()
        setUpYear()
        setUpSlider()
        render()
    }

    /** Horizontal flings move a day, as on the weather day page: left → next day, right → previous. */
    private val swipe by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                if (abs(dx) < dp(SWIPE_MIN_DP) || abs(dx) < 1.5f * abs(dy) || abs(velocityX) < SWIPE_MIN_VELOCITY) return false
                if (startsOnControl(start)) return false
                val target = offset + if (dx < 0) 1 else -1
                if (target !in -RANGE_DAYS..RANGE_DAYS) return false
                select(target)
                return true
            }
        })
    }

    /** The curve, the year strip and the slider use horizontal drags themselves. */
    private fun startsOnControl(e: MotionEvent): Boolean = listOf(R.id.sm_curve, R.id.sm_year, R.id.sm_slider).any { id ->
        val r = Rect()
        findViewById<View>(id).getGlobalVisibleRect(r) && r.contains(e.rawX.toInt(), e.rawY.toInt())
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (swipe.onTouchEvent(ev)) {
            // Let the page know the gesture ended so it doesn't keep a half-finished scroll.
            ev.action = MotionEvent.ACTION_CANCEL
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_OFFSET, offset)
    }

    private fun card(color: Int, radiusDp: Int = 24) =
        GradientDrawable().apply { cornerRadius = dp(radiusDp).toFloat(); setColor(color) }

    private fun styleStaticParts() {
        findViewById<ImageButton>(R.id.sm_back).apply {
            setColorFilter(palette.text)
            setOnClickListener { finish() }
        }
        findViewById<TextView>(R.id.sm_place).apply {
            text = MainActivity.placeLabel(this@SunMoonActivity, place)
            setTextColor(palette.text)
            // Leave room for the date beside a long place name.
            maxWidth = (resources.displayMetrics.widthPixels * 0.5f).toInt()
        }
        findViewById<TextView>(R.id.sm_date).setTextColor(palette.textSecondary)
        // Same background as the sun tile on the main page.
        findViewById<View>(R.id.sm_sun_card).setBackgroundResource(palette.theme.background)
        findViewById<View>(R.id.sm_moon_card).background = card(palette.cardBackground)
        val inner = Palette.blend(palette.cardBackground, Color.WHITE, 0.05f)
        for (id in listOf(R.id.sm_useful_box, R.id.sm_morning, R.id.sm_evening)) {
            findViewById<View>(id).background = card(inner, 12)
        }
        for ((id, color) in listOf(
            R.id.sm_sunrise to palette.text, R.id.sm_sunset to palette.text,
            R.id.sm_rise_az to palette.textSecondary, R.id.sm_set_az to palette.textSecondary,
            R.id.sm_length to palette.text, R.id.sm_delta to palette.textSecondary,
            R.id.sm_useful_label to palette.textSecondary, R.id.sm_useful to palette.text,
            R.id.sm_morning to palette.text, R.id.sm_evening to palette.text,
            R.id.sm_compare to palette.textSecondary,
            R.id.sm_moon_pct to palette.text, R.id.sm_moon_name to palette.textSecondary,
            R.id.sm_moon_times to palette.text, R.id.sm_moon_next to palette.textSecondary,
            R.id.sm_next_event to palette.text,
            R.id.sm_min to palette.textSecondary, R.id.sm_max to palette.textSecondary,
            R.id.sm_today to palette.accent,
        )) findViewById<TextView>(id).setTextColor(color)
        for (id in listOf(R.id.sm_sunrise, R.id.sm_sunset)) {
            findViewById<TextView>(id).compoundDrawableTintList = ColorStateList.valueOf(palette.accent)
        }
        findViewById<TextView>(R.id.sm_moon_next).setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, TypedValue.COMPLEX_UNIT_SP)
        findViewById<SunPathView>(R.id.sm_curve).onCursor = { t ->
            touched = t
            showCursor(SunDayFacts(date, place.latitude, place.longitude, zone))
        }
        findViewById<TextView>(R.id.sm_today).setOnClickListener { select(0) }
        findViewById<View>(R.id.sm_date).setOnClickListener { pickDate() }
    }

    /** Daylight over the whole range, the equinoxes and solstices in it: computed once. */
    private fun setUpYear() {
        val minutes = IntArray(2 * RANGE_DAYS + 1) { i ->
            SunDayFacts.daylight(today.plusDays((i - RANGE_DAYS).toLong()), place.latitude, place.longitude, zone).toMinutes().toInt()
        }
        val from = today.minusDays(RANGE_DAYS.toLong()).atStartOfDay(zone).toInstant()
        val marks = SunCalculator.Season.values().mapNotNull { season ->
            val day = SunCalculator.nextSeason(season, from).atZone(zone).toLocalDate()
            val o = ChronoUnit.DAYS.between(today, day).toInt()
            if (o in -RANGE_DAYS..RANGE_DAYS) YearStripView.Mark(o, getString(seasonNames(season).second)) else null
        }
        findViewById<YearStripView>(R.id.sm_year).apply {
            show(palette, minutes, marks, RANGE_DAYS)
            // Not the strip's own select(), which only moves its dot: the page must change date.
            onPick = { this@SunMoonActivity.select(it) }
        }
    }

    private fun setUpSlider() {
        findViewById<SeekBar>(R.id.sm_slider).apply {
            max = 2 * RANGE_DAYS
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            progressBackgroundTintList = ColorStateList.valueOf(palette.textSecondary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) select(progress - RANGE_DAYS, animate = false)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        val short = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        findViewById<TextView>(R.id.sm_min).text = short.format(today.minusDays(RANGE_DAYS.toLong())).replace(".", "")
        findViewById<TextView>(R.id.sm_max).text = short.format(today.plusDays(RANGE_DAYS.toLong())).replace(".", "")
    }

    /** The system date picker, limited to the slider's range. */
    internal fun pickDate(): DatePickerDialog {
        val d = date
        val dialog = DatePickerDialog(this, { _, year, month, day ->
            select(ChronoUnit.DAYS.between(today, LocalDate.of(year, month + 1, day)).toInt())
        }, d.year, d.monthValue - 1, d.dayOfMonth)
        fun millis(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        dialog.datePicker.minDate = millis(today.minusDays(RANGE_DAYS.toLong()))
        dialog.datePicker.maxDate = millis(today.plusDays(RANGE_DAYS.toLong()))
        dialog.show()
        return dialog
    }

    /** Shows the date [newOffset] days from today; tabs and the Today button slide the content in. */
    internal fun select(newOffset: Int, animate: Boolean = true) {
        val target = newOffset.coerceIn(-RANGE_DAYS, RANGE_DAYS)
        if (target == offset) return
        val delta = (target - offset).coerceIn(-1, 1)
        offset = target
        touched = null
        render()
        if (animate) slideIn(delta)
    }

    private fun slideIn(delta: Int) {
        val content = findViewById<View>(R.id.sm_content)
        content.animate().cancel()
        content.translationX = delta * content.width * SLIDE_FRACTION
        content.alpha = 0f
        content.animate().translationX(0f).alpha(1f)
            .setDuration(resources.getInteger(R.integer.page_transition_ms).toLong())
            .setInterpolator(AnimationUtils.loadInterpolator(this, android.R.interpolator.fast_out_slow_in))
            .start()
    }

    private fun render() {
        val facts = SunDayFacts(date, place.latitude, place.longitude, zone)
        showHeader()
        showSun(facts)
        showMoon()
        showYear(facts)
        showTabs()
        findViewById<SeekBar>(R.id.sm_slider).progress = offset + RANGE_DAYS
        findViewById<TextView>(R.id.sm_today).visibility = if (offset == 0) View.INVISIBLE else View.VISIBLE
    }

    /** "Fri 9 Oct" as on the main page; the year only when it isn't this year's. */
    private fun showHeader() {
        val pattern = if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy"
        findViewById<TextView>(R.id.sm_date).text = DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date)
            .replace(".", "").replaceFirstChar { it.titlecase() }
    }

    private fun time(t: Instant?) = t?.let { Formatters.time(this, it, zone) } ?: DASH
    private fun span(s: SunDayFacts.Span?) = s?.let { "${time(it.start)}–${time(it.end)}" } ?: DASH

    private fun showSun(facts: SunDayFacts) {
        val now = Instant.now()
        findViewById<SunPathView>(R.id.sm_curve).show(palette, facts, touched ?: now.takeIf { offset == 0 })
        showCursor(facts)

        val normal = facts.normal
        findViewById<TextView>(R.id.sm_sunrise).text = time(normal?.sunrise)
        findViewById<TextView>(R.id.sm_sunset).text = time(normal?.sunset)
        findViewById<TextView>(R.id.sm_rise_az).text = facts.sunriseAzimuth?.let { SunDayFacts.direction(it, points) } ?: ""
        findViewById<TextView>(R.id.sm_set_az).text = facts.sunsetAzimuth?.let { SunDayFacts.direction(it, points) } ?: ""
        findViewById<TextView>(R.id.sm_length).text = when (facts.day) {
            is SunCalculator.Day.Normal -> Formatters.length(facts.daylight)
            is SunCalculator.Day.PolarDay -> getString(R.string.polar_day)
            is SunCalculator.Day.PolarNight -> getString(R.string.polar_night)
        }
        val yesterday = SunDayFacts.daylight(date.minusDays(1), place.latitude, place.longitude, zone)
        findViewById<TextView>(R.id.sm_delta).text =
            if (normal != null) Formatters.delta(facts.daylight.seconds - yesterday.seconds) else ""

        findViewById<TextView>(R.id.sm_useful).text = facts.usefulLight?.let {
            SpannableStringBuilder("${span(it)} · ").append(Formatters.length(it.length), ForegroundColorSpan(palette.accent), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } ?: DASH

        showTwilights(facts)
        findViewById<SunCompassView>(R.id.sm_compass).show(
            palette, facts.sunriseAzimuth, facts.sunsetAzimuth, place.latitude < 0,
            arrayOf(points[0], points[4], points[8], points[12]),
        )
        findViewById<TextView>(R.id.sm_morning).text = hours(R.string.sm_morning,
            R.string.sm_blue to facts.morningBlue, R.string.sm_golden to facts.morningGolden)
        findViewById<TextView>(R.id.sm_evening).text = hours(R.string.sm_evening,
            R.string.sm_golden to facts.eveningGolden, R.string.sm_blue to facts.eveningBlue)
        findViewById<TextView>(R.id.sm_compare).text = compare(facts)
    }

    /** "Morning" over "● Blue 07:01–07:10" and "● Golden 07:10–07:52", dots in their colours. */
    private fun hours(title: Int, vararg rows: Pair<Int, SunDayFacts.Span?>): CharSequence {
        val text = SpannableStringBuilder().append(getString(title), ForegroundColorSpan(palette.textSecondary), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        for ((label, s) in rows) {
            val color = if (label == R.string.sm_blue) palette.blueHour else palette.goldenHour
            text.append("\n").append("● ", ForegroundColorSpan(color), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                .append(getString(label, span(s)))
        }
        return text
    }

    private fun showTwilights(facts: SunDayFacts) {
        val table = findViewById<LinearLayout>(R.id.sm_twilights)
        table.removeAllViews()
        fun row(name: String, a: String, b: String?, color: Int = palette.text, nameColor: Int = palette.textSecondary) {
            table.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
                addView(cell(name, nameColor, 1.15f, Gravity.START))
                if (b != null) {
                    addView(cell(a, color, 1f, Gravity.CENTER_HORIZONTAL))
                    addView(cell(b, color, 1f, Gravity.CENTER_HORIZONTAL))
                } else {
                    addView(cell(a, color, 2f, Gravity.CENTER_HORIZONTAL))
                }
            })
        }
        row("", getString(R.string.sm_dawn), getString(R.string.sm_dusk), palette.textSecondary)
        for ((name, c) in listOf(
            R.string.sm_civil to facts.civil, R.string.sm_nautical to facts.nautical, R.string.sm_astronomical to facts.astronomical,
        )) row(getString(name), time(c.morning), time(c.evening))
        row(getString(R.string.sm_noon), getString(R.string.sm_noon_value, time(facts.day.solarNoon), facts.noonElevation.roundToInt()), null)
    }

    private fun cell(value: String, color: Int, weight: Float, gravity: Int) = TextView(this).apply {
        text = value
        textSize = 13f
        maxLines = 1
        this.gravity = gravity
        setTextColor(color)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
    }

    /** Daylight vs today and the two solstices of the chosen date's year. */
    private fun compare(facts: SunDayFacts): String {
        val parts = mutableListOf<String>()
        fun signed(d: Duration) = (if (d.isNegative) "−" else "+") + Formatters.length(d.abs())
        if (offset != 0) {
            val todayLength = SunDayFacts.daylight(today, place.latitude, place.longitude, zone)
            parts += getString(R.string.sm_vs, getString(R.string.sm_today_lower), signed(facts.daylight.minus(todayLength)))
        }
        val yearStart = date.withDayOfYear(1).atStartOfDay(zone).toInstant()
        val dm = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        for (season in listOf(SunCalculator.Season.JUNE_SOLSTICE, SunCalculator.Season.DECEMBER_SOLSTICE)) {
            val day = SunCalculator.nextSeason(season, yearStart).atZone(zone).toLocalDate()
            val length = SunDayFacts.daylight(day, place.latitude, place.longitude, zone)
            parts += getString(R.string.sm_vs, dm.format(day).replace(".", ""), signed(facts.daylight.minus(length)))
        }
        return parts.joinToString(" · ")
    }

    /** "14:35 (now) · 32° · 210° SSW · shadow ×1.6" for the cursor, or a hint. */
    private fun showCursor(facts: SunDayFacts) {
        val label = findViewById<TextView>(R.id.sm_cursor)
        val t = touched ?: Instant.now().takeIf { offset == 0 }
        if (t == null) {
            label.text = getString(R.string.sm_cursor_hint)
            label.setTextColor(palette.dim)
            return
        }
        val elevation = SunCalculator.elevation(t, facts.latitude, facts.longitude)
        val azimuth = SunCalculator.azimuth(t, facts.latitude, facts.longitude)
        val parts = mutableListOf(
            time(t) + (if (touched == null) " " + getString(R.string.sm_now) else ""),
            "${elevation.roundToInt()}°",
            SunDayFacts.direction(azimuth, points),
        )
        parts += if (elevation > 0.5) {
            getString(R.string.sm_shadow, String.format(Locale.getDefault(), "%.1f", 1 / tan(Math.toRadians(elevation))))
        } else {
            getString(R.string.sm_below_horizon)
        }
        label.text = parts.joinToString(" · ")
        label.setTextColor(palette.text)
    }

    private fun showMoon() {
        val noon = date.atTime(12, 0).atZone(zone).toInstant()
        val phase = MoonCalculator.phase(if (offset == 0) Instant.now() else noon)
        findViewById<ImageView>(R.id.sm_moon_image).setImageBitmap(
            MoonRenderer.draw(dp(64), phase, place.latitude < 0, Palette.blend(palette.cardBackground, Color.WHITE, 0.08f), MOON_LIT),
        )
        findViewById<TextView>(R.id.sm_moon_pct).text = getString(R.string.percent, (phase.illumination * 100).roundToInt())
        findViewById<TextView>(R.id.sm_moon_name).text = getString(MainActivity.phaseName(phase.name))
        val riseSet = MoonCalculator.riseSet(date, place.latitude, place.longitude, zone)
        val parts = listOfNotNull(
            riseSet.rise?.let { getString(R.string.moon_rise, time(it)) },
            riseSet.set?.let { getString(R.string.moon_set, time(it)) },
        )
        findViewById<TextView>(R.id.sm_moon_times).text = if (parts.isEmpty()) getString(R.string.sm_moon_no_events) else parts.joinToString("   ")
        val ddmm = DateTimeFormatter.ofPattern("dd/MM")
        findViewById<TextView>(R.id.sm_moon_next).text = MoonCalculator.nextQuarters(date.atStartOfDay(zone).toInstant())
            .joinToString(" • ") { (quarter, at) -> getString(MainActivity.quarterName(quarter), ddmm.format(at.atZone(zone))) }
    }

    private fun showYear(facts: SunDayFacts) {
        findViewById<YearStripView>(R.id.sm_year).select(offset)
        // The first equinox or solstice after the chosen date.
        val from = date.plusDays(1).atStartOfDay(zone).toInstant()
        val (season, at) = SunCalculator.Season.values().map { it to SunCalculator.nextSeason(it, from) }.minBy { it.second }
        val day = at.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(date, day).toInt()
        val length = SunDayFacts.daylight(day, place.latitude, place.longitude, zone)
        val d = length.minus(facts.daylight)
        findViewById<TextView>(R.id.sm_next_event).text = getString(
            R.string.sm_next_event,
            getString(seasonNames(season).first),
            DateTimeFormatter.ofPattern("dd/MM").format(day),
            resources.getQuantityString(R.plurals.sm_in_days, days, days),
            (if (d.isNegative) "−" else "+") + Formatters.length(d.abs()),
        )
    }

    /** Seven days centred on the chosen one (kept inside the range); a dot marks today. */
    private fun showTabs() {
        val tabs = findViewById<LinearLayout>(R.id.sm_tabs)
        tabs.removeAllViews()
        val first = (offset - 3).coerceIn(-RANGE_DAYS, RANGE_DAYS - 6)
        val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM")
        for (o in first until first + 7) {
            val d = today.plusDays(o.toLong())
            val isSelected = o == offset
            tabs.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(7), 0, dp(6))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(2); marginEnd = dp(2)
                }
                if (isSelected) background = card(Palette.blend(palette.cardBackground, palette.accent, 0.22f), 14)
                setOnClickListener { select(o) }
                addView(tabText(dayFormat.format(d).trimEnd('.').replaceFirstChar { it.titlecase() }, 14f,
                    if (isSelected) palette.text else palette.textSecondary))
                addView(tabText(dateFormat.format(d), 12f, palette.textSecondary))
                addView(View(this@SunMoonActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(5), dp(5)).apply { topMargin = dp(3) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (o == 0) palette.accent else Color.TRANSPARENT)
                    }
                })
            })
        }
    }

    private fun tabText(value: String, sizeSp: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = sizeSp
        maxLines = 1
        gravity = Gravity.CENTER_HORIZONTAL
        setTextColor(color)
    }

    /** (full name, short mark) for the hemisphere: the March equinox is spring in the north. */
    private fun seasonNames(season: SunCalculator.Season): Pair<Int, Int> {
        val north = place.latitude >= 0
        return when (season) {
            SunCalculator.Season.MARCH_EQUINOX -> (if (north) R.string.sm_spring_equinox else R.string.sm_autumn_equinox) to R.string.sm_mark_march
            SunCalculator.Season.JUNE_SOLSTICE -> (if (north) R.string.sm_summer_solstice else R.string.sm_winter_solstice) to R.string.sm_mark_june
            SunCalculator.Season.SEPTEMBER_EQUINOX -> (if (north) R.string.sm_autumn_equinox else R.string.sm_spring_equinox) to R.string.sm_mark_september
            SunCalculator.Season.DECEMBER_SOLSTICE -> (if (north) R.string.sm_winter_solstice else R.string.sm_summer_solstice) to R.string.sm_mark_december
        }
    }

    companion object {
        const val EXTRA_DATE = "date"
        const val RANGE_DAYS = 182
        private const val STATE_OFFSET = "offset"
        private const val SLIDE_FRACTION = 0.25f
        private const val SWIPE_MIN_DP = 80
        private const val SWIPE_MIN_VELOCITY = 600f
        private const val DASH = "—"
        private const val MOON_LIT = 0xFFECE6D2.toInt()
    }
}
