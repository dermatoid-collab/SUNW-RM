package it.sunw.widget

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Keeps the last crash (or caught, unexpected error) in a file, so the app can show it at the next
 * start: on a phone without a debugger it is the only way to learn why the app closed.
 */
object CrashLog {

    private const val FILE = "last_crash.txt"
    @Volatile private var context: Context? = null
    @Volatile private var installed = false

    /** Writes every uncaught exception of the app's process to the file, then lets Android handle it. */
    fun install(app: Context) {
        context = app.applicationContext
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            context?.let { record(it, error, thread.name) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Stores [error] (keeps only the latest). Never throws. */
    fun record(context: Context, error: Throwable, thread: String = Thread.currentThread().name) {
        runCatching {
            File(context.filesDir, FILE).writeText(
                "Build ${BuildConfig.BUILD_NUMBER} (${BuildConfig.GIT_SHA}) · Android ${Build.VERSION.RELEASE} " +
                    "(API ${Build.VERSION.SDK_INT}) · ${Build.MODEL}\nThread: $thread\n\n" + Log.getStackTraceString(error),
            )
        }
    }

    /** The stored error, or null when there is none. */
    fun pending(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
