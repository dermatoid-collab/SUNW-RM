package it.sunw.widget.alerts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/**
 * Downloads the latest national alert bulletin from the Civil Protection's open-data repository
 * on GitHub (CC BY 4.0) and keeps its CAP file on disk. The bulletin comes out once a day
 * (normally by 16:00, sometimes corrected later), so the network is checked at most hourly.
 */
class AlertRepository(context: Context) {

    private val cacheFile = File(context.cacheDir, "dpc_bulletin.xml")
    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    fun cached(): Bulletin? {
        if (!cacheFile.exists()) return null
        return runCatching { Bulletin.parse(cacheFile.readText()) }.getOrNull()
    }

    fun isFresh(now: Instant = Instant.now()) =
        Duration.between(Instant.ofEpochMilli(prefs.getLong(KEY_CHECKED, 0)), now) < MAX_AGE

    /** Fetches the newest bulletin on a background thread (skipping it if already cached). */
    fun refresh(callback: (Result<Bulletin>) -> Unit) {
        // Unit tests (Robolectric) never reach the network.
        if (android.os.Build.FINGERPRINT == "robolectric") {
            callback(Result.failure(IOException("offline in tests")))
            return
        }
        executor.execute {
            callback(runCatching {
                val name = latestZipName()
                val known = prefs.getString(KEY_NAME, null)
                val bulletin = if (name == known && cacheFile.exists()) {
                    Bulletin.parse(cacheFile.readText())
                } else {
                    val xml = capFromZip(get("$RAW/$name"))
                    Bulletin.parse(xml).also { cacheFile.writeText(xml) } // validate before caching
                }
                prefs.edit().putString(KEY_NAME, name).putLong(KEY_CHECKED, System.currentTimeMillis()).apply()
                bulletin
            })
        }
    }

    /**
     * Name of the newest files/xml/<date>_<time>.zip: the last commit touching that folder,
     * then the files it changed (GitHub API, no key needed for this light use).
     */
    private fun latestZipName(): String {
        val commits = JSONArray(String(get("$API/commits?path=files/xml&per_page=1")))
        val sha = commits.getJSONObject(0).getString("sha")
        val files = JSONObject(String(get("$API/commits/$sha"))).getJSONArray("files")
        return (0 until files.length()).map { files.getJSONObject(it).getString("filename") }
            .filter { ZIP_NAME.matches(it) }
            .maxOrNull() ?: throw IOException("no bulletin in commit $sha")
    }

    private fun get(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "SunW-Android")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val REPO = "pcm-dpc/DPC-Bollettini-Criticita-Idrogeologica-Idraulica"
        private const val API = "https://api.github.com/repos/$REPO"
        private const val RAW = "https://raw.githubusercontent.com/$REPO/master"
        private val ZIP_NAME = Regex("files/xml/\\d{8}_\\d{4}\\.zip")
        val MAX_AGE: Duration = Duration.ofMinutes(60)
        private const val KEY_NAME = "name"
        private const val KEY_CHECKED = "checked"
        private val executor = Executors.newSingleThreadExecutor()

        /** The CAP file (Cap_<date>_<time>.xml) inside a bulletin zip. */
        fun capFromZip(bytes: ByteArray): String {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.startsWith("Cap_") && entry.name.endsWith(".xml")) return String(zip.readBytes(), Charsets.UTF_8)
                }
            }
            throw IOException("no CAP file in the bulletin")
        }
    }
}
