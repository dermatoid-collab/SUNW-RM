package it.sunw.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery saving can hold back the widget's sunrise and sunset updates (and the hourly alert check).
 * Detects whether the app is limited and sends the user to the system's "unrestricted" choice.
 * Samsung's "sleeping apps" list can't be read, so it is only named in the texts.
 */
object BatteryGuard {

    /** True when the app is under battery optimisation or restricted in the background. */
    fun isRestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        val optimised = power?.isIgnoringBatteryOptimizations(context.packageName) == false
        val restricted = Build.VERSION.SDK_INT >= 28 &&
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        return optimised || restricted
    }

    /** The system's "allow unrestricted" request for this app; the app's settings page if it is not available. */
    @SuppressLint("BatteryLife")
    fun intents(context: Context): List<Intent> {
        val pkg = Uri.parse("package:${context.packageName}")
        return listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
        )
    }

    /** Starts the first of [intents] the device can handle; false when none can. */
    fun open(activity: Activity): Boolean {
        for (intent in intents(activity)) {
            try {
                activity.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // try the next one
            }
        }
        return false
    }

    private fun prefs(context: Context) = context.getSharedPreferences("battery", Context.MODE_PRIVATE)

    fun wasAsked(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) = prefs(context).edit().putBoolean(KEY_ASKED, true).apply()

    private const val KEY_ASKED = "asked"
}
