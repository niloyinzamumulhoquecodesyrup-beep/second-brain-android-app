package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.data.PendingOp
import com.secondbrain.lock.data.SyncQueue
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.service.ReminderScheduler
import com.secondbrain.lock.network.dto.CreateTaskRequest
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.network.dto.TaskBreakdownRequest
import com.secondbrain.lock.network.dto.TaskBreakdownResponse
import com.secondbrain.lock.network.dto.TaskPiece
import com.secondbrain.lock.network.dto.UpdateTaskRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

private const val LOCAL_ID_PREFIX = "local-"

object TasksRepository {
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun restore() {
        LocalCache.load<List<Task>>("tasks")?.let { _tasks.value = it }
    }

    /** Merges the server's list into local state rather than replacing it wholesale — a plain
     * `_tasks.value = server` wipes any offline-created placeholder (and reverts any queued
     * optimistic edit) whenever this beats [SyncQueue.flush]'s WorkManager job back from a cold
     * start, which it usually does. See [resolveLocalTask] for the matching fix on the other side
     * of that race.
     *
     * Uses [MutableStateFlow.update] (a CAS retry loop), not a plain read-then-write, because this
     * merge and [resolveLocalTask]'s merge are two independent writers that can genuinely run
     * concurrently — the flush's POST and this GET can both land back-to-back on reconnect. A
     * plain `_tasks.value = _tasks.value.x` on both sides can interleave into one writer's result
     * clobbering the other's, which in practice showed up as the same server task appearing twice
     * in the list. `update` guarantees each transform sees the other's result before committing. */
    suspend fun refresh() {
        ApiClient.getTyped<List<Task>>("/api/tasks")
            .onSuccess { server ->
                _tasks.update { local ->
                    // Placeholders the server can't know about yet — keep them, or an
                    // offline-created task blinks out of existence until a later refresh resolves it.
                    val unsynced = local.filter { it.id.startsWith(LOCAL_ID_PREFIX) }
                    // Real rows with a queued optimistic edit — keep OUR version, not the server's
                    // stale one, or the user watches their offline edit (e.g. a "done" tick) revert
                    // until the flush lands.
                    val locallyEdited = local.filter { it.pendingSync && !it.id.startsWith(LOCAL_ID_PREFIX) }
                        .associateBy { it.id }
                    server.map { locallyEdited[it.id] ?: it } + unsynced
                }
                _error.value = null
                LocalCache.save("tasks", _tasks.value)
                ReminderScheduler.rescheduleAll()
            }
            .onFailure { _error.value = it.message ?: "Couldn't load tasks" }
    }

    /** On a genuine (non-connectivity) failure this surfaces the error like every other mutator
     * here. When offline, it instead inserts a local placeholder (id "local-<uuid>",
     * [Task.pendingSync] = true) and queues the real create via [SyncQueue] for when connectivity
     * returns — see [SyncQueue.flush]'s "create_task" handling for how the placeholder gets
     * swapped for the server's real task. */
    suspend fun create(title: String, dueDate: String? = null, noteId: String? = null): Result<Task> {
        val request = CreateTaskRequest(title = title, dueDate = dueDate, noteId = noteId)
        val result = ApiClient.postTyped<CreateTaskRequest, Task>("/api/tasks", request)
        result.onSuccess { task -> _tasks.update { it + task }; _error.value = null; ReminderScheduler.rescheduleAll() }
        if (result.isFailure) {
            if (ApiClient.isOffline()) {
                val placeholder = Task(
                    id = "$LOCAL_ID_PREFIX${UUID.randomUUID()}",
                    title = title,
                    dueDate = dueDate,
                    noteId = noteId,
                    // A server-created task always has created_at set to "now" — without it here,
                    // an undated placeholder reads as created on no particular day, so
                    // isTodayTask/isDraftTask (TasksPanel.kt) sort it into Drafts instead of
                    // Today, same as a real task would if the server ever left this null.
                    createdAt = java.time.Instant.now().toString(),
                    pendingSync = true
                )
                _tasks.update { it + placeholder }
                LocalCache.save("tasks", _tasks.value)
                SyncQueue.enqueue(
                    PendingOp(
                        id = SyncQueue.newOpId(),
                        type = PendingOp.TYPE_CREATE_TASK,
                        createdAt = System.currentTimeMillis(),
                        localId = placeholder.id,
                        create = request
                    )
                )
                ReminderScheduler.rescheduleAll()
                return Result.success(placeholder)
            }
            _error.value = result.exceptionOrNull()?.message ?: "Couldn't create task"
        }
        return result
    }

    suspend fun setDone(id: String, done: Boolean): Result<Task> =
        updateTask(id, UpdateTaskRequest(done = done), failureMessage = "Couldn't update task") { it.copy(done = done) }

    suspend fun reschedule(id: String, startMin: Int, durationMin: Int): Result<Task> {
        val result = updateTask(id, UpdateTaskRequest(startMin = startMin, durationMin = durationMin), failureMessage = "Couldn't reschedule task") {
            it.copy(startMin = startMin, durationMin = durationMin)
        }
        if (result.isSuccess) ReminderScheduler.rescheduleAll()
        return result
    }

    /** Moves a task's due date to another day — mirrors TasksPanel.js's scheduleTask(). */
    suspend fun setDueDate(id: String, dueDate: String?): Result<Task> {
        val result = updateTask(id, UpdateTaskRequest(dueDate = dueDate), failureMessage = "Couldn't reschedule task") { it.copy(dueDate = dueDate) }
        if (result.isSuccess) ReminderScheduler.rescheduleAll()
        return result
    }

    /** "Break it into pieces" — persists the focus session's sub-step checklist onto the task.
     * Not offline-queued: editing pieces offline is a narrow enough case it's left requiring
     * connectivity, same as before. */
    suspend fun setPieces(id: String, pieces: List<TaskPiece>): Result<Task> =
        ApiClient.putTyped<UpdateTaskRequest, Task>("/api/tasks/$id", UpdateTaskRequest(pieces = pieces))
            .onSuccess { updated -> _tasks.update { list -> list.map { if (it.id == id) updated else it } } }

    /** AI breakdown of a task into subtasks (long-press on a task row). Purely a lookup — the
     * suggestions it returns are ephemeral until the caller adds one via [create]/[reschedule], so
     * this doesn't touch [_tasks]/[_error] the way the mutators above do. */
    suspend fun breakdown(task: String): Result<TaskBreakdownResponse> =
        ApiClient.postTyped<TaskBreakdownRequest, TaskBreakdownResponse>("/api/tasks/breakdown", TaskBreakdownRequest(task))

    /** A task that never synced (still has its local placeholder id) is simply dropped — and its
     * queued create cancelled via [SyncQueue.cancelPendingCreate] — rather than deleted
     * server-side, since the server never saw it. */
    suspend fun delete(id: String): Result<Unit> {
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            _tasks.update { it.filterNot { t -> t.id == id } }
            LocalCache.save("tasks", _tasks.value)
            SyncQueue.cancelPendingCreate(id)
            return Result.success(Unit)
        }

        val result = ApiClient.deleteRaw("/api/tasks/$id")
        result.onSuccess { _tasks.update { it.filterNot { t -> t.id == id } }; _error.value = null }
        if (result.isFailure) {
            if (ApiClient.isOffline()) {
                _tasks.update { it.filterNot { t -> t.id == id } }
                LocalCache.save("tasks", _tasks.value)
                SyncQueue.enqueue(
                    PendingOp(id = SyncQueue.newOpId(), type = PendingOp.TYPE_DELETE_TASK, createdAt = System.currentTimeMillis(), taskId = id)
                )
                return Result.success(Unit)
            }
            _error.value = result.exceptionOrNull()?.message ?: "Couldn't delete task"
        }
        return result
    }

    /** Called by [SyncQueue.flush] once a queued create/update op succeeds: swaps the optimistic
     * local entry — matched by [placeholderId], the old "local-" id for a resolved create, or just
     * the task's own id for an update — for the server's authoritative copy. A plain `.map` here
     * silently drops the task if a [refresh] already wiped the placeholder before this ran (the
     * flush racing behind a refresh is the common case, not an edge case) — so append instead when
     * the placeholder is already gone and the real task isn't present yet either. */
    suspend fun resolveLocalTask(placeholderId: String, real: Task) {
        _tasks.update { current ->
            when {
                current.any { it.id == placeholderId } -> current.map { if (it.id == placeholderId) real else it }
                current.none { it.id == real.id } -> current + real
                else -> current
            }
        }
        LocalCache.save("tasks", _tasks.value)
    }

    /** Shared offline fallback for the single-field PUT mutators (done/reschedule/due-date): tries
     * the network call as usual; on a genuine failure it's surfaced via [_error] like [refresh]
     * already does, but on a connectivity failure the same [update] is applied to the local copy
     * optimistically (marked [Task.pendingSync]) and queued via [SyncQueue] instead. */
    private suspend fun updateTask(id: String, update: UpdateTaskRequest, failureMessage: String, apply: (Task) -> Task): Result<Task> {
        val result = ApiClient.putTyped<UpdateTaskRequest, Task>("/api/tasks/$id", update)
        result.onSuccess { updated -> _tasks.update { list -> list.map { if (it.id == id) updated else it } }; _error.value = null }
        if (result.isFailure) {
            if (ApiClient.isOffline()) {
                val current = _tasks.value.find { it.id == id } ?: return result
                val optimistic = apply(current).copy(pendingSync = true)
                _tasks.update { list -> list.map { if (it.id == id) optimistic else it } }
                LocalCache.save("tasks", _tasks.value)
                SyncQueue.enqueue(
                    PendingOp(id = SyncQueue.newOpId(), type = PendingOp.TYPE_UPDATE_TASK, createdAt = System.currentTimeMillis(), taskId = id, update = update)
                )
                return Result.success(optimistic)
            }
            _error.value = result.exceptionOrNull()?.message ?: failureMessage
        }
        return result
    }
}
