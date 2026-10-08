package it.sunw.widget

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsBackupTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun clear() = listOf("place", "favorites", "appearance").forEach {
        context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Before
    fun setUp() = clear()

    @Test
    fun exportThenImportRestoresEverything() {
        LocationStore(context).save(46.5287, 10.4527, automatic = false, name = "Passo dello Stelvio")
        FavoritesStore(context).add(Favorite("Livigno", 46.5386, 10.1357))
        FavoritesStore(context).add(Favorite("🏠", 44.73, 10.37))
        AppearanceStore(context).apply {
            curveStyle = CurveStyle.TWILIGHT
            accent = Accent.TOKYO_BLUE
            theme = Theme.TOKYO_NIGHT
        }
        val json = SettingsBackup.export(context)

        clear() // as after a reinstall
        SettingsBackup.import(context, json)

        val place = LocationStore(context).current()
        assertFalse(place.automatic)
        assertEquals("Passo dello Stelvio", place.name)
        assertEquals(46.5287, place.latitude, 1e-9)
        assertEquals(listOf("Livigno", "🏠"), FavoritesStore(context).all().map { it.name })
        AppearanceStore(context).let {
            assertEquals(CurveStyle.TWILIGHT, it.curveStyle)
            assertEquals(Accent.TOKYO_BLUE, it.accent)
            assertEquals(Theme.TOKYO_NIGHT, it.theme)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherFiles() {
        SettingsBackup.import(context, """{"hello":"world"}""")
    }
}
