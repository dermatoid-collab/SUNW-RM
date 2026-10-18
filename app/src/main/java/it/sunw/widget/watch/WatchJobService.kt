package it.sunw.widget.watch

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import it.sunw.widget.CrashLog

/**
 * Every 30 minutes, with a network, runs the weather watch for the place in use (see [WeatherWatch]) and makes sure the
 * recurring summaries are scheduled. Survives reboots.
 */
class WatchJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val context = applicationContext
        Thread {
            runCatching { DigestScheduler.scheduleNext(context) }.onFailure { CrashLog.record(context, it) }
            runCatching { WeatherWatch.run(context) }.onFailure { CrashLog.record(context, it) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 4203
        private const val PERIOD_MS = 30 * 60 * 1000L
        private const val FLEX_MS = 10 * 60 * 1000L

        /** Schedules the periodic run when the watch is on, cancels it when it is off. */
        fun ensure(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (!WatchPrefs.isEnabled(context)) {
                scheduler.cancel(JOB_ID)
                return
            }
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, WatchJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(PERIOD_MS, FLEX_MS)
                    .setPersisted(true)
                    .build(),
            )
        }

        internal fun isScheduled(context: Context) =
            context.getSystemService(JobScheduler::class.java)?.getPendingJob(JOB_ID) != null
    }
}
