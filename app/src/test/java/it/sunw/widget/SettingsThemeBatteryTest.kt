package it.sunw.widget

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import it.sunw.widget.alerts.AlertNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/** The Settings page follows the chosen theme, and the battery question (first start and Settings). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsThemeBatteryTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun settings() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun allowUnrestricted(value: Boolean) =
        shadowOf(context.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, value)

    @Test
    fun pickingAThemeRecolorsTheSettingsPageAtOnce() {
        val activity = settings()
        val themes = activity.findViewById<LinearLayout>(R.id.themes)
        for (theme in listOf(Theme.SOLARIZED_LIGHT, Theme.NIGHT_OWL, Theme.DUNA)) {
            themes.getChildAt(Theme.values().indexOf(theme)).performClick()
            val palette = AppearanceStore(context).palette()
            assertEquals(theme, AppearanceStore(context).theme)
            assertEquals("$theme page", palette.pageBackground, (activity.window.decorView.background as ColorDrawable).color)
            assertEquals("$theme text", palette.text, activity.findViewById<TextView>(R.id.summary).currentTextColor)
            assertEquals("$theme button", palette.text, activity.findViewById<Button>(R.id.save).currentTextColor)
        }
    }

    @Test
    fun theAccentColoursTheSectionHeaders() {
        val activity = settings()
        val accents = activity.findViewById<LinearLayout>(R.id.accents)
        accents.getChildAt(Accent.values().indexOf(Accent.CORAL)).performClick()
        val header = activity.findViewById<View>(R.id.battery_status).let { (it.parent as LinearLayout).getChildAt((it.parent as LinearLayout).indexOfChild(it) - 1) } as TextView
        assertEquals(AppearanceStore(context).palette().accent, header.currentTextColor)
    }

    @Test
    fun settingsShowsTheBatteryStateAndOffersTheFixOnlyWhileLimited() {
        allowUnrestricted(false)
        val limited = settings()
        assertEquals(limited.getString(R.string.battery_status_limited), limited.findViewById<TextView>(R.id.battery_status).text.toString())
        assertEquals(View.VISIBLE, limited.findViewById<View>(R.id.battery_fix).visibility)
        limited.findViewById<View>(R.id.battery_fix).performClick()
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, shadowOf(limited).nextStartedActivity.action)

        allowUnrestricted(true)
        val free = settings()
        assertEquals(free.getString(R.string.battery_status_ok), free.findViewById<TextView>(R.id.battery_status).text.toString())
        assertEquals(View.GONE, free.findViewById<View>(R.id.battery_fix).visibility)
    }

    @Test
    fun firstStartAsksOnceAboutTheBatteryAndSendsToTheSystemChoice() {
        AlertNotifier.setEnabled(context, false) // no notification prompt first
        allowUnrestricted(false)
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertEquals(main.getString(R.string.battery_dialog_open), dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle() // a dialog button hands its click to the looper
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, shadowOf(main).nextStartedActivity.action)

        // The second start does not ask again.
        ShadowAlertDialog.reset()
        Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test
    fun noQuestionWhenTheAppIsAlreadyUnrestricted() {
        AlertNotifier.setEnabled(context, false)
        allowUnrestricted(true)
        Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test
    fun theBatteryChoiceHasAFallbackToTheAppSettings() {
        val intents = BatteryGuard.intents(context)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intents[0].action)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intents[1].action)
        assertTrue(intents.all { it.data.toString() == "package:${context.packageName}" })
    }
}
