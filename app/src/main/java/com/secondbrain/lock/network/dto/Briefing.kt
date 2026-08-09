package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [data] is a complete, self-contained WAV file (mono, 16-bit PCM), base64-encoded. */
@Serializable
data class BriefingAudio(
    val data: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("sample_rate") val sampleRate: Int
)

@Serializable
data class BriefingTasksUsed(val overdue: Int = 0, val today: Int = 0, val upcoming: Int = 0)

/** POST /api/briefing/today response — the server enforces a once-per-UTC-day generation lock. */
@Serializable
data class BriefingResponse(
    val date: String,
    val script: String,
    val audio: BriefingAudio,
    @SerialName("tasks_used") val tasksUsed: BriefingTasksUsed,
    val cached: Boolean
)
