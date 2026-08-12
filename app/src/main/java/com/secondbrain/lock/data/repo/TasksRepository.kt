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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import java.util.UUID

private const val LOCAL_ID_PREFIX = "local-"

object TasksRepository {
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Genuinely fire-and-forget background work that must outlive the suspend function that
     * launches it — [bankruptcy]/[undoBankruptcy]'s per-task PUTs specifically. A plain
     * `coroutineScope { launch { ... } }` inside those functions would AWAIT every child before
     * the function returns, which quietly defeats the entire point (the caller must never wait on
     * N network round-trips). SupervisorJob so one task's failure can't cancel the others. */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    /** Moves a task's due date to another day, or clears it entirely (making it a draft) when
     * [dueDate] is null — mirrors TasksPanel.js's scheduleTask().
     *
     * The null case can't go through [updateTask]/[ApiClient.putTyped]: [ApiClient.json]'s
     * `explicitNulls = false` means `UpdateTaskRequest(dueDate = null)` — a nullable property
     * whose default IS null — always encodes with the `due_date` key omitted entirely, never as
     * `"due_date": null`. A PATCH-style endpoint reads a missing key as "leave this field alone,"
     * so the typed path can never actually clear a due date over the network; it only ever
     * LOOKED like it worked, via the optimistic local state, until a later refresh (or the
     * mutation's own success handler swapping in the server's unchanged response) silently
     * reverted it. Confirmed by direct reproduction: a task's "waiting since ..." date survived
     * repeated real "Clear the whole list" (P9) network round-trips.
     *
     * Routes the null case through [ApiClient.putJson] with an explicit `org.json.JSONObject.NULL`
     * instead. Pre-existing bug, not introduced by P9 — [SyncQueue.flush]'s TYPE_UPDATE_TASK
     * replay path still uses the typed encoder for a QUEUED clear-due-date op, so a due-date clear
     * that gets queued while offline (rather than applied immediately here) will hit the exact
     * same gap on replay. Fixing that is a wider change than this call site; left as a known
     * follow-up rather than attempted here. */
    suspend fun setDueDate(id: String, dueDate: String?): Result<Task> {
        val result = if (dueDate == null) clearDueDate(id) else updateTask(
            id, UpdateTaskRequest(dueDate = dueDate), failureMessage = "Couldn't reschedule task"
        ) { it.copy(dueDate = dueDate) }
        if (result.isSuccess) ReminderScheduler.rescheduleAll()
        return result
    }

    /** The null-clearing half of [setDueDate] — same network-first / offline-optimistic-queue
     * shape as [updateTask], just built on [ApiClient.putJson] instead of [ApiClient.putTyped] so
     * the explicit null actually reaches the server. See [setDueDate]'s KDoc for why. */
    private suspend fun clearDueDate(id: String): Result<Task> {
        val update = UpdateTaskRequest(dueDate = null)
        val result = ApiClient.putJson("/api/tasks/$id", JSONObject().put("due_date", JSONObject.NULL))
            .mapCatching { obj -> ApiClient.json.decodeFromString<Task>(obj.toString()) }
        result.onSuccess { updated -> _tasks.update { list -> list.map { if (it.id == id) updated else it } }; _error.value = null }
        if (result.isFailure) {
            if (ApiClient.isOffline()) {
                val current = _tasks.value.find { it.id == id } ?: return result
                val optimistic = current.copy(dueDate = null, pendingSync = true)
                _tasks.update { list -> list.map { if (it.id == id) optimistic else it } }
                LocalCache.save("tasks", _tasks.value)
                SyncQueue.enqueue(
                    PendingOp(id = SyncQueue.newOpId(), type = PendingOp.TYPE_UPDATE_TASK, createdAt = System.currentTimeMillis(), taskId = id, update = update)
                )
                return Result.success(optimistic)
            }
            _error.value = result.exceptionOrNull()?.message ?: "Couldn't reschedule task"
        }
        return result
    }

    /** WelcomeBackSheet's "Clear the whole list" (P9) — sets every open task's due date to null
     * (making it a draft, per [com.secondbrain.lock.ui.screens.work.isDraftTask]) with no
     * confirmation dialog; a 10-second undo in the sheet itself is the entire safety net.
     *
     * [refresh]es first, awaited, before computing which tasks to clear — confirmed by direct
     * reproduction that skipping this drops tasks silently: on a cold start, this and
     * [com.secondbrain.lock.ui.screens.work.WorkScreen]'s own `TasksRepository.refresh()` call
     * both fire around the same time, and [_tasks] can still be [restore]'s (older) cached
     * snapshot at the exact moment bankruptcy reads it, missing anything created or changed since
     * that cache was last written. This does NOT reintroduce the "user must never wait" problem
     * this function otherwise avoids — nothing upstream ever awaits [bankruptcy]'s return before
     * dismissing the sheet, so this extra round-trip just makes the BACKGROUND work more correct,
     * invisibly, without blocking anything the user sees.
     *
     * Once current, applies to local state SYNCHRONOUSLY (the line right after) and returns a
     * snapshot the caller can hand back to [undoBankruptcy] — the actual per-task PUTs fire
     * afterward and are never awaited by anything upstream. Deliberately reuses [setDueDate]
     * (network-first, offline-queued on a connectivity failure) for each task rather than a
     * bespoke unconditional-enqueue shortcut, so a genuine per-task failure still surfaces
     * normally instead of masquerading as connectivity trouble. */
    suspend fun bankruptcy(): Map<String, String?> {
        runCatching { refresh() }
        val openTasks = _tasks.value.filter { !it.done }
        val snapshot = openTasks.associate { it.id to it.dueDate }
        _tasks.update { list -> list.map { if (!it.done) it.copy(dueDate = null, pendingSync = true) else it } }
        LocalCache.save("tasks", _tasks.value)
        openTasks.forEach { task -> backgroundScope.launch { setDueDate(task.id, null) } }
        return snapshot
    }

    /** Reverses [bankruptcy] within its 10-second undo window — [snapshot] maps task id to its
     * ORIGINAL due date (which may itself be null, for a task that was already a draft), so this
     * uses [Map.containsKey] rather than a null-coalescing read to tell "restore this task's
     * original null" apart from "this task wasn't part of the snapshot, leave it alone." */
    suspend fun undoBankruptcy(snapshot: Map<String, String?>) {
        _tasks.update { list ->
            list.map { task ->
                if (snapshot.containsKey(task.id)) task.copy(dueDate = snapshot.getValue(task.id), pendingSync = true) else task
            }
        }
        LocalCache.save("tasks", _tasks.value)
        snapshot.forEach { (id, dueDate) -> backgroundScope.launch { setDueDate(id, dueDate) } }
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
