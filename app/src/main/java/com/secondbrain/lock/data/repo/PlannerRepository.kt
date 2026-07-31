package com.secondbrain.lock.data.repo

import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.CreatePlannerBlockRequest
import com.secondbrain.lock.network.dto.CreatePlannerRoutineRequest
import com.secondbrain.lock.network.dto.PlannerBlock
import com.secondbrain.lock.network.dto.PlannerDayResponse
import com.secondbrain.lock.network.dto.PlannerRoutine
import com.secondbrain.lock.network.dto.UpdatePlannerBlockRequest
import com.secondbrain.lock.network.dto.UpdatePlannerRoutineRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

object PlannerRepository {
    private val _today = MutableStateFlow(PlannerDayResponse())
    val today: StateFlow<PlannerDayResponse> = _today.asStateFlow()

    private val _routines = MutableStateFlow<List<PlannerRoutine>>(emptyList())
    val routines: StateFlow<List<PlannerRoutine>> = _routines.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun restore() {
        LocalCache.load<PlannerDayResponse>("planner_today")?.let { _today.value = it }
        LocalCache.load<List<PlannerRoutine>>("planner_routines")?.let { _routines.value = it }
    }

    suspend fun refreshToday() {
        val date = LocalDate.now().toString()
        ApiClient.getTyped<PlannerDayResponse>("/api/planner?from=$date&days=1")
            .onSuccess { _today.value = it; _error.value = null; LocalCache.save("planner_today", it) }
            .onFailure { _error.value = it.message ?: "Couldn't load today's plan" }
    }

    suspend fun refreshRoutines() {
        ApiClient.getTyped<List<PlannerRoutine>>("/api/planner/routines")
            .onSuccess { _routines.value = it; _error.value = null; LocalCache.save("planner_routines", it) }
            .onFailure { _error.value = it.message ?: "Couldn't load routines" }
    }

    suspend fun createRoutine(request: CreatePlannerRoutineRequest): Result<PlannerRoutine> =
        ApiClient.postTyped<CreatePlannerRoutineRequest, PlannerRoutine>("/api/planner/routines", request)
            .onSuccess { routine -> _routines.value = _routines.value + routine }

    suspend fun updateRoutine(id: String, request: UpdatePlannerRoutineRequest): Result<PlannerRoutine> =
        ApiClient.patchTyped<UpdatePlannerRoutineRequest, PlannerRoutine>("/api/planner/routines/$id", request)
            .onSuccess { updated -> _routines.value = _routines.value.map { if (it.id == id) updated else it } }

    suspend fun deleteRoutine(id: String): Result<Unit> =
        ApiClient.deleteRaw("/api/planner/routines/$id")
            .onSuccess { _routines.value = _routines.value.filterNot { it.id == id } }

    suspend fun updateBlock(id: String, request: UpdatePlannerBlockRequest): Result<PlannerBlock> =
        ApiClient.patchTyped<UpdatePlannerBlockRequest, PlannerBlock>("/api/planner/$id", request)
            .onSuccess { updated ->
                _today.value = _today.value.copy(blocks = _today.value.blocks.map { if (it.id == id) updated else it })
            }

    /** Materializes a routine occurrence for one date — used to "skip today" a virtual (not yet
     * materialized) routine entry without touching the recurring routine itself. */
    suspend fun createBlock(request: CreatePlannerBlockRequest): Result<PlannerBlock> =
        ApiClient.postTyped<CreatePlannerBlockRequest, PlannerBlock>("/api/planner", request)
            .onSuccess { created -> _today.value = _today.value.copy(blocks = _today.value.blocks + created) }
}
