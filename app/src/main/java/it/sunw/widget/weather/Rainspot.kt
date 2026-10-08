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
    private const val TRACES = 0xFF4C8F74.toInt()   // '9' measured
    private const val LIGHT = 0xFF509DA8.toInt()    // '1' measured
    private const val MODERATE = 0xFF3D74C4.toInt() // '2'
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

    /** Code for the cell at [row] (0 = north) and [col] (0 = west). */
    fun cell(rainspot: String, row: Int, col: Int): Char = rainspot[(6 - row) * 7 + col]

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
        val cellSize = box.width() / 7
        for (row in 0 until 7) for (col in 0 until 7) {
            paint.color = color(cell(rainspot, row, col)) ?: continue
            val left = box.left + col * cellSize
            val top = box.top + row * cellSize
            canvas.drawRect(left, top, left + cellSize, top + cellSize, paint)
        }
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
