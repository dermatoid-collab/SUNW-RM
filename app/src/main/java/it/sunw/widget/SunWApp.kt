package it.sunw.widget

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class SunWApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        redrawWidgetsWhenTheScreenComesOn()
        // The weather watch (every 30 minutes) and the next recurring summary.
        runCatching {
            it.sunw.widget.watch.WatchJobService.ensure(this)
            it.sunw.widget.watch.DigestScheduler.scheduleNext(this)
        }.onFailure { CrashLog.record(this, it) }
    }

    /**
     * While the app's process is alive, turning the screen on or unlocking redraws the widgets (at most once
     * a minute): one more trigger beside the alarm and the background job, at no cost when nothing is shown.
     */
    private fun redrawWidgetsWhenTheScreenComesOn() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching { WidgetRefresh.onScreen(context) }.onFailure { CrashLog.record(context, it) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
    }
}
