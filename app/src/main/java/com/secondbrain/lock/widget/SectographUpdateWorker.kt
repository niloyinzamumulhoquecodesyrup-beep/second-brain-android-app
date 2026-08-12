package com.secondbrain.lock.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.secondbrain.lock.data.RoutineRepository
import java.util.concurrent.TimeUnit

/** Refreshes the routine cache, then redraws every sectograph widget instance. */
class SectographUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { RoutineRepository(applicationContext).refresh() }
        runCatching { SectographWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "sectograph_periodic_refresh"

        fun schedulePeriodic(context: Context) {
            // 15 minutes is WorkManager's own hard floor for PeriodicWorkRequest — cannot go
            // lower (P18).
            val request = PeriodicWorkRequestBuilder<SectographUpdateWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Call after anything that changes what the widget should show (routines, theme, login). */
        fun requestImmediateUpdate(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SectographUpdateWorker>().build())
        }
    }
}
