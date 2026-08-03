package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.data.PendingOp
import com.secondbrain.lock.data.SyncQueue
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.CreateTaskRequest
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.network.dto.TaskPiece
import com.secondbrain.lock.network.dto.UpdateTaskRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    suspend fun refresh() {
        ApiClient.getTyped<List<Task>>("/api/tasks")
            .onSuccess { _tasks.value = it; _error.value = null; LocalCache.save("tasks", it) }
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
        result.onSuccess { task -> _tasks.value = _tasks.value + task; _error.value = null }
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
                _tasks.value = _tasks.value + placeholder
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
                return Result.success(placeholder)
            }
            _error.value = result.exceptionOrNull()?.message ?: "Couldn't create task"
        }
        return result
    }

    suspend fun setDone(id: String, done: Boolean): Result<Task> =
        updateTask(id, UpdateTaskRequest(done = done), failureMessage = "Couldn't update task") { it.copy(done = done) }

    suspend fun reschedule(id: String, startMin: Int, durationMin: Int): Result<Task> =
        updateTask(id, UpdateTaskRequest(startMin = startMin, durationMin = durationMin), failureMessage = "Couldn't reschedule task") {
            it.copy(startMin = startMin, durationMin = durationMin)
        }

    /** Moves a task's due date to another day — mirrors TasksPanel.js's scheduleTask(). */
    suspend fun setDueDate(id: String, dueDate: String?): Result<Task> =
        updateTask(id, UpdateTaskRequest(dueDate = dueDate), failureMessage = "Couldn't reschedule task") { it.copy(dueDate = dueDate) }

    /** "Break it into pieces" — persists the focus session's sub-step checklist onto the task.
     * Not offline-queued: editing pieces offline is a narrow enough case it's left requiring
     * connectivity, same as before. */
    suspend fun setPieces(id: String, pieces: List<TaskPiece>): Result<Task> =
        ApiClient.putTyped<UpdateTaskRequest, Task>("/api/tasks/$id", UpdateTaskRequest(pieces = pieces))
            .onSuccess { updated -> _tasks.value = _tasks.value.map { if (it.id == id) updated else it } }

    /** A task that never synced (still has its local placeholder id) is simply dropped — and its
     * queued create cancelled via [SyncQueue.cancelPendingCreate] — rather than deleted
     * server-side, since the server never saw it. */
    suspend fun delete(id: String): Result<Unit> {
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            _tasks.value = _tasks.value.filterNot { it.id == id }
            LocalCache.save("tasks", _tasks.value)
            SyncQueue.cancelPendingCreate(id)
            return Result.success(Unit)
        }

        val result = ApiClient.deleteRaw("/api/tasks/$id")
        result.onSuccess { _tasks.value = _tasks.value.filterNot { it.id == id }; _error.value = null }
        if (result.isFailure) {
            if (ApiClient.isOffline()) {
                _tasks.value = _tasks.value.filterNot { it.id == id }
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
     * the task's own id for an update — for the server's authoritative copy. */
    suspend fun resolveLocalTask(placeholderId: String, real: Task) {
        _tasks.value = _tasks.value.map { if (it.id == placeholderId) real else it }
        LocalCache.save("tasks", _tasks.value)
    }

    /** Shared offline fallback for the single-field PUT mutators (done/reschedule/due-date): tries
     * the network call as usual; on a genuine failure it's surfaced via [_error] like [refresh]
     * already does, but on a connectivity failure the same [update] is applied to the local copy
     * optimistically (marked [Task.pendingSync]) and queued via [SyncQueue] instead. */
    private suspend fun updateTask(id: String, update: UpdateTaskRequest, failureMessage: String, apply: (Task) -> Task): Result<Task> {
        val result = ApiClient.putTyped<UpdateTaskRequest, Task>("/api/tasks/$id", update)
        result.onSuccess { updated -> _tasks.value = _tasks.value.map { if (it.id == id) updated else it }; _error.value = null }
        if (result.isFailure) {
            if (ApiClient.isOffline()) {
                val current = _tasks.value.find { it.id == id } ?: return result
                val optimistic = apply(current).copy(pendingSync = true)
                _tasks.value = _tasks.value.map { if (it.id == id) optimistic else it }
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
