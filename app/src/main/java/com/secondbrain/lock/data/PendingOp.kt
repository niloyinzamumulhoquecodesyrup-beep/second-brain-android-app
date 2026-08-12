package com.secondbrain.lock.data

import com.secondbrain.lock.network.dto.CreateNoteRequest
import com.secondbrain.lock.network.dto.CreateTaskRequest
import com.secondbrain.lock.network.dto.UpdateTaskRequest
import kotlinx.serialization.Serializable

/**
 * A single offline-queued mutation, persisted (as a list) via [LocalCache] and replayed in
 * [SyncQueue.flush] once connectivity returns. Flat shape with one field set per [type] rather
 * than a sealed hierarchy, to keep it a plain kotlinx.serialization data class like every other
 * DTO in this codebase (FocusState, RoutineCache, ...).
 */
@Serializable
data class PendingOp(
    val id: String,
    val type: String,
    val createdAt: Long,
    /** create_task / create_note: the placeholder id [TasksRepository]/[NotesRepository] assigned
     * locally, to resolve once synced. */
    val localId: String? = null,
    /** update_task / delete_task / log_focus_activity: target task id — may itself still be an
     * unresolved [localId] from an earlier queued create_task, resolved at flush time. */
    val taskId: String? = null,
    val create: CreateTaskRequest? = null,
    val update: UpdateTaskRequest? = null,
    /** create_note payload. */
    val createNote: CreateNoteRequest? = null,
    val mode: String? = null,
    val minutes: Int? = null
) {
    companion object {
        const val TYPE_CREATE_TASK = "create_task"
        const val TYPE_UPDATE_TASK = "update_task"
        const val TYPE_DELETE_TASK = "delete_task"
        const val TYPE_LOG_FOCUS_ACTIVITY = "log_focus_activity"
        const val TYPE_CREATE_NOTE = "create_note"
    }
}
