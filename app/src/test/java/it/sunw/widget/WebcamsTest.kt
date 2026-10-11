package it.sunw.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import it.sunw.widget.webcams.OwnWebcam
import it.sunw.widget.webcams.OwnWebcamStore
import it.sunw.widget.webcams.WebcamViewActivity
import it.sunw.widget.webcams.WebcamsActivity
import it.sunw.widget.webcams.WindyWebcams
import it.sunw.widget.weather.MeteogramActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Windy Webcams parsing, the user's own list, and the pages that show them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebcamsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun place() {
        LocationStore(context).save(44.80, 10.33, automatic = false, name = "Parma")
    }

    private fun find(root: View, tag: Any): View? {
        if (root.tag == tag) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), tag)?.let { return it }
        return null
    }

    private val sample = """
        {"total":3,"webcams":[
          {"webcamId":2,"title":"Far","images":{"current":{"preview":"https://img/far.jpg"}},
           "location":{"latitude":45.3,"longitude":10.9},"urls":{"detail":"https://windy.com/webcams/2"}},
          {"webcamId":1,"title":"Near","images":{"current":{"thumbnail":"https://img/near-t.jpg","icon":"https://img/near-i.jpg"}},
           "location":{"latitude":44.81,"longitude":10.34},"urls":{"detail":"https://windy.com/webcams/1"}},
          {"webcamId":3,"title":"No image","location":{"latitude":44.8,"longitude":10.3}}
        ]}"""

    @Test
    fun windyListIsSortedByDistanceAndNeedsAnImage() {
        val list = WindyWebcams.parse(sample, 44.80, 10.33)
        assertEquals(listOf("Near", "Far"), list.map { it.title })
        assertEquals("https://img/near-t.jpg", list[0].imageUrl) // preview, else thumbnail, else icon
        assertEquals("https://windy.com/webcams/1", list[0].pageUrl)
        assertTrue(list[0].distanceKm < 3)
        assertTrue(list[1].distanceKm in 50.0..80.0)
        assertTrue(WindyWebcams.nearbyUrl(44.8, 10.33).contains("nearby=44.8000,10.3300,60"))
    }

    @Test
    fun ownListStartsWithTheFourWebcamsAndCanBeEdited() {
        val store = OwnWebcamStore(context)
        assertEquals(4, store.all().size)
        val cam = OwnWebcam("Test", "https://example.com/cam")
        store.add(cam)
        assertEquals(5, store.all().size)
        store.replace(cam, cam.copy(name = "Renamed"))
        assertEquals("Renamed", store.all().last().name)
        store.remove(store.all().last())
        assertEquals(4, store.all().size)
        assertEquals("Twitch", store.all()[1].kind)
        assertEquals("webtv.feratel.com", store.all()[2].host)
    }

    @Test
    fun anImageAddressIsKeptAndSavedWithTheBackup() {
        val store = OwnWebcamStore(context)
        val cam = OwnWebcam("Delta", "https://example.com/cam", "https://example.com/cam.jpg")
        store.add(cam)
        assertEquals("https://example.com/cam.jpg", store.all().last().imageUrl)
        assertEquals(null, store.all().first().imageUrl)
        val json = SettingsBackup.export(context)
        store.remove(cam)
        SettingsBackup.import(context, json)
        assertEquals("https://example.com/cam.jpg", OwnWebcamStore(context).all().last().imageUrl)
        store.replace(OwnWebcamStore(context).all().last(), cam.copy(imageUrl = null))
        assertEquals(null, OwnWebcamStore(context).all().last().imageUrl)
    }

    @Test
    fun theNameSitsBelowThePreviewOfEachOfMyWebcams() {
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        val mine = activity.findViewById<ViewGroup>(R.id.mg_webcams_mine)
        for (i in 0 until 4) {
            val tile = mine.getChildAt(i) as android.widget.LinearLayout
            assertEquals(2, tile.childCount)
            // Preview first (an icon and the kind of page when there is no picture), then the caption.
            val placeholder = (tile.getChildAt(0) as ViewGroup).getChildAt(0) as ViewGroup
            assertTrue(placeholder.getChildAt(0) is android.widget.ImageView) // the video-camera icon
            val kind = (placeholder.getChildAt(1) as TextView).text.toString()
            assertEquals(OwnWebcamStore.DEFAULTS[i].kind, kind)
            assertEquals(OwnWebcamStore.DEFAULTS[i].name, (tile.getChildAt(1) as TextView).text.toString())
        }
    }

    @Test
    fun addressesAreCheckedAndCompleted() {
        assertEquals("https://example.com/cam", OwnWebcamStore.normalizeUrl(" example.com/cam "))
        assertEquals("http://a.it/x", OwnWebcamStore.normalizeUrl("http://a.it/x"))
        assertNull(OwnWebcamStore.normalizeUrl(""))
        assertNull(OwnWebcamStore.normalizeUrl("not an address"))
        assertNull(OwnWebcamStore.normalizeUrl("localhost"))
    }

    @Test
    fun ownWebcamsAreInTheSettingsBackup() {
        val store = OwnWebcamStore(context)
        store.add(OwnWebcam("Mine", "https://example.com/m"))
        val json = SettingsBackup.export(context)
        store.remove(OwnWebcam("Mine", "https://example.com/m"))
        assertEquals(4, store.all().size)
        SettingsBackup.import(context, json)
        assertEquals(5, OwnWebcamStore(context).all().size)
    }

    @Test
    fun theRadarPageEndsWithAWebcamsSectionThatOpensTheList() {
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.mg_webcams_all).performClick()
        assertEquals(WebcamsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test
    fun theRadarPageShowsMyWebcamsAboveTheNearbyOnes() {
        val activity = Robolectric.buildActivity(MeteogramActivity::class.java).setup().get()
        val mine = activity.findViewById<ViewGroup>(R.id.mg_webcams_mine)
        assertEquals(4, mine.childCount)
        // Tiles with one or two lines of text must not be shifted to line their baselines up.
        assertTrue(!(mine as android.widget.LinearLayout).isBaselineAligned)
        assertEquals(OwnWebcamStore.DEFAULTS.map { it.url }, (0 until 4).map { mine.getChildAt(it).tag })
        // Order inside the tile: my webcams, then the nearby ones, then the button.
        val card = activity.findViewById<ViewGroup>(R.id.mg_webcams)
        val order = listOf(R.id.mg_webcams_mine_label, R.id.mg_webcams_mine, R.id.mg_webcams_near_label, R.id.mg_webcams_row, R.id.mg_webcams_all)
            .map { card.indexOfChild(activity.findViewById(it)) }
        assertTrue(order.all { it >= 0 })
        assertEquals(order, order.sorted())
        mine.getChildAt(1).performClick()
        val next = shadowOf(activity).nextStartedActivity
        assertEquals(WebcamViewActivity::class.java.name, next.component?.className)
        assertEquals(OwnWebcamStore.DEFAULTS[1].url, next.getStringExtra(WebcamViewActivity.EXTRA_URL))
    }

    @Test
    fun withoutAWindyKeyTheNearbyTabSaysSo() {
        val activity = Robolectric.buildActivity(WebcamsActivity::class.java).setup().get()
        assertEquals(WebcamsActivity.Tab.NEAR, activity.tab)
        // Tests are built without the key.
        val texts = mutableListOf<String>()
        fun collect(v: View) { if (v is TextView) texts += v.text.toString(); if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i)) }
        collect(activity.findViewById(R.id.wc_page))
        assertTrue(texts.any { it == activity.getString(R.string.wc_no_key) })
    }

    @Test
    fun myWebcamsOpenInTheViewer() {
        val activity = Robolectric.buildActivity(WebcamsActivity::class.java).setup().get()
        activity.show(WebcamsActivity.Tab.MINE)
        val url = OwnWebcamStore.DEFAULTS[0].url
        val row = find(activity.findViewById(R.id.wc_page), url)
        assertNotNull(row)
        row!!.performClick()
        val next = shadowOf(activity).nextStartedActivity
        assertEquals(WebcamViewActivity::class.java.name, next.component?.className)
        assertEquals(url, next.getStringExtra(WebcamViewActivity.EXTRA_URL))

        val viewer = Robolectric.buildActivity(WebcamViewActivity::class.java, next).setup().get()
        assertEquals(url, shadowOf(viewer.findViewById<View>(android.R.id.content).let { v ->
            var web: android.webkit.WebView? = null
            fun walk(x: View) { if (x is android.webkit.WebView) web = x; if (x is ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i)) }
            walk(v); web!!
        }).lastLoadedUrl)
    }
}
