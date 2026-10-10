package it.sunw.widget.webcams

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** One of the user's own webcams: a web page or live player, shown in the app or in the browser. */
data class OwnWebcam(val name: String, val url: String) {
    val host: String get() = Uri.parse(url).host?.removePrefix("www.").orEmpty()

    /** Short kind of page, from its address. */
    val kind: String get() = when {
        host.endsWith("twitch.tv") -> "Twitch"
        host.endsWith("feratel.com") -> "Feratel"
        else -> "Web"
    }
}

/** The user's webcam list, kept on the phone and saved with the settings backup. */
class OwnWebcamStore(context: Context) {

    private val prefs = context.getSharedPreferences("webcams", Context.MODE_PRIVATE)

    fun all(): List<OwnWebcam> {
        if (!prefs.contains(KEY)) return DEFAULTS
        val json = JSONArray(prefs.getString(KEY, "[]"))
        return (0 until json.length()).map { i -> json.getJSONObject(i).let { OwnWebcam(it.getString("name"), it.getString("url")) } }
    }

    fun add(webcam: OwnWebcam) = write(all().filterNot { it.url == webcam.url } + webcam)

    fun remove(webcam: OwnWebcam) = write(all() - webcam)

    fun replace(old: OwnWebcam, new: OwnWebcam) = write(all().map { if (it == old) new else it })

    fun toJson(): JSONArray = toArray(all())

    /** Replaces the list with the one in a backup. */
    fun restore(json: JSONArray) = write((0 until json.length()).map { i ->
        json.getJSONObject(i).let { OwnWebcam(it.getString("name"), it.getString("url")) }
    })

    private fun write(list: List<OwnWebcam>) {
        prefs.edit().putString(KEY, toArray(list).toString()).apply()
    }

    private fun toArray(list: List<OwnWebcam>) = JSONArray().also { a -> list.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url)) } }

    companion object {
        private const val KEY = "list"

        /** The list before the user changes it. */
        val DEFAULTS = listOf(
            OwnWebcam("Delta e Parapendio", "https://www.deltaeparapendio.it/utilities/webcam2.php"),
            OwnWebcam("Rifugio Fauniera", "https://player.twitch.tv/?channel=rifugiofauniera&enableExtensions=true&muted=false&parent=twitch.tv&player=popout&quality=auto&volume=1"),
            OwnWebcam("Feratel 5571", "https://webtv.feratel.com/webtv/?cam=5571&design=v3&c0=0&c1=0&c2=0&lg=de&pg=5EB12424-7C2D-428A-BEFF-0C9140CD772F&s=0"),
            OwnWebcam("Alta Badia live", "https://www.altabadia.org/it/webcam-alta-badia-live"),
        )

        /** [text] as a web address (https:// added when missing), or null when it cannot be one. */
        fun normalizeUrl(text: String): String? {
            val t = text.trim()
            if (t.isEmpty() || t.any { it.isWhitespace() }) return null
            val full = if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
            val uri = Uri.parse(full)
            return full.takeIf { !uri.host.isNullOrEmpty() && uri.host!!.contains('.') }
        }
    }
}
