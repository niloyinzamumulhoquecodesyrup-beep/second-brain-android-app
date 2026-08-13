package com.secondbrain.lock.ui.screens.mind

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.network.dto.MindSection
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.Sky400
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.Violet400
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class NewsItem(val text: String, val url: String?)

/** Mirrors pages/mind.js's sectionItems(): flattens the "feed" mind_sections row's metadata.items. */
fun feedItemsFrom(sections: List<MindSection>): List<NewsItem> =
    sections.filter { it.renderer == "feed" }.flatMap { section ->
        val items = (section.metadata as? JsonObject)?.get("items") as? JsonArray ?: return@flatMap emptyList()
        items.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val text = (obj["text"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            NewsItem(text, (obj["url"] as? JsonPrimitive)?.contentOrNull)
        }
    }

// Trimmed version of pages/mind.js's NEWS_DOMAIN_KEYWORDS — same four clusters as the
// Knowledge Galaxy, just enough keyword coverage to color the ticker consistently with it.
private val DOMAIN_KEYWORDS = mapOf(
    "science" to listOf("brain", "neuro", "protein", "biology", "physics", "chemistry", "gene", "cell", "species", "climate", "quantum", "space", "medicine", "disease", "vaccine", "clinical", "psycholog", "cognit", "evolution", "memory"),
    "technology" to listOf("artificial intelligence", " ai ", "software", "algorithm", "robot", "computer", "chip", " app ", "machine learning", "internet", "llm", "automation"),
    "business" to listOf("market", "startup", "finance", "econom", "stock", "investment", "revenue", "company", "ecommerce", "trade", "ipo"),
    "humanities" to listOf("philosoph", "history", "literature", "ethic", "politic", "culture", "language", " art ", "linguistic", "society", "religion")
)

private fun classifyDomainColor(text: String): Color {
    val lower = " ${text.lowercase()} "
    for ((domain, words) in DOMAIN_KEYWORDS) {
        if (words.any { lower.contains(it) }) {
            return when (domain) {
                "science" -> Emerald400
                "technology" -> Sky400
                "business" -> Rose400
                else -> Violet400
            }
        }
    }
    return Mist300
}

/**
 * Ports pages/mind.js's NewsStrip (§4j) — a single-line, always-scrolling "Latest in your
 * world" ticker fed by the cycle-authored "feed" mind_sections row. Web pauses on hover;
 * that gesture doesn't exist on touch, so this just loops continuously.
 */
@Composable
fun MindNewsTicker(items: List<NewsItem>) {
    if (items.isEmpty()) return
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(items) {
        if (items.size <= 1) return@LaunchedEffect
        while (isActive) {
            val max = scrollState.maxValue
            if (max <= 0) {
                kotlinx.coroutines.delay(300)
                continue
            }
            scrollState.animateScrollTo(max, animationSpec = tween(durationMillis = max * 10, easing = LinearEasing))
            scrollState.scrollTo(0)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink900)
            .border(androidx.compose.foundation.BorderStroke(1.dp, Ink600), RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(StreakAccent.copy(alpha = 0.1f))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text("LATEST", color = Mist300, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(vertical = 8.dp)
        ) {
            items.forEach { item ->
                val color = classifyDomainColor(item.text)
                val modifier = if (item.url != null) {
                    Modifier.clickable { uriHandler.openUri(item.url) }
                } else Modifier
                Text(
                    item.text + (if (item.url != null) " ↗" else ""),
                    color = color,
                    fontSize = 13.sp,
                    modifier = modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }
}
