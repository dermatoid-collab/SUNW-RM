package it.sunw.widget.webcams

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import it.sunw.widget.AppearanceStore
import it.sunw.widget.Palette
import it.sunw.widget.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * One webcam. A Windy webcam is an image reloaded every minute while the page is open; one of the
 * user's own is its web page or player inside the app (with "open in browser" as fallback).
 */
class WebcamViewActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val windy = WindyWebcams()
    private var windyId = 0L
    private var pageUrl: String? = null
    private var image: ImageView? = null
    private var web: WebView? = null
    private lateinit var status: TextView
    private var visible = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val palette = AppearanceStore(this).palette()
        window.decorView.setBackgroundColor(palette.pageBackground)
        window.statusBarColor = palette.pageBackground
        window.navigationBarColor = palette.pageBackground
        windyId = intent.getLongExtra(EXTRA_WINDY_ID, 0L)
        val own = intent.getStringExtra(EXTRA_URL)
        pageUrl = own ?: intent.getStringExtra(EXTRA_PAGE)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(16), 0)
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_arrow_back)
                setColorFilter(palette.text)
                setBackgroundResource(android.R.color.transparent)
                contentDescription = getString(R.string.sm_back)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(context).apply {
                text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                textSize = 20f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                setTextColor(palette.text)
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, dp(64)))

        val frame = FrameLayout(this)
        if (own != null) {
            web = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                setBackgroundColor(Color.BLACK)
                webViewClient = object : WebViewClient() {
                    // Navigation stays inside the page.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                }
                webChromeClient = WebChromeClient()
                loadUrl(own)
            }
            frame.addView(web, FrameLayout.LayoutParams(-1, -1))
        } else {
            image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.BLACK)
            }
            frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(dp(14), dp(4), dp(14), 0) })

        status = TextView(this).apply {
            textSize = 12.5f
            setTextColor(palette.textSecondary)
            setPadding(dp(18), dp(10), dp(18), 0)
            text = if (own != null) getString(R.string.wc_own_note) else getString(R.string.wc_windy_credit)
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = getString(R.string.wc_open_browser)
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(palette.text)
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Palette.blend(palette.cardBackground, palette.accent, 0.22f)) }
            visibility = if (pageUrl == null) View.GONE else View.VISIBLE
            setOnClickListener { pageUrl?.let { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } } }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(dp(14), dp(10), dp(14), dp(14)) })
        setContentView(root)
    }

    private val refresh = object : Runnable {
        override fun run() {
            reloadImage()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    /** Fetches the current image address (it expires) and shows the picture. */
    private fun reloadImage() {
        val view = image ?: return
        fun show(url: String?) {
            if (url == null) return
            ImageLoader.load(url) { bitmap ->
                if (bitmap != null && !isDestroyed) {
                    view.setImageBitmap(bitmap)
                    status.text = getString(R.string.wc_updated, DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalTime.now()))
                }
            }
        }
        if (windyId == 0L) return
        windy.currentImage(windyId) { result ->
            runOnUiThread { show(result.getOrNull() ?: intent.getStringExtra(EXTRA_IMAGE).takeIf { view.drawable == null }) }
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        web?.onResume()
        if (image != null) handler.post(refresh)
    }

    override fun onPause() {
        visible = false
        handler.removeCallbacks(refresh)
        web?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        web?.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_WINDY_ID = "windy_id"
        const val EXTRA_IMAGE = "image"
        const val EXTRA_PAGE = "page"
        const val EXTRA_URL = "url"
        private const val REFRESH_MS = 60_000L
    }
}
