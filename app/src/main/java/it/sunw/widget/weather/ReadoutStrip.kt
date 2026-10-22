package it.sunw.widget.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import it.sunw.widget.Palette
import it.sunw.widget.R

/**
 * The field under a chart that reads out the values of the hour under the cursor (the day page's style): the
 * time on the first line, the values in bold on the second, each in the colour of its line, and the details on the
 * third. Without a cursor it reads out "now", so the field is never empty.
 */
internal object ReadoutStrip {
    const val HEIGHT_DP = 58f

    const val GAP_DP = 8f

    /** Draws the field between [left] and [right], its top at [top] (pixels). */
    fun draw(
        canvas: Canvas, paint: Paint, context: Context, p: Palette,
        left: Float, right: Float, top: Float,
        whenText: String, values: List<Pair<String, Int>>, detail: String,
    ) {
        val metrics = context.resources.displayMetrics
        fun dp(v: Float) = v * metrics.density
        fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, metrics)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.color = p.raised(0.07f)
        canvas.drawRoundRect(RectF(left, top, right, top + dp(HEIGHT_DP)), dp(10f), dp(10f), paint)
        val pad = dp(10f)
        val x0 = left + pad
        val maxWidth = right - left - 2 * pad
        paint.textAlign = Paint.Align.LEFT

        paint.typeface = Typeface.DEFAULT
        paint.textSize = sp(11f)
        paint.color = p.textSecondary
        canvas.drawText(whenText, x0, top + dp(17f), paint)

        // The values, shrunk to fit when they are too wide.
        val separator = " · "
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = sp(14f)
        val natural = values.sumOf { paint.measureText(it.first).toDouble() }.toFloat() + paint.measureText(separator) * (values.size - 1)
        paint.textSize = sp(if (natural > maxWidth) 14f * maxWidth / natural else 14f)
        var x = x0
        for ((k, value) in values.withIndex()) {
            if (k > 0) {
                paint.color = p.textSecondary
                canvas.drawText(separator, x, top + dp(36f), paint)
                x += paint.measureText(separator)
            }
            paint.color = value.second
            canvas.drawText(value.first, x, top + dp(36f), paint)
            x += paint.measureText(value.first)
        }

        paint.typeface = Typeface.DEFAULT
        paint.textSize = sp(11.5f)
        val detailWidth = paint.measureText(detail)
        if (detailWidth > maxWidth) paint.textSize = sp(11.5f * maxWidth / detailWidth)
        paint.color = p.textSecondary
        canvas.drawText(detail, x0, top + dp(52f), paint)
        paint.textSize = sp(11f)
    }

    /** "from SW 235°": where the wind comes from, in degrees and the eight winds' names. */
    fun windFrom(context: Context, degrees: Int): String {
        val names = context.resources.getStringArray(R.array.wind_compass)
        val d = ((degrees % 360) + 360) % 360
        return context.getString(R.string.wind_from, names[((d + 22) % 360) / 45], d)
    }
}
