package it.sunw.widget

import android.app.Activity
import android.app.AlertDialog
import android.location.Address
import android.location.Geocoder
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.util.Locale

/**
 * Place search by name with the system Geocoder, shared by Settings and the place menu of the main page: the name is
 * asked for in a dialog, the places found are listed under "Choose a place" (one result is taken at once) and the
 * caller gets the one picked.
 */
internal object PlaceSearch {
    private const val MAX_RESULTS = 6

    /**
     * Asks for a name and searches it; [onPick] gets the place the user chose. Under the input the last places picked
     * from a search are listed ("Recents", scrolling): tapping one picks it without searching again.
     */
    fun ask(activity: Activity, onPick: (Address) -> Unit) {

        val input = EditText(activity).apply {
            setHint(R.string.search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine()
        }
        val density = activity.resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        body.addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val recents = RecentPlaces(activity).all()
        var dialog: AlertDialog? = null
        if (recents.isNotEmpty()) {
            body.addView(TextView(activity).apply {
                setText(R.string.recents)
                textSize = 12f
                alpha = 0.7f
                setPadding(0, (14 * density).toInt(), 0, (2 * density).toInt())
            })
            // At most ~4.5 rows high, so the list scrolls instead of growing the dialog.
            val rowDp = 48
            val list = ListView(activity).apply {
                adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, recents.map { it.name })
                setOnItemClickListener { _, _, position, _ ->
                    dialog?.dismiss()
                    val recent = recents[position]
                    pickAndRemember(activity, Address(Locale.getDefault()).apply {
                        locality = recent.name
                        latitude = recent.latitude
                        longitude = recent.longitude
                    }, onPick)
                }
            }
            body.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, (minOf(recents.size * rowDp, 216) * density).toInt()))
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.search)
            .setView(body, pad, pad / 2, pad, 0)
            .setPositiveButton(R.string.search) { _, _ -> search(activity, input.text.toString(), onPick) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                dialog?.dismiss()
                search(activity, input.text.toString(), onPick)
                true
            } else false
        }
        dialog?.show()
    }

    /** Remembers [address] among the recents (first) and hands it to [onPick]. */
    private fun pickAndRemember(activity: Activity, address: Address, onPick: (Address) -> Unit) {
        RecentPlaces(activity).add(Favorite(shortName(address), address.latitude, address.longitude))
        onPick(address)
    }

    /** Searches [query] off the main thread and hands the chosen place to [onPick] (and remembers it among the recents). */
    fun search(activity: Activity, query: String, onPick: (Address) -> Unit) {
        val text = query.trim()
        if (text.isEmpty()) return
        if (!Geocoder.isPresent()) {
            Toast.makeText(activity, R.string.search_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        // Geocoder blocks on the network.
        Thread {
            val results = try {
                @Suppress("DEPRECATION") // the async overload needs API 33
                Geocoder(activity, Locale.getDefault()).getFromLocationName(text, MAX_RESULTS).orEmpty()
                    .filter { it.hasLatitude() && it.hasLongitude() }
            } catch (e: IOException) {
                null
            }
            activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) show(activity, text, results, onPick) }
        }.start()
    }

    private fun show(activity: Activity, query: String, results: List<Address>?, onPick: (Address) -> Unit) {
        when {
            results == null -> Toast.makeText(activity, R.string.search_failed, Toast.LENGTH_LONG).show()
            results.isEmpty() -> Toast.makeText(activity, R.string.search_no_results, Toast.LENGTH_LONG).show()
            // One result that is what was asked for is taken at once; anything else is offered to choose (and confirm).
            results.size == 1 && matches(query, results[0]) -> pickAndRemember(activity, results[0], onPick)
            else -> AlertDialog.Builder(activity)
                .setTitle(R.string.search_pick)
                .setItems(results.map { describe(it) }.toTypedArray()) { _, which -> pickAndRemember(activity, results[which], onPick) }
                .show()
        }
    }

    /** "Passo dello Stelvio, Bormio, Lombardia, Italia" — for the result list. */
    fun describe(address: Address): String =
        listOfNotNull(address.featureName, address.locality, address.subAdminArea, address.adminArea, address.countryName)
            .filter { it.isNotBlank() && it.toDoubleOrNull() == null } // featureName can be a house number
            .distinct()
            .joinToString(", ")

    /**
     * The name a found place gets: the place itself ("Prato Spilla") and not the municipality it lies in
     * ("Monchio delle Corti"); for a street address (the feature is a number or the street) the locality, then the area.
     * The coordinates are always the found point's own, whatever the name.
     */
    fun shortName(address: Address): String {
        fun usable(name: String?) = name?.takeIf { it.isNotBlank() && it.toDoubleOrNull() == null }
        return usable(address.featureName)?.takeIf { it != address.thoroughfare }
            ?: listOfNotNull(address.locality, address.subAdminArea, address.adminArea).firstNotNullOfOrNull { usable(it) }
            ?: describe(address)
    }

    /** Whether a found place is what [query] names: the name matches in either direction, ignoring case and accents. */
    fun matches(query: String, address: Address): Boolean {
        val wanted = plain(query)
        if (wanted.isEmpty()) return true
        return listOfNotNull(address.featureName, address.locality, address.subAdminArea, address.adminArea)
            .map { plain(it) }.any { it.isNotEmpty() && (it.contains(wanted) || wanted.contains(it)) }
    }

    private fun plain(text: String): String =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
}
