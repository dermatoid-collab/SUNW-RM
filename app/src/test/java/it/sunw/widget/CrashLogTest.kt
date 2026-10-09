package it.sunw.widget

import android.content.Context
import android.widget.TextView
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

/** The last crash is kept and shown at the next start instead of the page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashLogTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun anUncaughtExceptionIsWrittenDownAndRead() {
        CrashLog.clear(context)
        assertNull(CrashLog.pending(context))
        CrashLog.record(context, IllegalStateException("boom"), "main")
        val report = CrashLog.pending(context)!!
        assertTrue(report.contains("IllegalStateException: boom"))
        assertTrue(report.contains("Thread: main"))
        CrashLog.clear(context)
        assertNull(CrashLog.pending(context))
    }

    @Test
    fun theDefaultHandlerRecordsTheCrash() {
        CrashLog.clear(context)
        CrashLog.install(context)
        // The handler writes the file and passes the error on; here the previous handler is a no-op.
        val handler = Thread.getDefaultUncaughtExceptionHandler()!!
        runCatching { handler.uncaughtException(Thread.currentThread(), RuntimeException("crashed")) }
        assertTrue(CrashLog.pending(context)!!.contains("RuntimeException: crashed"))
        CrashLog.clear(context)
    }

    @Test
    fun theMainPageShowsTheCrashBeforeOpeningAndThenContinues() {
        CrashLog.record(context, RuntimeException("last time"), "main")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)
        assertNull("the page isn't built while the error is shown", activity.findViewById<TextView>(R.id.place))
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull(CrashLog.pending(context))
    }

    @Test
    fun withoutACrashThePageOpensAsUsual() {
        CrashLog.clear(context)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(android.view.View.VISIBLE, activity.findViewById<TextView>(R.id.place).visibility)
    }
}
