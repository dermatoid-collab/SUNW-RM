package it.sunw.widget

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Every background and accent combines freely and has what the widget and pages need. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppearanceTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun everyThemeWorksWithEveryAccent() {
        val store = AppearanceStore(context)
        for (accent in Accent.values()) {
            store.accent = accent
            for (theme in Theme.values()) {
                val palette = store.palette(theme)
                assertEquals("$theme accent", AppearanceStore.readable(accent.resolve(context), theme.isLight), palette.accent)
                assertEquals("$theme sky stops", 8, palette.sky.size)
                assertTrue("$theme sky order", palette.sky.zipWithNext().all { (a, b) -> a.first < b.first })
                assertNotNull("$theme background", context.getDrawable(theme.background))
                assertNotNull("$theme square background", context.getDrawable(theme.backgroundSquare))
            }
        }
    }

    @Test
    fun keysAreUnique() {
        assertEquals(Theme.values().size, Theme.values().map { it.key }.toSet().size)
        assertEquals(Accent.values().size, Accent.values().map { it.key }.toSet().size)
    }

    @Test
    fun theOriginalLookKeepsItsKeyAndIsTheDefaultUnderTheNameLegacy() {
        val store = AppearanceStore(context)
        assertEquals(Theme.ANTHRACITE, store.theme)
        assertEquals("anthracite", Theme.ANTHRACITE.key)
        assertEquals("SUNW-RM Legacy", context.getString(Theme.ANTHRACITE.label))
        // Its colours are the ones it always had.
        val p = store.palette(Theme.ANTHRACITE)
        assertEquals(0xFF121316.toInt(), p.pageBackground)
        assertEquals(0xFF1B1C20.toInt(), p.cardBackground)
        assertEquals(0xFFF2F1EE.toInt(), p.text)
    }

    @Test
    fun lightThemesHaveDarkTextOnLightPages() {
        val store = AppearanceStore(context)
        fun luminance(c: Int) = (0.299 * android.graphics.Color.red(c) + 0.587 * android.graphics.Color.green(c) + 0.114 * android.graphics.Color.blue(c)) / 255
        for (theme in Theme.values()) {
            val p = store.palette(theme)
            if (theme.isLight) {
                assertTrue("$theme page is light", luminance(p.pageBackground) > 0.7)
                assertTrue("$theme text is dark", luminance(p.text) < 0.35)
                // Raised areas and the unlit Moon stay visible against the card.
                assertTrue("$theme raised differs", p.raised(0.1f) != p.cardBackground)
                assertTrue("$theme moon differs", p.moonDark() != p.cardBackground)
            } else if (theme != Theme.MATERIAL_YOU) {
                assertTrue("$theme page is dark", luminance(p.pageBackground) < 0.35)
            }
        }
        assertEquals(3, Theme.values().count { it.isLight })
    }

    @Test
    fun lightAccentsAreDarkenedOnLightThemesOnly() {
        val store = AppearanceStore(context)
        store.accent = Accent.AMBER
        assertEquals(0xFFFFB547.toInt(), store.palette(Theme.NORD).accent)
        assertTrue(store.palette(Theme.SOLARIZED_LIGHT).accent != 0xFFFFB547.toInt())
    }
}
