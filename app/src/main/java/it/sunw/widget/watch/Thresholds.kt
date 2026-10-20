package it.sunw.widget.watch

import android.content.Context
import it.sunw.widget.R

/**
 * One adjustable number of a weather event (a threshold, a lead time…): its key, label, unit, the starting value
 * and the range the user may set it in.
 */
class Param(val key: String, val label: Int, val unit: String, val default: Double, val min: Double, val max: Double, val decimals: Int = 0)

/**
 * The numbers the weather watch judges with: every event's thresholds and lead time and the general hysteresis
 * parameters. What the user has not changed is the starting value of each [Param], so a default [Thresholds]
 * behaves as the watch always did. Kept in preferences by [load] / [save].
 */
class Thresholds(private val values: Map<String, Double> = emptyMap()) {

    /** The value of [key] for [type]. */
    fun value(type: EventType, key: String): Double = values[name(type.key, key)] ?: type.param(key).default

    /** The lead time of [type], in minutes. */
    fun lead(type: EventType): Long = value(type, "lead").toLong()

    private fun general(key: String): Double = values[name(GLOBAL, key)] ?: GENERAL.first { it.key == key }.default

    /** OFF updates, really different ones, an event needs to end. */
    val offConfirmations: Int get() = general("confirmations").toInt()

    /** Least time between two counted OFF updates. */
    val minIntervalMs: Long get() = general("interval").toLong() * 60_000L

    /** A new event of the same type within this is not notified again unless it is stronger. */
    val dedupMs: Long get() = (general("dedup") * 3_600_000.0).toLong()

    /** Every value of the general parameters and of [type], as set now. */
    fun valuesOf(type: EventType): Map<String, Double> = type.params.associate { it.key to value(type, it.key) }

    fun generalValues(): Map<String, Double> = GENERAL.associate { it.key to general(it.key) }

    companion object {
        const val GLOBAL = "global"
        private const val PREFS = "weather_watch_thresholds"

        /** The general parameters: how the watch confirms the end of an event and avoids repeating itself. */
        val GENERAL: List<Param> = listOf(
            Param("confirmations", R.string.pr_conf, "", 2.0, 1.0, 5.0),
            Param("interval", R.string.pr_interval, "min", 15.0, 5.0, 120.0),
            Param("dedup", R.string.pr_dedup, "h", 6.0, 1.0, 24.0),
        )

        val DEFAULTS = Thresholds()

        private fun name(owner: String, key: String) = "$owner.$key"

        fun load(context: Context): Thresholds {
            val all = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            // Kept as text: a float would turn 0.2 into 0.20000000298 and a threshold equal to it would no longer be reached.
            return Thresholds(all.mapNotNull { (k, v) -> (v as? String)?.toDoubleOrNull()?.let { k to it } }.toMap())
        }

        /** Stores [values] for [type]; a value equal to the starting one is not stored (the default may change one day). */
        fun save(context: Context, type: EventType, values: Map<String, Double>) {
            val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            for (p in type.params) {
                val v = values[p.key] ?: continue
                if (v == p.default) edit.remove(name(type.key, p.key)) else edit.putString(name(type.key, p.key), v.toString())
            }
            edit.apply()
        }

        fun saveGeneral(context: Context, values: Map<String, Double>) {
            val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            for (p in GENERAL) {
                val v = values[p.key] ?: continue
                if (v == p.default) edit.remove(name(GLOBAL, p.key)) else edit.putString(name(GLOBAL, p.key), v.toString())
            }
            edit.apply()
        }

        /** Back to the starting values: of [type], or of everything when it is null. */
        fun reset(context: Context, type: EventType?) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            for (k in prefs.all.keys) if (type == null || k.startsWith(type.key + ".")) edit.remove(k)
            edit.apply()
        }
    }
}
