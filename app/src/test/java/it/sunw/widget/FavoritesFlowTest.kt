package it.sunw.widget

import android.app.AlertDialog
import android.content.Context
import android.location.Address
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPopupMenu
import java.util.Locale

/** Drives the real activities: add, select and remove favourites, and the quick switch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoritesFlowTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clean() {
        listOf("place", "favorites", "appearance").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun settings() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun favoriteRows(activity: SettingsActivity): List<View> {
        val list = activity.findViewById<LinearLayout>(R.id.favorites)
        return (0 until list.childCount).map { list.getChildAt(it) }.filter { it is LinearLayout }
    }

    private fun addViaDialog(activity: SettingsActivity, name: String?) {
        activity.findViewById<TextView>(R.id.add_favorite).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("add-favourite dialog not shown", dialog)
        if (name != null) dialog.findViewById<EditText>(R.id.favorite_name).setText(name)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun addFavoriteFromManualPlace() {
        LocationStore(context).save(46.5287, 10.4527, automatic = false, name = "Passo dello Stelvio")
        val activity = settings()
        addViaDialog(activity, null) // keeps the prefilled name

        val favorites = FavoritesStore(context).all()
        assertEquals(listOf(Favorite("Passo dello Stelvio", 46.5287, 10.4527)), favorites)
        assertEquals(1, favoriteRows(activity).size)
    }

    @Test
    fun addFavoriteWithEmptyNameInAutomaticMode() {
        // Default: device location, no fix in tests → Parma fallback, empty name field.
        val activity = settings()
        addViaDialog(activity, "")

        val favorites = FavoritesStore(context).all()
        assertEquals(1, favorites.size)
        assertEquals("44.8015, 10.3279", favorites[0].name)
    }

    @Test
    fun selectAndRemoveFavorite() {
        FavoritesStore(context).add(Favorite("Livigno", 46.5386, 10.1357))
        FavoritesStore(context).add(Favorite("Passo Gavia", 46.3437, 10.4876))
        val activity = settings()
        assertEquals(2, favoriteRows(activity).size)

        favoriteRows(activity)[0].performLongClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(listOf("Passo Gavia"), FavoritesStore(context).all().map { it.name })
        assertEquals(1, favoriteRows(activity).size)

        // Tapping a favourite uses it and returns to the main page.
        favoriteRows(activity)[0].performClick()
        val place = LocationStore(context).current()
        assertFalse(place.automatic)
        assertEquals("Passo Gavia", place.name)
        assertEquals(46.3437, place.latitude, 1e-4)
        assertTrue(activity.isFinishing)
    }

    private fun address(name: String, lat: Double, lon: Double) = Address(Locale.ITALY).apply {
        featureName = name
        locality = name
        latitude = lat
        longitude = lon
    }

    @Test
    fun searchResultIsUsedAndCanBeAddedToFavorites() {
        val activity = settings()
        activity.pick(address("Livigno", 46.5386, 10.1357))

        // Used right away, even before answering the dialog.
        assertEquals("Livigno", LocationStore(context).current().name)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()

        assertEquals(listOf("Livigno"), FavoritesStore(context).all().map { it.name })
        assertTrue(activity.isFinishing)
    }

    @Test
    fun searchResultJustUsed() {
        val activity = settings()
        activity.pick(address("Bormio", 46.4669, 10.3705))
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        ShadowLooper.idleMainLooper()

        assertEquals("Bormio", LocationStore(context).current().name)
        assertTrue(FavoritesStore(context).all().isEmpty())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun quickSwitchFromMainPage() {
        FavoritesStore(context).add(Favorite("Livigno", 46.5386, 10.1357))
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<TextView>(R.id.place).performClick()

        val popup = ShadowPopupMenu.getLatestPopupMenu()
        assertNotNull("place menu not shown", popup)
        shadowOf(popup).onMenuItemClickListener.onMenuItemClick(popup.menu.getItem(0))

        val place = LocationStore(context).current()
        assertEquals("Livigno", place.name)
        assertTrue(activity.findViewById<TextView>(R.id.place).text.toString().startsWith("Livigno"))
    }
}
