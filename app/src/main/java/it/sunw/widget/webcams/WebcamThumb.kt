package it.sunw.widget.webcams

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import it.sunw.widget.Palette
import it.sunw.widget.raised

/**
 * Preview box of one of the user's webcams: its picture when an image address is set, otherwise a
 * plain tile with the kind of page (Twitch, Feratel, Web), since a page or player has no picture.
 */
object WebcamThumb {

    fun create(context: Context, cam: OwnWebcam, palette: Palette, radiusDp: Float = 10f): View {
        val d = context.resources.displayMetrics.density
        val box = FrameLayout(context).apply {
            background = GradientDrawable().apply { cornerRadius = radiusDp * d; setColor(palette.raised(0.08f)) }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        box.addView(TextView(context).apply {
            text = cam.kind
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(palette.textSecondary)
        }, FrameLayout.LayoutParams(-1, -1))
        cam.imageUrl?.let { url ->
            val picture = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
            box.addView(picture, FrameLayout.LayoutParams(-1, -1))
            // The picture covers the kind label once it has arrived; until then (or if it fails) the label stays.
            ImageLoader.load(url, 480) { bitmap ->
                if (bitmap != null) {
                    picture.setImageBitmap(bitmap)
                    picture.visibility = View.VISIBLE
                }
            }
        }
        return box
    }
}
