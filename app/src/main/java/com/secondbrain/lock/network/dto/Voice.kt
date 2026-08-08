package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** POST /api/voice/classify request — the on-device transcript, no audio. */
@Serializable
data class VoiceClassifyRequest(val transcript: String)

/** "task" = action/to-do; "capture" = info, idea, or an explicit create-project/area/resource. */
@Serializable
data class VoiceClassification(
    val type: String,
    val title: String,
    val description: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("start_min") val startMin: Int? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
    val para: String,
    @SerialName("para_confidence") val paraConfidence: String,
    @SerialName("project_hint") val projectHint: String? = null
)

/**
 * POST /api/voice/classify response. [record] is left as raw JSON since its shape depends on
 * [classification]'s `type` (a `tasks` row vs a `notes` row) rather than being one fixed DTO.
 */
@Serializable
data class VoiceClassifyResponse(
    val transcript: String,
    val classification: VoiceClassification,
    val record: JsonElement
)
