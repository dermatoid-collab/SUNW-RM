package it.sunw.widget.webcams

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import it.sunw.widget.BuildConfig
import it.sunw.widget.Place
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A webcam from the Windy Webcams service (free plan: low-resolution images that expire soon). */
data class Webcam(
    val id: Long,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    /** Current image; the address expires after a few minutes, so it is fetched again on every visit. */
    val imageUrl: String?,
    /** The webcam's page on windy.com. */
    val pageUrl: String?,
    val distanceKm: Double,
)

/** Windy Webcams API v3: webcams around a place, and the current image of one webcam. */
class WindyWebcams(private val key: String = BuildConfig.WINDY_WEBCAMS_API_KEY) {

    val hasKey: Boolean get() = key.isNotBlank()

    /** Nearest first. Reports on [callback] (any thread). */
    fun nearby(place: Place, callback: (Result<List<Webcam>>) -> Unit) {
        cache[place.latitude to place.longitude]?.takeIf { System.currentTimeMillis() - it.first < MEMORY_MS }?.let {
            callback(Result.success(it.second))
            return
        }
        executor.execute {
            callback(runCatching {
                if (!hasKey) throw IOException("no key")
                val list = parse(get(nearbyUrl(place.latitude, place.longitude)), place.latitude, place.longitude)
                cache[place.latitude to place.longitude] = System.currentTimeMillis() to list
                list
            })
        }
    }

    /** The up-to-date image address of one webcam, or null. */
    fun currentImage(id: Long, callback: (Result<String?>) -> Unit) {
        executor.execute {
            callback(runCatching {
                if (!hasKey) throw IOException("no key")
                val json = JSONObject(get("$BASE/$id?lang=en&include=images"))
                imageOf(json)
            })
        }
    }

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("x-windy-api-key", key)
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            return body
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val BASE = "https://api.windy.com/webcams/api/v3/webcams"
        const val RADIUS_KM = 60
        private const val LIMIT = 24
        private const val MEMORY_MS = 5 * 60_000L
        private val executor = Executors.newFixedThreadPool(2)
        private val cache = HashMap<Pair<Double, Double>, Pair<Long, List<Webcam>>>()

        fun nearbyUrl(latitude: Double, longitude: Double): String = String.format(
            Locale.ROOT, "%s?nearby=%.4f,%.4f,%d&limit=%d&lang=en&include=images,location,urls",
            BASE, latitude, longitude, RADIUS_KM, LIMIT,
        )

        private fun imageOf(webcam: JSONObject): String? {
            val current = webcam.optJSONObject("images")?.optJSONObject("current") ?: return null
            return listOf("preview", "thumbnail", "icon").firstNotNullOfOrNull { current.optString(it).takeIf(String::isNotEmpty) }
        }

        /** Parses the list response, nearest webcam first. */
        fun parse(json: String, latitude: Double, longitude: Double): List<Webcam> {
            val list = JSONObject(json).optJSONArray("webcams") ?: return emptyList()
            return (0 until list.length()).mapNotNull { i ->
                val w = list.optJSONObject(i) ?: return@mapNotNull null
                val loc = w.optJSONObject("location")
                val lat = loc?.optDouble("latitude", Double.NaN) ?: Double.NaN
                val lon = loc?.optDouble("longitude", Double.NaN) ?: Double.NaN
                Webcam(
                    id = w.optLong("webcamId"),
                    title = w.optString("title").ifBlank { loc?.optString("city").orEmpty() },
                    latitude = lat, longitude = lon,
                    imageUrl = imageOf(w),
                    pageUrl = w.optJSONObject("urls")?.optString("detail")?.takeIf { it.isNotEmpty() },
                    distanceKm = if (lat.isNaN() || lon.isNaN()) Double.MAX_VALUE else distanceKm(latitude, longitude, lat, lon),
                )
            }.filter { it.id != 0L && it.imageUrl != null }.sortedBy { it.distanceKm }
        }

        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * 6371.0 * asin(sqrt(a))
        }
    }
}

/** Downloads images on background threads and delivers them on the main thread. */
object ImageLoader {
    private val executor = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String, maxWidth: Int = 1280, onDone: (Bitmap?) -> Unit) {
        executor.execute {
            val bitmap = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                try {
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()?.let { if (it.width > maxWidth) Bitmap.createScaledBitmap(it, maxWidth, it.height * maxWidth / it.width, true) else it }
            main.post { onDone(bitmap) }
        }
    }
}
