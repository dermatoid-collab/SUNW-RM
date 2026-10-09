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
                assertEquals("$theme accent", accent.resolve(context), palette.accent)
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
}
