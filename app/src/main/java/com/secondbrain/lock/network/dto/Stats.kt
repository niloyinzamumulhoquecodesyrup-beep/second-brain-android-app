package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParaCounts(
    val inbox: Int = 0,
    val project: Int = 0,
    val area: Int = 0,
    val resource: Int = 0,
    val archive: Int = 0
)

@Serializable
data class OpenTaskSummary(
    val id: String = "",
    val title: String = "",
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("note_id") val noteId: String? = null,
    @SerialName("note_title") val noteTitle: String? = null
)

@Serializable
data class RecentNoteSummary(
    val id: String = "",
    val title: String = "",
    val para: String = "inbox",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TagCount(
    val tag: String = "",
    val count: Int = 0
)

@Serializable
data class DayCount(
    val day: String = "",
    val count: Int = 0
)

/** GET /api/stats */
@Serializable
data class Stats(
    val totalNotes: Int = 0,
    val para: ParaCounts = ParaCounts(),
    val distilled: Int = 0,
    val packets: Int = 0,
    val tasksOpen: Int = 0,
    val tasksDone: Int = 0,
    val openTasks: List<OpenTaskSummary> = emptyList(),
    val links: Int = 0,
    val recent: List<RecentNoteSummary> = emptyList(),
    val topTags: List<TagCount> = emptyList(),
    val capturesByDay: List<DayCount> = emptyList(),
    val focusSessionsByDay: List<DayCount> = emptyList(),
    val focusSessionsTotal: Int = 0,
    val tasksDoneByDay: List<DayCount> = emptyList(),
    /** Minutes of completed focus/Pomodoro time per day (server-aggregated once the backend adds it). */
    val focusMinutesByDay: List<DayCount> = emptyList(),
    val focusMinutesTotal: Int = 0
)
