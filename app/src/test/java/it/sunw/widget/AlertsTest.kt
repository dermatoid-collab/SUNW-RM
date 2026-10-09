package it.sunw.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
import it.sunw.widget.alerts.AlertRepository
import it.sunw.widget.alerts.AlertZones
import it.sunw.widget.alerts.Bulletin
import it.sunw.widget.weather.WeatherDayActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** Civil Protection alerts: zones, the CAP bulletin, and where the app shows them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlertsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun realBulletin(): Bulletin {
        val zip = javaClass.classLoader!!.getResourceAsStream("dpc/20261009_1615.zip").use { it.readBytes() }
        return Bulletin.parse(AlertRepository.capFromZip(zip))
    }

    @Test
    fun zonesAreFoundFromCoordinates() {
        val zones = AlertZones.get(context)
        assertEquals("Pianura piacentino-parmense", zones.zoneAt(44.8015, 10.3279))
        assertEquals("Alta Valtellina", zones.zoneAt(46.538, 10.135))
        assertEquals("Bacini di Roma", zones.zoneAt(41.9028, 12.4964))
        assertNull("Zurich", zones.zoneAt(47.37, 8.54))
        assertNull("open sea", zones.zoneAt(43.0, 10.0))
    }

    @Test
    fun realBulletinIsReadByZoneAndDay() {
        val b = realBulletin()
        assertEquals("Error", b.msgType) // an "errata corrige" of the 16:00 bulletin
        val today = LocalDate.of(2026, 10, 9)
        val naples = "Piana campana, Napoli, Isole e Area vesuviana"
        assertEquals(Bulletin.Level.ORANGE, b.levelFor(naples, today))
        assertEquals(Bulletin.Risk.HYDROGEOLOGICAL, b.forZone(naples, today).first().risk)
        assertTrue(b.forZone("Versante Tirrenico Settentrionale", today).any { it.risk == Bulletin.Risk.HYDRAULIC && it.level == Bulletin.Level.YELLOW })
        assertTrue(b.warnings.any { it.date == today.plusDays(1) })
        assertNull(b.levelFor("Pianura piacentino-parmense", today))
        // Every zone named by the bulletin is one of the bundled zones.
        val zoneNames = AlertZones.parse(context.assets.open("alert_zones.json").bufferedReader().readText())
        assertTrue(b.warnings.map { it.zone }.distinct().isNotEmpty())
        assertTrue(zoneNames.zoneAt(40.8518, 14.2681) == naples)
    }

    @Test
    fun eventTextGivesLevelAndRisk() {
        assertEquals(Bulletin.Level.RED, Bulletin.level("ELEVATA CRITICITA' PER RISCHIO IDRAULICO / ALLERTA ROSSA:"))
        assertEquals(Bulletin.Level.YELLOW, Bulletin.level("ORDINARIA CRITICITA' PER RISCHIO TEMPORALI / ALLERTA GIALLA:"))
        assertEquals(Bulletin.Risk.THUNDERSTORMS, Bulletin.risk("ORDINARIA CRITICITA' PER RISCHIO TEMPORALI / ALLERTA GIALLA:"))
        assertEquals(Bulletin.Risk.HYDRAULIC, Bulletin.risk("MODERATA CRITICITA' PER RISCHIO IDRAULICO / ALLERTA ARANCIONE:"))
    }

    /** A one-zone bulletin for Parma: orange thunderstorms today. */
    private fun parmaCap(today: LocalDate) = """
        <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
          <sent>${today}T16:00:00+02:00</sent><msgType>Actual</msgType>
          <info>
            <event>MODERATA CRITICITA' PER RISCHIO TEMPORALI / ALLERTA ARANCIONE:</event>
            <onset>${today}T00:00:00+02:00</onset><expires>${today}T23:59:59+02:00</expires>
            <area><areaDesc>Pianura piacentino-parmense</areaDesc></area>
          </info>
        </alert>
    """.trimIndent()

    @Test
    fun dayPageShowsTheAlertForTheDay() {
        val today = LocalDate.now()
        LocationStore(context).save(44.80, 10.33, automatic = false, name = "Parma")
        File(context.cacheDir, "meteoblue.json").writeText(WeatherTest.sampleJson(today))
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putLong("fetched", System.currentTimeMillis())
            .putLong("lat", 44.80.toRawBits())
            .putLong("lon", 10.33.toRawBits())
            .commit()
        File(context.cacheDir, "dpc_bulletin.xml").writeText(parmaCap(today))

        val activity = Robolectric.buildActivity(
            WeatherDayActivity::class.java,
            Intent(context, WeatherDayActivity::class.java).putExtra(WeatherDayActivity.EXTRA_DATE, today.toString()),
        ).setup().get()
        val strip = activity.findViewById<TextView>(R.id.day_alert)
        assertEquals(View.VISIBLE, strip.visibility)
        assertTrue(strip.text.contains(context.getString(R.string.alert_orange)))
        assertTrue(strip.text.contains(context.getString(R.string.risk_thunderstorms)))

        // Tomorrow has no alert: the strip goes away.
        activity.findViewById<android.widget.LinearLayout>(R.id.day_tabs).getChildAt(1).performClick()
        assertEquals(View.GONE, strip.visibility)
    }

    @Test
    fun mainPageShowsAColouredIconOnlyWhenThereIsAnAlert() {
        LocationStore(context).save(44.80, 10.33, automatic = false, name = "Parma")
        val today = LocalDate.now()
        File(context.cacheDir, "dpc_bulletin.xml").writeText(parmaCap(today))
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val icon = main.findViewById<android.widget.ImageView>(R.id.weather_alert)
        assertEquals(View.VISIBLE, icon.visibility)
        assertTrue(icon.contentDescription.contains(context.getString(R.string.alert_orange)))

        // A bulletin with nothing for Parma: no icon.
        File(context.cacheDir, "dpc_bulletin.xml").writeText(parmaCap(today).replace("Pianura piacentino-parmense", "Bacini di Roma"))
        val again = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(View.GONE, again.findViewById<android.widget.ImageView>(R.id.weather_alert).visibility)
    }
}
