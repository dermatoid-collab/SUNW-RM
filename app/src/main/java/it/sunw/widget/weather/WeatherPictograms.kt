package it.sunw.widget.weather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Coloured weather pictograms (sun or moon, clouds, rain, snow, lightning, fog) on a 48×48 grid,
 * used for the large icon of the day page; the small icons are the monochrome [WeatherIcons].
 * Original artwork in the spirit of Meteoblue's pictograms.
 */
object WeatherPictograms {

    private const val SUN = 0xFFF6C445.toInt()
    private const val MOON = 0xFFF2E2A6.toInt()
    private const val STAR = 0xFFCFD8EA.toInt()
    private const val CLOUD = 0xFFEEF1F6.toInt()
    private const val CLOUD_DARK = 0xFFB9C2CF.toInt()
    private const val RAIN = 0xFF8FB8F2.toInt()
    private const val SNOW = 0xFFFFFFFF.toInt()
    private const val BOLT = 0xFFF0569B.toInt()
    private const val FOG = 0xFFA9B2BF.toInt()

    fun draw(sizePx: Int, condition: Condition, night: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(sizePx / 48f, sizePx / 48f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val c = condition
        if (c == Condition.CLEAR) {
            if (night) moon(canvas, p, 24f, 24f, 13f) else sun(canvas, p, 24f, 24f, 9f)
            return bitmap
        }
        if (c.hasSunOrMoon) {
            if (night) moon(canvas, p, 32f, 14f, 9f) else sun(canvas, p, 31f, 15f, 7.5f)
        }
        val heavy = c in setOf(Condition.OVERCAST, Condition.RAIN, Condition.HEAVY_RAIN, Condition.SNOW, Condition.SLEET, Condition.FOG)
        when (c) {
            Condition.MOSTLY_CLEAR -> cloud(canvas, p, 6f, 22f, 0.7f, CLOUD)
            Condition.PARTLY_CLOUDY -> cloud(canvas, p, 3f, 17f, 0.9f, CLOUD)
            Condition.MOSTLY_CLOUDY -> {
                cloud(canvas, p, 12f, 10f, 0.75f, CLOUD_DARK)
                cloud(canvas, p, 2f, 16f, 0.95f, CLOUD)
            }
            else -> {
                if (heavy) cloud(canvas, p, 12f, 6f, 0.75f, CLOUD_DARK)
                cloud(canvas, p, 2f, 11f, 1f, if (c == Condition.FOG) CLOUD_DARK else CLOUD)
            }
        }
        when (c) {
            Condition.LIGHT_RAIN -> drops(canvas, p, 14f, 37f, 2)
            Condition.SHOWERS, Condition.RAIN -> drops(canvas, p, 11f, 37f, 3)
            Condition.HEAVY_RAIN -> drops(canvas, p, 9f, 37f, 4)
            Condition.SNOW, Condition.SNOW_SHOWERS -> flakes(canvas, p, 12f, 40f, 3)
            Condition.SLEET -> {
                drops(canvas, p, 11f, 37f, 1); flakes(canvas, p, 20f, 40f, 1); drops(canvas, p, 27f, 37f, 1)
            }
            Condition.THUNDERSTORM -> {
                bolt(canvas, p, 14f, 30f); drops(canvas, p, 25f, 36f, 2)
            }
            Condition.FOG -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.strokeCap = Paint.Cap.ROUND; p.color = FOG
                canvas.drawLine(8f, 38f, 40f, 38f, p); canvas.drawLine(12f, 43f, 36f, 43f, p)
                p.style = Paint.Style.FILL
            }
            else -> Unit
        }
        return bitmap
    }

    private fun sun(canvas: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        p.color = SUN
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 - 90)
            canvas.drawLine(
                cx + (r + 3) * cos(a).toFloat(), cy + (r + 3) * sin(a).toFloat(),
                cx + (r + 7) * cos(a).toFloat(), cy + (r + 7) * sin(a).toFloat(), p,
            )
        }
        p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r, p)
    }

    private fun moon(canvas: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = MOON
        val crescent = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            op(Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        }
        canvas.drawPath(crescent, p)
        p.color = STAR
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.strokeCap = Paint.Cap.ROUND
        for ((x, y) in listOf(cx - r - 4 to cy - r + 2, cx + r + 2 to cy - r - 1, cx + r + 4 to cy + 3)) {
            canvas.drawLine(x, y - 2.5f, x, y + 2.5f, p)
            canvas.drawLine(x - 2.5f, y, x + 2.5f, y, p)
        }
        p.style = Paint.Style.FILL
    }

    /** Cloud with its bottom-left near (x, y + 26·s). */
    private fun cloud(canvas: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(s, s)
        canvas.drawCircle(17f, 13f, 10f, p)
        canvas.drawCircle(29f, 16f, 8f, p)
        canvas.drawCircle(9f, 19f, 7f, p)
        canvas.drawRoundRect(RectF(2f, 16f, 40f, 26f), 5f, 5f, p)
        canvas.restore()
    }

    private fun drops(canvas: Canvas, p: Paint, x: Float, y: Float, n: Int) {
        p.color = RAIN; p.style = Paint.Style.STROKE; p.strokeWidth = 1.8f; p.strokeCap = Paint.Cap.ROUND
        repeat(n) { i -> canvas.drawLine(x + i * 7, y, x + i * 7 - 2.5f, y + 6, p) }
        p.style = Paint.Style.FILL
    }

    private fun flakes(canvas: Canvas, p: Paint, x: Float, y: Float, n: Int) {
        p.color = SNOW; p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f; p.strokeCap = Paint.Cap.ROUND
        repeat(n) { i ->
            val cx = x + i * 9
            for (k in 0 until 3) {
                val a = Math.toRadians(k * 60.0)
                canvas.drawLine(cx - 3 * cos(a).toFloat(), y - 3 * sin(a).toFloat(), cx + 3 * cos(a).toFloat(), y + 3 * sin(a).toFloat(), p)
            }
        }
        p.style = Paint.Style.FILL
    }

    private fun bolt(canvas: Canvas, p: Paint, x: Float, y: Float) {
        p.color = BOLT; p.style = Paint.Style.FILL
        canvas.drawPath(Path().apply {
            moveTo(x + 6, y); lineTo(x, y + 10); lineTo(x + 5, y + 10); lineTo(x + 2, y + 18)
            lineTo(x + 11, y + 6); lineTo(x + 6, y + 6); lineTo(x + 9, y); close()
        }, p)
    }
}
