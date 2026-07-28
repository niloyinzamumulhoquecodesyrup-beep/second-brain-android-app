package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** category: sleep|work|study|exercise|meals|leisure|other. status: active|done|skipped|dismissed|suggested. */
@Serializable
data class PlannerBlock(
    val id: String = "",
    @SerialName("plan_date") val planDate: String = "",
    val title: String = "",
    val category: String = "other",
    @SerialName("start_min") val startMin: Int = 0,
    @SerialName("duration_min") val durationMin: Int = 0,
    val status: String = "active",
    val source: String = "user",
    @SerialName("routine_id") val routineId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** days: 0=Mon..6=Sun. source: user|starter|cycle. */
@Serializable
data class PlannerRoutine(
    val id: String = "",
    val title: String = "",
    val category: String = "other",
    val days: List<Int> = emptyList(),
    @SerialName("start_min") val startMin: Int = 0,
    @SerialName("duration_min") val durationMin: Int = 0,
    val active: Boolean = true,
    val source: String = "user",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PlannerPrompt(
    val id: String = "",
    val text: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

/** GET /api/planner?from=&days= */
@Serializable
data class PlannerDayResponse(
    val blocks: List<PlannerBlock> = emptyList(),
    val routines: List<PlannerRoutine> = emptyList(),
    val prompts: List<PlannerPrompt> = emptyList()
)

@Serializable
data class CreatePlannerBlockRequest(
    val title: String,
    @SerialName("plan_date") val planDate: String,
    @SerialName("start_min") val startMin: Int,
    @SerialName("duration_min") val durationMin: Int,
    val category: String? = null,
    @SerialName("routine_id") val routineId: String? = null,
    val status: String? = null
)

@Serializable
data class UpdatePlannerBlockRequest(
    val title: String? = null,
    val category: String? = null,
    @SerialName("start_min") val startMin: Int? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
    @SerialName("plan_date") val planDate: String? = null,
    val status: String? = null
)

@Serializable
data class CreatePlannerRoutineRequest(
    val title: String,
    @SerialName("start_min") val startMin: Int,
    @SerialName("duration_min") val durationMin: Int,
    val category: String? = null,
    val days: List<Int>? = null,
    val source: String? = null
)

@Serializable
data class UpdatePlannerRoutineRequest(
    val title: String? = null,
    val category: String? = null,
    val days: List<Int>? = null,
    @SerialName("start_min") val startMin: Int? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
    val active: Boolean? = null
)

@Serializable
data class PlannerPromptRequest(val text: String)
