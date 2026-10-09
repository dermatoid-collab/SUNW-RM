package it.sunw.widget.alerts

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import it.sunw.widget.CrashLog

/**
 * About once an hour, with a network, checks the Civil Protection bulletin even when the app is
 * closed, and notifies new alerts for the place in use ([AlertNotifier]). Survives reboots.
 */
class AlertJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        AlertRepository(this).refresh { result ->
            result.onSuccess {
                runCatching { AlertNotifier.check(applicationContext, it) }.onFailure { e -> CrashLog.record(applicationContext, e) }
            }
            jobFinished(params, false)
        }
        return true // work continues on the repository's thread
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 4201
        private const val PERIOD_MS = 60 * 60 * 1000L
        private const val FLEX_MS = 20 * 60 * 1000L

        /** Schedules the periodic check if notifications are on and it isn't scheduled yet. */
        fun schedule(context: Context) {
            if (!AlertNotifier.isEnabled(context)) return
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, AlertJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(PERIOD_MS, FLEX_MS)
                    .setPersisted(true)
                    .build(),
            )
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }

        internal fun isScheduled(context: Context) =
            context.getSystemService(JobScheduler::class.java)?.getPendingJob(JOB_ID) != null
    }
}
