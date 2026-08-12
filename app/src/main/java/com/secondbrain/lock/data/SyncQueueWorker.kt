package com.secondbrain.lock.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Replays [SyncQueue]'s pending offline mutations — enqueued by [SyncQueue.scheduleFlush] with a
 * `NetworkType.CONNECTED` constraint, so WorkManager itself holds/retries this until online. */
class SyncQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        runCatching { SyncQueue.flush() }
            .fold(
                onSuccess = { drained -> if (drained) Result.success() else Result.retry() },
                onFailure = { Result.retry() }
            )
}
