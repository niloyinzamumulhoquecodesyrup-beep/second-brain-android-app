package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** kind: task|routine|block|routine_suggestion|interest_event|custom|.... origin: user|cycle|system. status: active|done|dismissed|snoozed. */
@Serializable
data class Reminder(
    val id: String = "",
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("routine_id") val routineId: String? = null,
    val message: String = "",
    @SerialName("fire_at") val fireAt: String? = null,
    @SerialName("time_min") val timeMin: Int? = null,
    @SerialName("lead_min") val leadMin: Int? = null,
    val kind: String = "custom",
    val origin: String = "user",
    val status: String = "active",
    @SerialName("snooze_until") val snoozeUntil: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val due: Boolean = false,
    val payload: JsonElement? = null
) {
    /** Ports lib/reminders.js's reminderOpenTarget: an external "Learn more" link for interest_event reminders. */
    val learnMoreUrl: String?
        get() = if (kind == "interest_event") {
            ((payload as? JsonObject)?.get("source_url") as? JsonPrimitive)?.contentOrNull
        } else null
}

/** PATCH /api/reminders/:id — action: snooze|done|dismiss|accept. */
@Serializable
data class ReminderActionRequest(
    val action: String,
    val minutes: Int? = null
)
