package it.sunw.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * A second, independent way to keep the widgets current: every 15 minutes (the shortest period Android
 * allows) the system runs this job, which redraws them and re-arms the alarm. It survives reboots, and
 * if the alarm was lost this brings it back.
 */
class WidgetRefreshJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        SunWidgetProvider.updateAll(this, WidgetRefresh.Source.JOB)
        return false // done
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 4202
        internal const val PERIOD_MS = 15 * 60 * 1000L

        /** Schedules the periodic job if it isn't scheduled yet. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, WidgetRefreshJobService::class.java))
                    .setPeriodic(PERIOD_MS)
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
