package com.secondbrain.lock.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Matches InsightCards.js's SourceRefs rendering exactly: `note` uses id+title, `mind_insight`
 * uses kind, `resource` uses title+url, anything else falls back to name/type + total/
 * followed_through or value. `value` is genuinely polymorphic server-side (seen as both a
 * number and a boolean for different "stat" refs), so it's read as a raw JsonElement and
 * formatted on display rather than typed as Double — a strict Double crashed the whole
 * insights parse the moment any ref's value happened to be `true`/`false`.
 */
@Serializable
data class SourceRef(
    val type: String = "note",
    val id: String? = null,
    val title: String? = null,
    val url: String? = null,
    val kind: String? = null,
    val name: String? = null,
    val total: Int? = null,
    @SerialName("followed_through") val followedThrough: Int? = null,
    val value: JsonElement? = null
) {
    val valueDisplay: String?
        get() = when (val v = value) {
            null -> null
            is JsonPrimitive -> v.contentOrNull
            else -> v.toString()
        }
}

/** metadata is opaque per-kind JSON (path/concept/chart/suggestion/name/icon/...). */
@Serializable
data class MindInsight(
    val id: String = "",
    val kind: String = "",
    val summary: String = "",
    @SerialName("source_refs") val sourceRefs: List<SourceRef> = emptyList(),
    val section: String? = null,
    val metadata: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    /** Best-effort string field pull from [metadata] (a JsonObject) — used for banner titles etc. */
    fun metadataString(key: String): String? =
        (metadata as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.contentOrNull }
}

@Serializable
data class MindInsightsByKind(
    @SerialName("interest_cluster") val interestCluster: List<MindInsight> = emptyList(),
    @SerialName("open_loop") val openLoop: List<MindInsight> = emptyList(),
    @SerialName("attention_pattern") val attentionPattern: List<MindInsight> = emptyList(),
    @SerialName("dormant_revival") val dormantRevival: List<MindInsight> = emptyList(),
    @SerialName("inferred_goal") val inferredGoal: List<MindInsight> = emptyList(),
    @SerialName("user_model") val userModel: List<MindInsight> = emptyList(),
    val recommendation: List<MindInsight> = emptyList()
)

/** GET /api/mind/insights */
@Serializable
data class MindInsightsResponse(
    val overview: MindInsight? = null,
    val byKind: MindInsightsByKind = MindInsightsByKind(),
    val lastUpdated: String? = null
)

@Serializable
data class MindSection(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val accent: String? = null,
    val renderer: String = "insight_list",
    val position: Int = 0,
    val metadata: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** GET /api/mind/sections */
@Serializable
data class MindSectionsResponse(
    val sections: List<MindSection> = emptyList(),
    val isFallback: Boolean = false
)

@Serializable
data class MindCycleRun(
    val id: String = "",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("tokens_used") val tokensUsed: Int? = null,
    @SerialName("sections_written") val sectionsWritten: Int? = null,
    @SerialName("insights_written") val insightsWritten: Int? = null,
    val status: String = "ok",
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** GET /api/mind/cycles */
@Serializable
data class MindCyclesResponse(
    val cycles: List<MindCycleRun> = emptyList(),
    val latest: MindCycleRun? = null
)

@Serializable
data class MindTopic(
    val slug: String = "",
    @SerialName("parent_slug") val parentSlug: String? = null,
    val name: String = "",
    val cluster: String? = null,
    @SerialName("goal_name") val goalName: String? = null,
    val position: Int = 0
)

/** GET /api/mind/topics */
@Serializable
data class MindTopicsResponse(
    val topics: List<MindTopic> = emptyList(),
    val isFallback: Boolean = false
)

/** entry_type: concept|roadmap|fact|method. */
@Serializable
data class MindLibraryEntry(
    val id: String = "",
    val domain: String = "",
    @SerialName("entry_type") val entryType: String = "concept",
    val title: String = "",
    val summary: String = "",
    val metadata: JsonElement? = null,
    @SerialName("source_refs") val sourceRefs: List<SourceRef> = emptyList(),
    @SerialName("first_learned_at") val firstLearnedAt: String? = null,
    @SerialName("last_reinforced_at") val lastReinforcedAt: String? = null,
    @SerialName("cycle_count") val cycleCount: Int = 0,
    val surfaced: Boolean = false
)

/** GET /api/mind/library */
@Serializable
data class MindLibraryResponse(val entries: List<MindLibraryEntry> = emptyList())
