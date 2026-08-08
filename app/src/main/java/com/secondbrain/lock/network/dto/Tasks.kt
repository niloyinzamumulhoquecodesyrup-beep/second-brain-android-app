package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A "break it into pieces" sub-step, as stored in the task's `pieces` jsonb column. */
@Serializable
data class TaskPiece(
    val id: String,
    val text: String,
    val done: Boolean = false
)

/** Row shape from GET/POST/PUT /api/tasks[/:id]. */
@Serializable
data class Task(
    val id: String = "",
    @SerialName("note_id") val noteId: String? = null,
    val title: String = "",
    val done: Boolean = false,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("start_min") val startMin: Int? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val pieces: List<TaskPiece>? = null,
    /** Local-only: true while this task (or an edit to it) is queued in [com.secondbrain.lock.data.SyncQueue]
     * waiting for connectivity. Never sent to or set by the server. */
    val pendingSync: Boolean = false
)

@Serializable
data class CreateTaskRequest(
    val title: String,
    @SerialName("note_id") val noteId: String? = null,
    @SerialName("due_date") val dueDate: String? = null
)

/** PUT /api/tasks/:id — only non-null fields are meant to change (COALESCE semantics server-side). */
@Serializable
data class UpdateTaskRequest(
    val title: String? = null,
    val done: Boolean? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("start_min") val startMin: Int? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
    val pieces: List<TaskPiece>? = null
)

/** POST /api/tasks/breakdown request. */
@Serializable
data class TaskBreakdownRequest(val task: String)

/** One AI-suggested subtask from a breakdown — not a real [Task] until the user adds it. */
@Serializable
data class TaskBreakdownSubtask(
    val topic: String,
    @SerialName("estimated_minutes") val estimatedMinutes: Int
)

/** POST /api/tasks/breakdown response. */
@Serializable
data class TaskBreakdownResponse(
    val task: String,
    val subtasks: List<TaskBreakdownSubtask>,
    @SerialName("remaining_today") val remainingToday: Int
)
