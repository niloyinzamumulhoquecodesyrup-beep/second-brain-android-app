package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /api/briefing/today request body — both the body and the field are optional. When given,
 * anything in today's schedule whose clock time already passed is dropped before the AI ever sees
 * it. Only matters on the first call of the day, since generation is cached behind a once-per-day
 * lock — a 7am call bakes a 7am perspective into the whole day's audio. */
@Serializable
data class BriefingRequest(@SerialName("current_time_min") val currentTimeMin: Int? = null)

/** [data] is a complete, self-contained WAV file (mono, 16-bit PCM), base64-encoded. */
@Serializable
data class BriefingAudio(
    val data: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("sample_rate") val sampleRate: Int
)

@Serializable
data class BriefingTasksUsed(
    val overdue: Int = 0,
    val today: Int = 0,
    val upcoming: Int = 0,
    @SerialName("today_routine_items") val todayRoutineItems: Int = 0
)

/** POST /api/briefing/today response — the server enforces a once-per-UTC-day generation lock. */
@Serializable
data class BriefingResponse(
    val date: String,
    val script: String,
    val audio: BriefingAudio,
    @SerialName("tasks_used") val tasksUsed: BriefingTasksUsed,
    val cached: Boolean
)
