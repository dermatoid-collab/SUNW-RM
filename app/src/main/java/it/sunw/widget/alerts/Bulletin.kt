package it.sunw.widget.alerts

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * The national alert bulletin of the Italian Civil Protection (Bollettino di criticità
 * nazionale/allerta), read from its CAP 1.2 file: for today and tomorrow, the yellow / orange /
 * red alerts for hydrogeological, thunderstorm and hydraulic risk on each of the alert zones.
 */
data class Bulletin(
    val sent: OffsetDateTime?,
    /** "Actual", or "Update"/"Error" for a correction issued after the 16:00 bulletin. */
    val msgType: String,
    val note: String,
    val warnings: List<Warning>,
) {
    enum class Level { YELLOW, ORANGE, RED }

    enum class Risk { HYDROGEOLOGICAL, THUNDERSTORMS, HYDRAULIC }

    data class Warning(
        val zone: String,
        val date: LocalDate,
        val risk: Risk,
        val level: Level,
        val onset: OffsetDateTime?,
        val expires: OffsetDateTime?,
    )

    /** Warnings for [zone] on [date], highest level first. */
    fun forZone(zone: String, date: LocalDate): List<Warning> =
        warnings.filter { it.zone == zone && it.date == date }
            .sortedWith(compareByDescending<Warning> { it.level }.thenBy { it.risk })

    /** Highest level for [zone] on [date], or null when there is no alert. */
    fun levelFor(zone: String, date: LocalDate): Level? = forZone(zone, date).firstOrNull()?.level

    companion object {
        fun parse(xml: String): Bulletin {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))
            var sent: OffsetDateTime? = null
            var msgType = ""
            var note = ""
            val warnings = mutableListOf<Warning>()
            // Inside an <info>: its event, times and areas.
            var event = ""
            var onset: OffsetDateTime? = null
            var expires: OffsetDateTime? = null
            val areas = mutableListOf<String>()
            var inInfo = false
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "info" -> {
                            inInfo = true; event = ""; onset = null; expires = null; areas.clear()
                        }
                        "sent" -> if (!inInfo) sent = time(parser.nextText())
                        "msgType" -> msgType = parser.nextText().trim()
                        "note" -> if (!inInfo) note = parser.nextText().trim()
                        "event" -> event = parser.nextText()
                        "onset" -> onset = time(parser.nextText())
                        "expires" -> expires = time(parser.nextText())
                        "areaDesc" -> areas += parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "info") {
                        inInfo = false
                        val level = level(event)
                        val risk = risk(event)
                        val date = onset?.toLocalDate()
                        if (level != null && risk != null && date != null) {
                            areas.distinct().forEach { warnings += Warning(it, date, risk, level, onset, expires) }
                        }
                    }
                }
            }
            return Bulletin(sent, msgType, note, warnings)
        }

        private fun time(s: String) = runCatching { OffsetDateTime.parse(s.trim()) }.getOrNull()

        /** "MODERATA CRITICITA' PER RISCHIO IDRAULICO / ALLERTA ARANCIONE:" → ORANGE */
        internal fun level(event: String): Level? = event.uppercase().let {
            when {
                "ROSSA" in it || "ELEVATA" in it -> Level.RED
                "ARANCIONE" in it || "MODERATA" in it -> Level.ORANGE
                "GIALLA" in it || "ORDINARIA" in it -> Level.YELLOW
                else -> null
            }
        }

        internal fun risk(event: String): Risk? = event.uppercase().let {
            when {
                "TEMPORALI" in it -> Risk.THUNDERSTORMS
                "IDRAULIC" in it -> Risk.HYDRAULIC
                "IDROGEOLOGIC" in it -> Risk.HYDROGEOLOGICAL
                else -> null
            }
        }
    }
}
