package it.sunw.widget.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.sunw.widget.CrashLog

/** The alarm of a recurring summary (post it, set the next one), and boot or clock changes (set the alarm again). */
class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action != DigestScheduler.ACTION) {
            runCatching { DigestScheduler.scheduleNext(app) }.onFailure { CrashLog.record(app, it) }
            return
        }
        runCatching { DigestScheduler.scheduleNext(app) }.onFailure { CrashLog.record(app, it) } // the next one first
        val pending = goAsync()
        Thread {
            try {
                DigestNotifier.post(app)
            } catch (e: Throwable) {
                CrashLog.record(app, e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
