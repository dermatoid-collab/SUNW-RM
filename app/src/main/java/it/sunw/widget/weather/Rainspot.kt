package it.sunw.widget.weather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * The "rainSPOT" target, drawn like the Meteoblue app: black rounded square with a thin white
 * border, the 7×7 precipitation grid filling it, three white rings and four tick marks across
 * the outer ring (north, south, east, west). The centre cell is the place itself.
 *
 * Encoding (MeteoBlue `rainspot`, 49 digits): the string runs **south to north** — characters
 * 0–6 are the bottom row, 42–48 the top row — and west to east within a row (the same reading
 * as openHAB's meteoblue binding). Digits: 0 none, 9 traces/showers, 1 light, 2 moderate,
 * 3 heavy. Colours for 9 and 1 were measured from a Meteoblue screenshot; 2 and 3 continue
 * Meteoblue's green → teal → blue → violet precipitation scale.
 */
object Rainspot {

    private const val BACKGROUND = 0xFF000000.toInt()
    // Tones tuned against a side-by-side screenshot with the Meteoblue app on the same phone:
    // green and teal a little deeper than first measured, and the moderate level a muted
    // steel blue (Meteoblue has no bright blue there).
    private const val TRACES = 0xFF388F74.toInt()   // '9'
    private const val LIGHT = 0xFF3C9DA8.toInt()    // '1'
    private const val MODERATE = 0xFF3D7EA6.toInt() // '2'
    private const val HEAVY = 0xFF6A4FB8.toInt()    // '3'
    private const val WHITE = 0xFFF8F8F8.toInt()

    fun color(code: Char): Int? = when (code) {
        '9' -> TRACES
        '1' -> LIGHT
        '2' -> MODERATE
        '3' -> HEAVY
        else -> null
    }

    /** Whether any cell expects precipitation. */
    fun hasRain(rainspot: String?) = rainspot?.any { it != '0' } == true

    /**
     * The picture is moved this many columns east of the string's own layout: compared hour by hour
     * with the Meteoblue app (same place, two days, 20+ hours), its target shows the same shapes one
     * column further east, with 97 % of the filled cells in the same colour once shifted (73 % not
     * shifted). Rows line up exactly. The string has no cell for the new west column, so it stays dry.
     */
    internal const val EAST_SHIFT = 1

    /** Code for the cell at [row] (0 = north) and [col] (0 = west), as the string lays it out. */
    fun cell(rainspot: String, row: Int, col: Int): Char = rainspot[(6 - row) * 7 + col]

    /** What the target shows at [row] and [col]: the string's cells moved [EAST_SHIFT] column(s) east. */
    fun shown(rainspot: String, row: Int, col: Int): Char =
        if (col < EAST_SHIFT) '0' else cell(rainspot, row, col - EAST_SHIFT)

    /** The 8 pixel positions of the 7 cell boundaries across [length] starting at [start]. */
    internal fun edges(start: Float, length: Float): IntArray =
        IntArray(8) { Math.round(start + length * it / 7f) }

    fun draw(sizePx: Int, rainspot: String): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = sizePx.toFloat()
        val line = maxOf(1f, s * 0.012f)
        val box = RectF(line / 2, line / 2, s - line / 2, s - line / 2)
        val corner = s * 0.14f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Grid, clipped to the rounded square.
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(box, corner, corner, Path.Direction.CW) })
        canvas.drawColor(BACKGROUND)
        // Cells on whole pixels and without anti-aliasing: neighbours then meet with no seam.
        val xs = edges(box.left, box.width())
        val ys = edges(box.top, box.height())
        paint.isAntiAlias = false
        for (row in 0 until 7) for (col in 0 until 7) {
            paint.color = color(shown(rainspot, row, col)) ?: continue
            canvas.drawRect(xs[col].toFloat(), ys[row].toFloat(), xs[col + 1].toFloat(), ys[row + 1].toFloat(), paint)
        }
        paint.isAntiAlias = true
        canvas.restore()

        // Target: three rings, ticks across the outer ring, border.
        val c = s / 2
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = line
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = WHITE
        canvas.drawCircle(c, c, s * 0.31f, paint)
        canvas.drawCircle(c, c, s * 0.19f, paint)
        canvas.drawCircle(c, c, s * 0.065f, paint)
        val inner = s * 0.25f
        val outer = s * 0.39f
        canvas.drawLine(c, c - outer, c, c - inner, paint)
        canvas.drawLine(c, c + inner, c, c + outer, paint)
        canvas.drawLine(c - outer, c, c - inner, c, paint)
        canvas.drawLine(c + inner, c, c + outer, c, paint)
        canvas.drawRoundRect(box, corner, corner, paint)
        return bitmap
    }
}
