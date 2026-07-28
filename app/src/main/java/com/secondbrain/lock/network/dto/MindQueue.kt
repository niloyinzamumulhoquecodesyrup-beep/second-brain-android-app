package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class MindQueueOption(
    val action: String? = null,
    val label: String? = null,
    val value: JsonObject? = null
)

/** One row from GET /api/mind/queue (backed by para_fun_queue). "Your brain suggests" only
 * surfaces the subset whose options include a create_task action — see [taskSuggestionTitle]. */
@Serializable
data class MindQueueItem(
    val id: String = "",
    @SerialName("note_id") val noteId: String? = null,
    @SerialName("question_type") val questionType: String = "",
    @SerialName("question_text") val questionText: String = "",
    val options: List<MindQueueOption> = emptyList()
)

/** Mirrors TasksPanel.js's taskSuggestion(): only counts as a task suggestion if some option
 * carries a create_task action with a usable title. */
fun MindQueueItem.taskSuggestionTitle(): String? {
    val option = options.firstOrNull { it.action == "create_task" } ?: return null
    val title = option.value?.get("title")?.jsonPrimitive?.contentOrNull
    return title?.ifBlank { null } ?: option.label?.ifBlank { null }
}

@Serializable
data class MindQueueTaskValue(val title: String)

/** POST /api/mind/queue/:id/answer. `value` is omitted from the JSON body when null
 * (ApiClient's Json has explicitNulls = false), matching the web client's two payload shapes:
 * `{action:"create_task", value:{title}}` to accept, `{action:"skip"}` to dismiss. */
@Serializable
data class MindQueueAnswerRequest(
    val action: String,
    val value: MindQueueTaskValue? = null
)
