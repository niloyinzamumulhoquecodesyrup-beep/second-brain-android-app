package com.secondbrain.lock.data.repo

import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.CreateTaskRequest
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.network.dto.TaskPiece
import com.secondbrain.lock.network.dto.UpdateTaskRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TasksRepository {
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun refresh() {
        ApiClient.getTyped<List<Task>>("/api/tasks")
            .onSuccess { _tasks.value = it; _error.value = null }
            .onFailure { _error.value = it.message ?: "Couldn't load tasks" }
    }

    suspend fun create(title: String, dueDate: String? = null, noteId: String? = null): Result<Task> =
        ApiClient.postTyped<CreateTaskRequest, Task>(
            "/api/tasks",
            CreateTaskRequest(title = title, dueDate = dueDate, noteId = noteId)
        ).onSuccess { task -> _tasks.value = _tasks.value + task }

    suspend fun setDone(id: String, done: Boolean): Result<Task> =
        ApiClient.putTyped<UpdateTaskRequest, Task>("/api/tasks/$id", UpdateTaskRequest(done = done))
            .onSuccess { updated -> _tasks.value = _tasks.value.map { if (it.id == id) updated else it } }

    suspend fun reschedule(id: String, startMin: Int, durationMin: Int): Result<Task> =
        ApiClient.putTyped<UpdateTaskRequest, Task>(
            "/api/tasks/$id",
            UpdateTaskRequest(startMin = startMin, durationMin = durationMin)
        ).onSuccess { updated -> _tasks.value = _tasks.value.map { if (it.id == id) updated else it } }

    /** Moves a task's due date to another day — mirrors TasksPanel.js's scheduleTask(). */
    suspend fun setDueDate(id: String, dueDate: String?): Result<Task> =
        ApiClient.putTyped<UpdateTaskRequest, Task>("/api/tasks/$id", UpdateTaskRequest(dueDate = dueDate))
            .onSuccess { updated -> _tasks.value = _tasks.value.map { if (it.id == id) updated else it } }

    /** "Break it into pieces" — persists the focus session's sub-step checklist onto the task. */
    suspend fun setPieces(id: String, pieces: List<TaskPiece>): Result<Task> =
        ApiClient.putTyped<UpdateTaskRequest, Task>("/api/tasks/$id", UpdateTaskRequest(pieces = pieces))
            .onSuccess { updated -> _tasks.value = _tasks.value.map { if (it.id == id) updated else it } }

    suspend fun delete(id: String): Result<Unit> =
        ApiClient.deleteRaw("/api/tasks/$id")
            .onSuccess { _tasks.value = _tasks.value.filterNot { it.id == id } }
}
