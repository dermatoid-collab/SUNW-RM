package it.sunw.widget.webcams

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import it.sunw.widget.AppearanceStore
import it.sunw.widget.LocationStore
import it.sunw.widget.MainActivity
import it.sunw.widget.Palette
import it.sunw.widget.Place
import it.sunw.widget.R
import it.sunw.widget.styleSystemBars
import java.util.Locale

/**
 * Webcams around the place (Windy previews) and the user's own list (web pages and live players).
 * Opened from the section at the bottom of the radar and meteogram page.
 */
class WebcamsActivity : Activity() {

    enum class Tab { NEAR, MINE }

    internal var tab = Tab.NEAR
        private set

    private lateinit var palette: Palette
    private lateinit var place: Place
    private lateinit var content: LinearLayout
    private lateinit var tabNear: TextView
    private lateinit var tabMine: TextView
    private val windy = WindyWebcams()
    private val store by lazy { OwnWebcamStore(this) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = AppearanceStore(this).palette()
        place = LocationStore(this).current()
        window.decorView.setBackgroundColor(palette.pageBackground)
        palette.styleSystemBars(window)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(header())
        root.addView(tabs())
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(12), dp(14), dp(20)) })
        setContentView(ScrollView(this).apply {
            id = R.id.wc_page
            isFillViewport = true
            fitsSystemWindows = true
            addView(root)
        })
        show(savedInstanceState?.getString(STATE_TAB)?.let { runCatching { Tab.valueOf(it) }.getOrNull() } ?: Tab.NEAR)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, tab.name)
    }

    private fun header(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(16), 0)
        addView(ImageButton(context).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(palette.text)
            setBackgroundResource(android.R.color.transparent)
            contentDescription = getString(R.string.sm_back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
            addView(TextView(context).apply {
                text = getString(R.string.wc_title)
                textSize = 22f
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                setTextColor(palette.text)
            })
            addView(TextView(context).apply {
                text = getString(R.string.wc_subtitle, MainActivity.placeLabel(this@WebcamsActivity, place), WindyWebcams.RADIUS_KM)
                textSize = 13f
                setTextColor(palette.textSecondary)
                maxLines = 1
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }.also { it.layoutParams = ViewGroup.LayoutParams(-1, dp(64)) }

    private fun tabs(): View = LinearLayout(this).apply {
        tabNear = tabView(R.string.wc_tab_near) { show(Tab.NEAR) }
        tabMine = tabView(R.string.wc_tab_mine) { show(Tab.MINE) }
        addView(tabNear, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        addView(tabMine, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(4) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(4), dp(14), 0) }
    }

    private fun tabView(label: Int, click: () -> Unit) = TextView(this).apply {
        text = getString(label)
        gravity = Gravity.CENTER
        textSize = 14f
        setOnClickListener { click() }
    }

    internal fun show(newTab: Tab) {
        tab = newTab
        for ((view, own) in listOf(tabNear to Tab.NEAR, tabMine to Tab.MINE)) {
            val on = own == tab
            view.setTextColor(if (on) palette.text else palette.textSecondary)
            view.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (on) Palette.blend(palette.cardBackground, palette.accent, 0.22f) else Color.TRANSPARENT)
            }
        }
        content.removeAllViews()
        if (tab == Tab.NEAR) showNearby() else showMine()
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = (getDrawable(R.drawable.card_background)!!.mutate() as GradientDrawable).apply { setColor(palette.cardBackground) }
    }

    private fun note(text: String, size: Float = 12f) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(palette.textSecondary)
        setPadding(0, dp(8), 0, 0)
    }

    private fun showNearby() {
        val card = card().also { content.addView(it) }
        if (!windy.hasKey) {
            card.addView(note(getString(R.string.wc_no_key), 14f))
            return
        }
        val status = note(getString(R.string.wc_loading), 14f).also { card.addView(it) }
        windy.nearby(place) { result ->
            runOnUiThread {
                if (isDestroyed || tab != Tab.NEAR) return@runOnUiThread
                card.removeView(status)
                val list = result.getOrNull()
                when {
                    list == null -> card.addView(note(getString(R.string.wc_error), 14f))
                    list.isEmpty() -> card.addView(note(getString(R.string.wc_none, WindyWebcams.RADIUS_KM), 14f))
                    else -> {
                        list.chunked(2).forEach { pair ->
                            card.addView(LinearLayout(this).apply {
                                pair.forEach { addView(nearbyCell(it), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(6), dp(4), dp(6)) }) }
                                if (pair.size == 1) addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                            })
                        }
                        card.addView(note(getString(R.string.wc_windy_note)))
                    }
                }
            }
        }
    }

    internal fun nearbyCell(cam: Webcam): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        tag = cam.id
        addView(thumbnail(cam.imageUrl, 100))
        addView(TextView(context).apply {
            text = cam.title
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(palette.text)
            setPadding(0, dp(6), 0, 0)
        })
        addView(TextView(context).apply {
            text = String.format(Locale.getDefault(), "%.0f km", cam.distanceKm)
            textSize = 11f
            setTextColor(palette.textSecondary)
        })
        setOnClickListener {
            startActivity(Intent(context, WebcamViewActivity::class.java)
                .putExtra(WebcamViewActivity.EXTRA_TITLE, cam.title)
                .putExtra(WebcamViewActivity.EXTRA_WINDY_ID, cam.id)
                .putExtra(WebcamViewActivity.EXTRA_IMAGE, cam.imageUrl)
                .putExtra(WebcamViewActivity.EXTRA_PAGE, cam.pageUrl))
        }
    }

    private fun thumbnail(url: String?, heightDp: Int): ImageView = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LinearLayout.LayoutParams(-1, dp(heightDp))
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(palette.raised(0.08f)) }
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        if (url != null) ImageLoader.load(url, 640) { bitmap -> if (bitmap != null) setImageBitmap(bitmap) }
    }

    private fun showMine() {
        val card = card().also { content.addView(it) }
        store.all().forEach { cam ->
            card.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                tag = cam.url
                setPadding(dp(12), dp(8), dp(4), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(palette.raised(0.07f)) }
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply { text = cam.name; textSize = 15f; setTextColor(palette.text) })
                    addView(TextView(context).apply { text = "${cam.kind} · ${cam.host}"; textSize = 12f; setTextColor(palette.textSecondary); maxLines = 1 })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(context).apply {
                    text = "⋮"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(palette.textSecondary)
                    contentDescription = getString(R.string.wc_edit)
                    setOnClickListener { editMenu(cam) }
                }, LinearLayout.LayoutParams(dp(44), dp(44)))
                setOnClickListener {
                    startActivity(Intent(context, WebcamViewActivity::class.java)
                        .putExtra(WebcamViewActivity.EXTRA_TITLE, cam.name)
                        .putExtra(WebcamViewActivity.EXTRA_URL, cam.url))
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        card.addView(TextView(this).apply {
            id = R.id.wc_add
            text = getString(R.string.wc_add)
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(palette.accent)
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setStroke(dp(1), palette.textSecondary) }
            setOnClickListener { editDialog(null) }
        }, LinearLayout.LayoutParams(-1, dp(44)))
        card.addView(note(getString(R.string.wc_mine_note)))
    }

    private fun editMenu(cam: OwnWebcam) {
        AlertDialog.Builder(this)
            .setTitle(cam.name)
            .setItems(arrayOf(getString(R.string.wc_edit), getString(R.string.wc_delete))) { _, which ->
                if (which == 0) editDialog(cam) else {
                    store.remove(cam)
                    show(Tab.MINE)
                }
            }
            .show()
    }

    /** Name and address of a webcam: new when [existing] is null. */
    internal fun editDialog(existing: OwnWebcam?) {
        val pad = dp(20)
        val name = EditText(this).apply { hint = getString(R.string.wc_name); setText(existing?.name.orEmpty()); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; id = R.id.wc_name }
        val url = EditText(this).apply { hint = getString(R.string.wc_address); setText(existing?.url.orEmpty()); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; id = R.id.wc_address }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, dp(8), pad, 0); addView(name); addView(url) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.wc_add else R.string.wc_edit)
            .setView(box)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        // Validate without closing the dialog on a mistake.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val address = OwnWebcamStore.normalizeUrl(url.text.toString())
            if (address == null) {
                Toast.makeText(this, R.string.wc_bad_address, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val label = name.text.toString().trim().ifEmpty { Regex("^https?://(www\\.)?").replace(address, "").substringBefore('/') }
            val cam = OwnWebcam(label, address)
            if (existing == null) store.add(cam) else store.replace(existing, cam)
            dialog.dismiss()
            show(Tab.MINE)
        }
    }

    companion object {
        private const val STATE_TAB = "tab"
    }
}
