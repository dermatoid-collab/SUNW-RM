package it.sunw.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs

/** Draws the Moon's disc with its lit part, as seen from the given hemisphere. */
object MoonRenderer {

    fun draw(sizePx: Int, phase: MoonCalculator.Phase, southernHemisphere: Boolean, dark: Int, lit: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = sizePx / 2f - 1
        val c = sizePx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(c, c, r, paint.apply { color = dark })

        // Northern hemisphere: waxing Moon is lit on the right. Draw that case and mirror as needed.
        val litOnRight = phase.waxing != southernHemisphere
        if (!litOnRight) canvas.scale(-1f, 1f, c, c)

        val k = phase.illumination.toFloat()
        val terminatorRx = abs(1 - 2 * k) * r
        val path = Path().apply {
            moveTo(c, c - r)
            // Lit limb: right half of the disc, top → bottom.
            arcTo(RectF(c - r, c - r, c + r, c + r), -90f, 180f)
            // Terminator: half-ellipse back to the top, bulging right for a crescent, left for a gibbous Moon.
            val oval = RectF(c - terminatorRx, c - r, c + terminatorRx, c + r)
            arcTo(oval, 90f, if (k < 0.5f) -180f else 180f)
            close()
        }
        canvas.drawPath(path, paint.apply { color = lit })
        // Faint limb so a nearly new Moon still reads as a disc.
        canvas.drawCircle(c, c, r - 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, sizePx / 48f)
            color = lit
            alpha = 70
        })
        return bitmap
    }
}
