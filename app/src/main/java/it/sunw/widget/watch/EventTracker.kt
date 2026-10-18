package it.sunw.widget.watch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * Remembers each weather event between updates, so a forecast that wobbles around a threshold does not switch the
 * event on and off. Every event has two thresholds ([EventType.isOn] to start, [EventType.isOff] to end); between them
 * its state is kept, and it ends only after [OFF_CONFIRMATIONS] OFF updates that are really different updates.
 *
 * - An event is an *episode*: a run of hours over the ON threshold. The episodes of a new update are matched to the
 *   stored ones by overlap in absolute time (±3 h), never by "hours from now", so the horizon sliding is not read
 *   as an easing.
 * - It is followed from the moment it reaches ON (up to two days ahead) and notified once it falls within its lead
 *   time ([EventType.leadMinutes]) and is still ACTIVE.
 * - ACTIVE → RESOLVED after the OFF confirmations (silent); ACTIVE → EXPIRED once its window is over (silent).
 * - A higher level notifies at once; a new event of the same type soon after a notification is not notified again
 *   unless it is stronger.
 * - An update with missing data in the event's window changes nothing and does not count as a confirmation.
 * One state per place and per event; the state is kept in a file.
 */
class EventTracker private constructor(private val file: File?, private val placeKey: String) {

    enum class State { ACTIVE, RESOLVED, EXPIRED }

    class Event(
        val id: String,
        val type: EventType,
        var state: State,
        var start: LocalDateTime,
        var end: LocalDateTime,
        var level: Int,
        var peak: Double,
        var notifiedLevel: Int = 0,
        var notifiedAt: Long = 0,
        var offCount: Int = 0,
        var lastOffAt: Long = 0,
        var lastOffFingerprint: String = "",
        var updatedAt: Long = 0,
    )

    class Alert(val event: Event, val escalation: Boolean)

    private val events = ArrayList<Event>()

    /** Per type: when and at which level it was last notified (the dedup memory). */
    private val lastNotified = HashMap<EventType, Pair<Long, Int>>()

    private var lastValidUpdate = 0L

    val all: List<Event> get() = events.toList()

    fun active(type: EventType): List<Event> = events.filter { it.type == type && it.state == State.ACTIVE }

    /** Applies a new forecast; returns what to notify now. [enabled] are the event types the user wants. */
    fun update(data: WatchData, now: Instant, enabled: Set<EventType>): List<Alert> {
        val nowMs = now.toEpochMilli()
        val local = data.localNow(now)
        val alerts = ArrayList<Alert>()
        for (type in EventType.values()) {
            if (type !in enabled) {
                events.removeAll { it.type == type }
                continue
            }
            val series = type.series(data)
            val leftover = type.episodes(series.filter { it.time.plusMinutes(it.minutes).isAfter(local) }).toMutableList()
            for (e in active(type)) {
                if (local.isAfter(e.end.plusHours(1))) { // the window is over: expired, which is not the same as confirmed over
                    e.state = State.EXPIRED
                    e.updatedAt = nowMs
                    continue
                }
                val matches = leftover.filter { !it.start.isAfter(e.end.plusHours(3)) && !it.end.isBefore(e.start.minusHours(3)) }
                if (matches.isNotEmpty()) {
                    leftover.removeAll(matches)
                    e.start = matches.minOf { it.start }
                    e.end = matches.maxOf { it.end }
                    e.level = maxOf(e.level, matches.maxOf { it.level })
                    e.peak = matches.map { it.peak }.fold(e.peak) { a, b -> type.stronger(a, b) }
                    if (RESET_OFF_COUNTER_ON_RECOVERY) e.offCount = 0
                    e.updatedAt = nowMs
                    continue
                }
                val window = series.filter { it.time.plusMinutes(it.minutes).isAfter(e.start) && it.time.isBefore(e.end) }
                if (window.isEmpty() || window.any { it.value == null }) continue // not enough data: nothing changes
                if (window.all { type.isOff(it) }) confirmOff(e, window, nowMs) else e.offCount = 0
            }
            for (episode in leftover) {
                events += Event(
                    id = "${type.key}|${episode.start}", type = type, state = State.ACTIVE, start = episode.start, end = episode.end,
                    level = episode.level, peak = episode.peak, updatedAt = nowMs,
                )
            }
        }

        for (e in events.filter { it.state == State.ACTIVE }) {
            val inWindow = !e.start.isAfter(local.plusMinutes(e.type.leadMinutes)) && e.end.isAfter(local)
            if (e.notifiedLevel == 0 && inWindow) {
                val last = lastNotified[e.type]
                val repeated = last != null && nowMs - last.first < DEDUP_MS && e.level <= last.second
                val superseded = e.type == EventType.RAIN_SOON && active(EventType.RAIN_HEAVY).any { !it.start.isAfter(e.end) && !it.end.isBefore(e.start) }
                e.notifiedLevel = e.level
                e.notifiedAt = nowMs
                if (!repeated && !superseded) {
                    alerts += Alert(e, escalation = false)
                    lastNotified[e.type] = nowMs to e.level
                }
            } else if (ALLOW_IMMEDIATE_ESCALATION && e.notifiedLevel in 1 until e.level) {
                e.notifiedLevel = e.level
                e.notifiedAt = nowMs
                alerts += Alert(e, escalation = true)
                lastNotified[e.type] = nowMs to e.level
            }
        }
        events.removeAll { it.state != State.ACTIVE && nowMs - it.updatedAt > KEEP_ENDED_MS }
        lastValidUpdate = nowMs
        save()
        return alerts
    }

    /** One more OFF update, only when it is a different update from the last counted one (see the class comment). */
    private fun confirmOff(e: Event, window: List<Point>, nowMs: Long) {
        val fingerprint = window.joinToString(",") { String.format(java.util.Locale.ROOT, "%.2f", it.value) }
        val since = nowMs - e.lastOffAt
        val first = e.offCount == 0
        val distinct = first || (since >= MIN_INTERVAL_MS && (fingerprint != e.lastOffFingerprint || since >= SAME_DATA_MS))
        if (distinct) {
            e.offCount++
            e.lastOffAt = nowMs
            e.lastOffFingerprint = fingerprint
        }
        if (e.offCount >= (if (HYSTERESIS_ENABLED) OFF_CONFIRMATIONS else 1)) {
            e.state = State.RESOLVED
            e.updatedAt = nowMs
        }
    }

    private fun save() {
        val target = file ?: return
        val json = JSONObject().put("place", placeKey).put("lastValidUpdate", lastValidUpdate)
        json.put("events", JSONArray().apply {
            for (e in events) put(JSONObject()
                .put("id", e.id).put("type", e.type.key).put("state", e.state.name)
                .put("start", e.start.toString()).put("end", e.end.toString())
                .put("level", e.level).put("peak", e.peak).put("notifiedLevel", e.notifiedLevel).put("notifiedAt", e.notifiedAt)
                .put("offCount", e.offCount).put("lastOffAt", e.lastOffAt).put("lastOffFingerprint", e.lastOffFingerprint)
                .put("updatedAt", e.updatedAt).put("thresholds", e.type.thresholds)) // the thresholds used, for the record
        })
        json.put("lastNotified", JSONObject().apply {
            for ((type, v) in lastNotified) put(type.key, JSONArray().put(v.first).put(v.second))
        })
        runCatching { target.writeText(json.toString()) }
    }

    private fun load() {
        val source = file?.takeIf { it.exists() } ?: return
        runCatching {
            val json = JSONObject(source.readText())
            if (json.optString("place") != placeKey) return // another place: its events are not this one's
            lastValidUpdate = json.optLong("lastValidUpdate")
            val list = json.optJSONArray("events") ?: JSONArray()
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val type = EventType.fromKey(o.getString("type")) ?: continue
                events += Event(
                    id = o.getString("id"), type = type, state = State.valueOf(o.getString("state")),
                    start = LocalDateTime.parse(o.getString("start")), end = LocalDateTime.parse(o.getString("end")),
                    level = o.getInt("level"), peak = o.getDouble("peak"), notifiedLevel = o.optInt("notifiedLevel"),
                    notifiedAt = o.optLong("notifiedAt"), offCount = o.optInt("offCount"), lastOffAt = o.optLong("lastOffAt"),
                    lastOffFingerprint = o.optString("lastOffFingerprint"), updatedAt = o.optLong("updatedAt"),
                )
            }
            val last = json.optJSONObject("lastNotified")
            last?.keys()?.forEach { key ->
                val type = EventType.fromKey(key) ?: return@forEach
                val pair = last.getJSONArray(key)
                lastNotified[type] = pair.getLong(0) to pair.getInt(1)
            }
        }
    }

    companion object {
        // The configurable parameters of the design (kept as constants until a screen needs them).
        const val HYSTERESIS_ENABLED = true
        const val OFF_CONFIRMATIONS = 2
        const val OFF_CONFIRMATION_MIN_INTERVAL_MINUTES = 15L
        const val RESET_OFF_COUNTER_ON_RECOVERY = true
        const val ALLOW_IMMEDIATE_ESCALATION = true

        private val MIN_INTERVAL_MS = Duration.ofMinutes(OFF_CONFIRMATION_MIN_INTERVAL_MINUTES).toMillis()
        private val SAME_DATA_MS = Duration.ofMinutes(60).toMillis()
        private val DEDUP_MS = Duration.ofHours(6).toMillis()
        private val KEEP_ENDED_MS = Duration.ofHours(12).toMillis()

        /** The tracker saved for the place whose key is [placeKey] (empty for another place). */
        fun load(context: Context, placeKey: String): EventTracker =
            EventTracker(File(context.filesDir, "weather_watch.json"), placeKey).also { it.load() }

        /** A tracker that keeps nothing on disk, for tests. */
        fun inMemory(placeKey: String = "test"): EventTracker = EventTracker(null, placeKey)

        fun placeKey(latitude: Double, longitude: Double): String = String.format(java.util.Locale.ROOT, "%.2f,%.2f", latitude, longitude)
    }
}
