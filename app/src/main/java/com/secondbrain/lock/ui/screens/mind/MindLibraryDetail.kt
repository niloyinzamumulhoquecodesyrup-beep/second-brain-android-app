package com.secondbrain.lock.ui.screens.mind

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.secondbrain.lock.network.dto.ChartInfo
import com.secondbrain.lock.network.dto.Concept
import com.secondbrain.lock.network.dto.MindLibraryEntry
import com.secondbrain.lock.network.dto.PathInfo
import com.secondbrain.lock.network.dto.parseChart
import com.secondbrain.lock.network.dto.parseConcept
import com.secondbrain.lock.network.dto.parsePath
import com.secondbrain.lock.ui.screens.organize.SourceRefsView
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.Violet400
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Ports pages/mind.js's LibraryEntryModal: visual-first per entry_type (reuses the same
 * metadata.path/concept/chart parsers as organize/FieldInvestigationReport.kt), the
 * metadata.detail paragraphs, "known since / reinforced Nx" line, and an expandable sources
 * list. Not a pixel-for-pixel port of the web's SVG path diagram — same simplified tappable
 * list treatment organize/FieldInvestigationReport.kt already uses for that shape.
 */
@Composable
fun MindLibraryDetailDialog(entry: MindLibraryEntry, accent: Color, onOpenNote: (String) -> Unit, onDismiss: () -> Unit) {
    var showSources by remember(entry.id) { mutableStateOf(false) }
    val concept = remember(entry) { parseConcept(entry.metadata) }
    val path = remember(entry) { parsePath(entry.metadata) }
    val chart = remember(entry) { parseChart(entry.metadata) }
    val detail = remember(entry) { metadataDetail(entry.metadata) }
    val hasVisual = concept != null || path != null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Ink900)
                .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(16.dp))
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(accent))
            Column(
                Modifier
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.entryType.uppercase(), color = accent, fontSize = 11.sp, style = SecondBrainTypography.labelSmall)
                            if (!entry.surfaced) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(50))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) { Text("background research", color = Mist400, fontSize = 9.sp) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(entry.title, color = Mist100, style = SecondBrainTypography.titleLarge)
                        Text(entry.domain, color = Mist400, style = SecondBrainTypography.bodySmall)
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = Mist400, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))
                if (!hasVisual) {
                    Text(entry.summary, color = Mist100, style = SecondBrainTypography.bodyMedium)
                }
                path?.let { LibraryPathList(it) }
                concept?.let { LibraryConceptCard(it) }
                chart?.let { LibraryChartView(it) }

                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text("NOTES", color = Mist300, fontSize = 11.sp, style = SecondBrainTypography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    detail.split("\n\n").forEach { para ->
                        Text(para, color = Mist300, style = SecondBrainTypography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "known since ${formatDate(entry.firstLearnedAt)} · reinforced ${entry.cycleCount}×",
                        color = Mist400,
                        fontSize = 11.sp
                    )
                    if (entry.sourceRefs.isNotEmpty()) {
                        TextButton(onClick = { showSources = !showSources }) {
                            Text(if (showSources) "Hide sources" else "Sources (${entry.sourceRefs.size})", color = Mist400, fontSize = 11.sp)
                        }
                    }
                }
                if (showSources) SourceRefsView(entry.sourceRefs, onOpenNote)
            }
        }
    }
}

private fun formatDate(iso: String?): String {
    val instant = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "unknown"
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(instant)
}

private fun metadataDetail(metadata: JsonElement?): String? =
    ((metadata as? JsonObject)?.get("detail") as? JsonPrimitive)?.contentOrNull

@Composable
private fun LibraryConceptCard(concept: Concept) {
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Ink800.copy(alpha = 0.4f))
            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(concept.term, color = Mist100, style = SecondBrainTypography.titleLarge)
            concept.branch?.let { branch ->
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .border(BorderStroke(1.dp, Violet400.copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) { Text(branch.uppercase(), color = Violet400, fontSize = 10.sp) }
            }
        }
        concept.definition?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Mist300, style = SecondBrainTypography.bodySmall)
        }
        if (concept.philosophers.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                concept.philosophers.forEach { p ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Violet400.copy(alpha = 0.7f)))
                        Spacer(Modifier.height(4.dp))
                        Text(p.name, color = Mist100, fontSize = 11.sp, textAlign = TextAlign.Center)
                        p.era?.let { Text(it, color = Mist400, fontSize = 10.sp, textAlign = TextAlign.Center) }
                        p.contribution?.let { Text(it, color = Mist400, fontSize = 10.sp, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
        if (concept.relatedConcepts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row {
                concept.relatedConcepts.forEach { r ->
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) { Text(r, color = Mist400, fontSize = 10.sp) }
                }
            }
        }
    }
}

private fun pathNodeColor(type: String?): Color = when (type) {
    "concept" -> Violet400
    "fact" -> Emerald400
    "procedure" -> Gold500
    else -> Mist400
}

@Composable
private fun LibraryPathList(path: PathInfo) {
    var activeId by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(top = 8.dp)) {
        path.topic?.let {
            Text(it.uppercase(), color = StreakAccent, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
        }
        path.nodes.forEach { node ->
            val active = activeId == node.id
            val color = pathNodeColor(node.type)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = if (active) 0.22f else 0.1f))
                    .border(BorderStroke(1.dp, color.copy(alpha = if (active) 0.8f else 0.4f)), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(node.label, color = Mist100, fontSize = 12.sp)
                if (active) {
                    Spacer(Modifier.height(4.dp))
                    node.resourceTitle?.let { Text(it, color = Violet400, fontSize = 11.sp) }
                    node.whyThisOne?.let { Text(it, color = Mist400, fontSize = 11.sp) }
                    node.practice?.let { Text("✎ $it", color = Emerald400, fontSize = 11.sp) }
                }
            }
        }
        if (path.sequencingMode != null || path.timeline != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(path.sequencingMode?.let { "$it sequencing" }, path.timeline).joinToString(" · "),
                color = Mist400,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LibraryChartView(chart: ChartInfo) {
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Ink800.copy(alpha = 0.4f))
            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        chart.title?.let { Text(it + (chart.unit?.let { u -> " ($u)" } ?: ""), color = Mist300, fontSize = 12.sp) }
        val max = chart.bars.maxOfOrNull { it.value } ?: 1.0
        chart.bars.forEach { bar ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(bar.label, color = Mist400, fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ink600)) {
                    Box(
                        Modifier
                            .fillMaxWidth((bar.value / max).toFloat().coerceIn(0.05f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Gold500.copy(alpha = 0.7f))
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text("${bar.value}", color = Mist300, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Source: ${chart.sourceTitle ?: chart.sourceUrl ?: "cited"}", color = Mist400, fontSize = 11.sp)
    }
}
