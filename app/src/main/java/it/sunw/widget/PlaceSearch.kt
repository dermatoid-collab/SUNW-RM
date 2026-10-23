package it.sunw.widget

import android.app.Activity
import android.app.AlertDialog
import android.location.Address
import android.location.Geocoder
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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

    /** Asks for a name and searches it; [onPick] gets the place the user chose. */
    fun ask(activity: Activity, onPick: (Address) -> Unit) {
        val input = EditText(activity).apply {
            setHint(R.string.search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine()
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.search)
            .setView(input, pad, pad / 2, pad, 0)
            .setPositiveButton(R.string.search) { _, _ -> search(activity, input.text.toString(), onPick) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                dialog.dismiss()
                search(activity, input.text.toString(), onPick)
                true
            } else false
        }
        dialog.show()
    }

    /** Searches [query] off the main thread and hands the chosen place to [onPick]. */
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
            activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) show(activity, results, onPick) }
        }.start()
    }

    private fun show(activity: Activity, results: List<Address>?, onPick: (Address) -> Unit) {
        when {
            results == null -> Toast.makeText(activity, R.string.search_failed, Toast.LENGTH_LONG).show()
            results.isEmpty() -> Toast.makeText(activity, R.string.search_no_results, Toast.LENGTH_LONG).show()
            results.size == 1 -> onPick(results[0])
            else -> AlertDialog.Builder(activity)
                .setTitle(R.string.search_pick)
                .setItems(results.map { describe(it) }.toTypedArray()) { _, which -> onPick(results[which]) }
                .show()
        }
    }

    /** "Passo dello Stelvio, Bormio, Lombardia, Italia" — for the result list. */
    fun describe(address: Address): String =
        listOfNotNull(address.featureName, address.locality, address.subAdminArea, address.adminArea, address.countryName)
            .filter { it.isNotBlank() && it.toDoubleOrNull() == null } // featureName can be a house number
            .distinct()
            .joinToString(", ")

    fun shortName(address: Address): String =
        listOfNotNull(address.locality, address.featureName, address.subAdminArea, address.adminArea)
            .firstOrNull { it.isNotBlank() && it.toDoubleOrNull() == null }
            ?: describe(address)
}
