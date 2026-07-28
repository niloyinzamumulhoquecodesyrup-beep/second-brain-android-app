package com.secondbrain.lock.network.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Loose, best-effort readers over a `recommendation`/`inferred_goal` insight's opaque
 * `metadata` JSON — mirrors what components/InsightCards.js's RecommendationCardBody reads
 * out of `insight.metadata` (path/concept/chart/suggestion/keywords_used/icon). Not modeled
 * as strict @Serializable classes since the shape genuinely varies per insight.
 */

data class ConceptPhilosopher(val name: String, val era: String?, val contribution: String?)

data class Concept(
    val term: String,
    val branch: String?,
    val definition: String?,
    val philosophers: List<ConceptPhilosopher>,
    val relatedConcepts: List<String>
)

data class PathNode(
    val id: String,
    val label: String,
    val type: String?,
    val requires: List<String>,
    val resourceTitle: String?,
    val resourceUrl: String?,
    val whyThisOne: String?,
    val practice: String?
)

data class PathInfo(
    val topic: String?,
    val nodes: List<PathNode>,
    val sequencingMode: String?,
    val timeline: String?
)

data class ChartBar(val label: String, val value: Double)

data class ChartInfo(
    val title: String?,
    val unit: String?,
    val bars: List<ChartBar>,
    val sourceTitle: String?,
    val sourceUrl: String?
)

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.double(): Double? = (this as? JsonPrimitive)?.doubleOrNull

fun parseConcept(metadata: JsonElement?): Concept? {
    val c = metadata.obj()?.get("concept").obj() ?: return null
    val term = c["term"].str() ?: return null
    val philosophers = c["philosophers"].arr()?.mapNotNull { p ->
        val po = p as? JsonObject ?: return@mapNotNull null
        val name = po["name"].str() ?: return@mapNotNull null
        ConceptPhilosopher(name, po["era"].str(), po["contribution"].str())
    }.orEmpty().take(4)
    val related = c["related_concepts"].arr()?.mapNotNull { it.str() }.orEmpty().take(3)
    return Concept(term, c["branch"].str(), c["definition"].str(), philosophers, related)
}

fun parsePath(metadata: JsonElement?): PathInfo? {
    val p = metadata.obj()?.get("path").obj() ?: return null
    val nodesArr = p["nodes"].arr() ?: return null
    if (nodesArr.isEmpty()) return null
    val nodes = nodesArr.mapNotNull { n ->
        val no = n as? JsonObject ?: return@mapNotNull null
        val id = no["id"].str() ?: return@mapNotNull null
        val label = no["label"].str() ?: id
        val requires = no["requires"].arr()?.mapNotNull { it.str() }.orEmpty()
        val resource = no["resource"].obj()
        PathNode(
            id = id,
            label = label,
            type = no["type"].str(),
            requires = requires,
            resourceTitle = resource?.get("title").str(),
            resourceUrl = resource?.get("url").str(),
            whyThisOne = resource?.get("why_this_one").str(),
            practice = no["practice"].str()
        )
    }
    if (nodes.isEmpty()) return null
    return PathInfo(p["topic"].str(), nodes, p["sequencing_mode"].str(), p["timeline"].str())
}

/** Matches the web's rule: a chart only renders when it carries a cited source. */
fun parseChart(metadata: JsonElement?): ChartInfo? {
    val ch = metadata.obj()?.get("chart").obj() ?: return null
    val source = ch["source"].obj() ?: return null
    val bars = ch["bars"].arr()?.mapNotNull { b ->
        val bo = b as? JsonObject ?: return@mapNotNull null
        val label = bo["label"].str() ?: return@mapNotNull null
        val value = bo["value"].double() ?: return@mapNotNull null
        ChartBar(label, value)
    }.orEmpty()
    if (bars.isEmpty()) return null
    return ChartInfo(ch["title"].str(), ch["unit"].str(), bars, source["title"].str(), source["url"].str())
}

fun metadataIcon(metadata: JsonElement?): String? = metadata.obj()?.get("icon").str()
fun metadataSuggestion(metadata: JsonElement?): String? = metadata.obj()?.get("suggestion").str()
fun metadataKeywords(metadata: JsonElement?): List<String> =
    metadata.obj()?.get("keywords_used").arr()?.mapNotNull { it.str() }.orEmpty()
