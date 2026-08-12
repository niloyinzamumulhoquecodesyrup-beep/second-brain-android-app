package com.secondbrain.lock.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.CreateNoteRequest
import com.secondbrain.lock.network.dto.CreateTaskRequest
import com.secondbrain.lock.network.dto.Note
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.network.dto.UpdateTaskRequest
import java.util.UUID

/**
 * Queue of offline-created mutations (task create/update/delete, focus-activity logging) that
 * couldn't reach the server. Persisted via [LocalCache] (same blob-cache every other repository
 * already uses) rather than a new Room table — it's just an ordered list, no querying needed.
 * [scheduleFlush] leans on WorkManager's `NetworkType.CONNECTED` constraint instead of a hand-rolled
 * ConnectivityManager listener: WorkManager itself holds/retries the job until connectivity returns.
 */
object SyncQueue {
    private const val CACHE_KEY = "pending_ops"
    private const val WORK_NAME = "sync_queue_flush"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun enqueue(op: PendingOp) {
        val current = LocalCache.load<List<PendingOp>>(CACHE_KEY).orEmpty()
        LocalCache.save(CACHE_KEY, current + op)
        scheduleFlush()
    }

    /** Drops a queued create_task/create_note (and any update/delete queued against that same
     * never-synced localId) — used when a task or note is deleted before it ever reached the
     * server, so it's simply never sent instead of being created then immediately deleted. */
    suspend fun cancelPendingCreate(localId: String) {
        val current = LocalCache.load<List<PendingOp>>(CACHE_KEY).orEmpty()
        val remaining = current.filterNot {
            ((it.type == PendingOp.TYPE_CREATE_TASK || it.type == PendingOp.TYPE_CREATE_NOTE) && it.localId == localId) ||
                ((it.type == PendingOp.TYPE_UPDATE_TASK || it.type == PendingOp.TYPE_DELETE_TASK) && it.taskId == localId)
        }
        LocalCache.save(CACHE_KEY, remaining)
    }

    fun scheduleFlush() {
        val request = OneTimeWorkRequestBuilder<SyncQueueWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Replays queued ops in order, stopping at the first NETWORK failure so ordering/consistency
     * is preserved (an update must never apply before its own create) — WorkManager's own backoff
     * retries the remaining batch later, driven by this function's return value.
     *
     * A structurally malformed op (missing a field its type requires) is a different case: no
     * amount of retrying will ever make it succeed, so it's dropped from the queue outright rather
     * than left in place. Leaving it in place would both poison the queue forever (retried and
     * skipped on every future flush) AND let every op after it run out of order, since a bare
     * `continue` skips removal without breaking. Malformed => drop and keep going. Network failure
     * => stop and preserve order.
     *
     * @return true if the queue fully drained (or was already empty); false if it stopped early
     * with ops still remaining, in which case [SyncQueueWorker.doWork] should ask WorkManager to
     * retry.
     */
    suspend fun flush(): Boolean {
        val ops = LocalCache.load<List<PendingOp>>(CACHE_KEY).orEmpty().sortedBy { it.createdAt }
        if (ops.isEmpty()) return true

        val resolvedIds = mutableMapOf<String, String>()
        val remaining = ops.toMutableList()

        for (op in ops) {
            val resolvedTaskId = op.taskId?.let { resolvedIds[it] ?: it }
            val succeeded = when (op.type) {
                PendingOp.TYPE_CREATE_TASK -> {
                    val create = op.create
                    val localId = op.localId
                    if (create == null || localId == null) {
                        remaining.remove(op)
                        continue
                    }
                    ApiClient.postTyped<CreateTaskRequest, Task>("/api/tasks", create)
                        .onSuccess { real ->
                            resolvedIds[localId] = real.id
                            TasksRepository.resolveLocalTask(localId, real)
                        }
                        .isSuccess
                }
                PendingOp.TYPE_UPDATE_TASK -> {
                    val update = op.update
                    val id = resolvedTaskId
                    if (update == null || id == null) {
                        remaining.remove(op)
                        continue
                    }
                    ApiClient.putTyped<UpdateTaskRequest, Task>("/api/tasks/$id", update)
                        .onSuccess { real -> TasksRepository.resolveLocalTask(id, real) }
                        .isSuccess
                }
                PendingOp.TYPE_DELETE_TASK -> {
                    val id = resolvedTaskId
                    if (id == null) {
                        remaining.remove(op)
                        continue
                    }
                    ApiClient.deleteRaw("/api/tasks/$id").isSuccess
                }
                PendingOp.TYPE_LOG_FOCUS_ACTIVITY -> {
                    val mode = op.mode
                    val minutes = op.minutes
                    if (mode == null || minutes == null) {
                        remaining.remove(op)
                        continue
                    }
                    ApiClient.postFocusActivity(mode, minutes, resolvedTaskId, null).isSuccess
                }
                PendingOp.TYPE_CREATE_NOTE -> {
                    val create = op.createNote
                    val localId = op.localId
                    if (create == null || localId == null) {
                        remaining.remove(op)
                        continue
                    }
                    ApiClient.postTyped<CreateNoteRequest, Note>("/api/notes", create)
                        .onSuccess { real ->
                            resolvedIds[localId] = real.id
                            NotesRepository.resolveLocalNote(localId, real)
                        }
                        .isSuccess
                }
                else -> {
                    // Unrecognized op type (e.g. written by a newer app version) — can't be
                    // replayed here either, so drop it rather than looping on it forever.
                    remaining.remove(op)
                    continue
                }
            }
            if (succeeded) {
                remaining.remove(op)
            } else {
                break
            }
        }

        LocalCache.save(CACHE_KEY, remaining)
        return remaining.isEmpty()
    }

    fun newOpId(): String = UUID.randomUUID().toString()
}
