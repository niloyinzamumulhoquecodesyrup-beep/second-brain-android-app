package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.network.dto.ChartInfo
import com.secondbrain.lock.network.dto.Concept
import com.secondbrain.lock.network.dto.MindInsight
import com.secondbrain.lock.network.dto.PathInfo
import com.secondbrain.lock.network.dto.PathNode
import com.secondbrain.lock.network.dto.metadataIcon
import com.secondbrain.lock.network.dto.metadataKeywords
import com.secondbrain.lock.network.dto.metadataSuggestion
import com.secondbrain.lock.network.dto.parseChart
import com.secondbrain.lock.network.dto.parseConcept
import com.secondbrain.lock.network.dto.parsePath
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.Violet400
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ports components/InsightCards.js's RecommendationCard / RecommendationCardBody — a
 * SEPARATE card from Inferred Goals, one recommendation at a time with a "‹ i / N ›" pager.
 * Renders a ConceptCard, a simplified PathDiagram (a tappable ordered list instead of the
 * web's 2D dependency graph — flagged), or a MiniBarChart depending on what the insight's
 * metadata carries, else a plain icon+summary. Matches the "For you:", "researched via:",
 * and collapsible Sources rows exactly.
 * Chrome ported to the Streak Section redesign: a tinted [StreakSurface] card, no icon-chip
 * bullet, StreakAccent eyebrow title — Gold500/Violet400/Emerald400 stay put where they carry
 * content meaning (path-node type, concept branch tag, "For you" suggestion).
 */
@Composable
fun FieldInvestigationReport(recommendations: List<MindInsight>, onOpenNote: (String) -> Unit) {
    if (recommendations.isEmpty()) return
    var page by remember(recommendations) { mutableIntStateOf(0) }
    val index = page.coerceIn(0, recommendations.size - 1)
    val insight = recommendations[index]

    StreakSurface {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SbSectionTitle("Field investigation report", color = StreakAccent)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = (index - 1 + recommendations.size) % recommendations.size }, modifier = Modifier.size(26.dp)) {
                    Text("‹", color = Mist300, fontSize = 18.sp)
                }
                Text("${index + 1} / ${recommendations.size}", color = Mist400, style = SecondBrainTypography.bodySmall)
                IconButton(onClick = { page = (index + 1) % recommendations.size }, modifier = Modifier.size(26.dp)) {
                    Text("›", color = Mist300, fontSize = 18.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        RecommendationBody(insight, onOpenNote)
    }
}

@Composable
private fun RecommendationBody(insight: MindInsight, onOpenNote: (String) -> Unit) {
    var showSources by remember(insight.id) { mutableStateOf(false) }
    val concept = remember(insight) { parseConcept(insight.metadata) }
    val path = remember(insight) { parsePath(insight.metadata) }
    val chart = remember(insight) { parseChart(insight.metadata) }
    val suggestion = remember(insight) { metadataSuggestion(insight.metadata) }
    val keywords = remember(insight) { metadataKeywords(insight.metadata) }
    val icon = remember(insight) { metadataIcon(insight.metadata) }
    val hasVisual = concept != null || path != null

    Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
        if (!hasVisual) {
            Row {
                icon?.let {
                    Text(it, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(insight.summary, color = Mist100, style = SecondBrainTypography.bodyMedium)
            }
        }
        path?.let { PathList(it) }
        concept?.let { ConceptCardView(it) }
        chart?.let { ChartView(it) }

        suggestion?.let {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Violet400.copy(alpha = 0.05f))
                    .border(BorderStroke(1.dp, Violet400.copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row {
                    Text("For you: ", color = Violet400, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(it, color = Mist100, fontSize = 12.sp)
                }
            }
        }
        if (keywords.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("researched via: ${keywords.joinToString(" · ")}", color = Mist400, fontSize = 12.sp)
        }
        if (insight.sourceRefs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showSources = !showSources }, contentPadding = PaddingValues(0.dp)) {
                Text(
                    if (showSources) "Hide sources" else "Sources (${insight.sourceRefs.size})",
                    color = Mist400,
                    fontSize = 12.sp
                )
            }
            if (showSources) SourceRefsView(insight.sourceRefs, onOpenNote)
        }
    }
}

@Composable
private fun ConceptCardView(concept: Concept) {
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

private fun pathNodeColor(type: String?) = when (type) {
    "concept" -> Violet400
    "fact" -> Emerald400
    "procedure" -> Gold500
    else -> Mist400
}

/**
 * levelOf(id) = 0 for a node with no (resolvable) requires, else 1 + the deepest of its
 * requirements' levels — same longest-path-from-root rule as PathDiagram.js's `levelOf`,
 * including its cycle guard (a `seen` set breaks a malformed cyclic `requires` at 0).
 */
private fun computePathLevels(nodes: List<PathNode>): Map<String, Int> {
    val byId = nodes.associateBy { it.id }
    val level = HashMap<String, Int>()
    fun levelOf(id: String, seen: Set<String>): Int {
        level[id]?.let { return it }
        if (id in seen) return 0
        val reqs = byId[id]?.requires.orEmpty().filter { byId.containsKey(it) }
        val l = if (reqs.isEmpty()) 0 else 1 + reqs.maxOf { levelOf(it, seen + id) }
        level[id] = l
        return l
    }
    nodes.forEach { levelOf(it.id, emptySet()) }
    return level
}

/** Wraps each dependency level into one or more grid rows of at most [maxPerRow] nodes. */
private fun buildVisualRows(nodes: List<PathNode>, levels: Map<String, Int>, maxPerRow: Int = 3): List<List<PathNode>> {
    val byLevel = nodes.groupBy { levels[it.id] ?: 0 }.toSortedMap()
    val rows = mutableListOf<List<PathNode>>()
    byLevel.forEach { (_, rowNodes) -> rowNodes.chunked(maxPerRow).forEach { rows.add(it) } }
    return rows
}

private data class NodePos(val x: Dp, val y: Dp)

/** Greedy word-wrap to at most [maxLines] lines, ellipsizing the last line on overflow. */
private fun wrapLabel(text: String, maxWidthPx: Float, paint: android.graphics.Paint, maxLines: Int = 2): List<String> {
    val avail = (maxWidthPx - 10f).coerceAtLeast(10f)
    val words = text.split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return listOf("")
    val allLines = mutableListOf<String>()
    var current = ""
    for (word in words) {
        val trial = if (current.isEmpty()) word else "$current $word"
        if (current.isEmpty() || paint.measureText(trial) <= avail) {
            current = trial
        } else {
            allLines.add(current)
            current = word
        }
    }
    if (current.isNotEmpty()) allLines.add(current)
    if (allLines.size <= maxLines) return allLines

    val visible = allLines.take(maxLines).toMutableList()
    var last = visible[maxLines - 1]
    while (last.isNotEmpty() && paint.measureText("$last…") > avail) last = last.dropLast(1)
    visible[maxLines - 1] = "$last…"
    return visible
}

/**
 * Ports PathDiagram.js as a genuine layered dependency graph: nodes are grouped into rows by
 * `levelOf` (longest path from a root with no `requires`), centered per row, and connected by
 * arrows from each requirement to what needs it — the same structure as the web's SVG graph
 * (dependency levels -> rows, `requires` -> edges), drawn on a Canvas rather than in <svg>/
 * foreignObject. Simplified: straight-line edges instead of the web's cubic-bezier "S" curves,
 * and labels are manually word-wrapped/ellipsized instead of a flexbox foreignObject. Tapping a
 * node still opens the same resource/practice detail panel as before.
 */
@Composable
private fun PathList(path: PathInfo) {
    var activeId by remember(path) { mutableStateOf<String?>(null) }
    val nodes = path.nodes
    val levels = remember(nodes) { computePathLevels(nodes) }
    val rows = remember(nodes, levels) { buildVisualRows(nodes, levels) }

    Column(Modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        path.topic?.let {
            Text(it.uppercase(), color = StreakAccent, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(6.dp))
        }

        if (rows.isEmpty()) return@Column

        val nodeW = 92.dp
        val nodeH = 46.dp
        val gapX = 10.dp
        val gapY = 22.dp
        val maxCols = rows.maxOf { it.size }
        val canvasWidth = nodeW * maxCols + gapX * (maxCols - 1).coerceAtLeast(0)
        val canvasHeight = nodeH * rows.size + gapY * (rows.size - 1).coerceAtLeast(0) + 8.dp

        val positions = remember(rows) {
            val map = HashMap<String, NodePos>()
            rows.forEachIndexed { ri, row ->
                val rowWidth = nodeW * row.size + gapX * (row.size - 1).coerceAtLeast(0)
                var x = (canvasWidth - rowWidth) / 2
                val y = (nodeH + gapY) * ri
                row.forEach { n ->
                    map[n.id] = NodePos(x, y)
                    x += nodeW + gapX
                }
            }
            map
        }

        Canvas(
            modifier = Modifier
                .width(canvasWidth)
                .height(canvasHeight)
                .pointerInput(nodes) {
                    detectTapGestures { tap ->
                        val w = nodeW.toPx()
                        val h = nodeH.toPx()
                        val hit = nodes.firstOrNull { n ->
                            val p = positions[n.id] ?: return@firstOrNull false
                            val left = p.x.toPx()
                            val top = p.y.toPx()
                            tap.x in left..(left + w) && tap.y in top..(top + h)
                        }
                        activeId = if (hit != null && hit.id == activeId) null else hit?.id
                    }
                }
        ) {
            val wPx = nodeW.toPx()
            val hPx = nodeH.toPx()

            nodes.forEach { n ->
                val to = positions[n.id] ?: return@forEach
                n.requires.forEach { reqId ->
                    val from = positions[reqId] ?: return@forEach
                    val x1 = from.x.toPx() + wPx / 2f
                    val y1 = from.y.toPx() + hPx
                    val x2 = to.x.toPx() + wPx / 2f
                    val y2 = to.y.toPx()
                    drawLine(Mist400.copy(alpha = 0.55f), Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.6f)
                    val angle = atan2(y2 - y1, x2 - x1)
                    val ah = 6.dp.toPx()
                    val a1 = Offset(x2 - ah * cos(angle - 0.5f), y2 - ah * sin(angle - 0.5f))
                    val a2 = Offset(x2 - ah * cos(angle + 0.5f), y2 - ah * sin(angle + 0.5f))
                    drawLine(Mist400.copy(alpha = 0.55f), Offset(x2, y2), a1, strokeWidth = 1.6f)
                    drawLine(Mist400.copy(alpha = 0.55f), Offset(x2, y2), a2, strokeWidth = 1.6f)
                }
            }

            nodes.forEach { n ->
                val p = positions[n.id] ?: return@forEach
                val active = activeId == n.id
                val color = pathNodeColor(n.type)
                val left = p.x.toPx()
                val top = p.y.toPx()
                drawRoundRect(
                    color = color.copy(alpha = if (active) 0.28f else 0.12f),
                    topLeft = Offset(left, top),
                    size = Size(wPx, hPx),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
                drawRoundRect(
                    color = color.copy(alpha = if (active) 0.9f else 0.5f),
                    topLeft = Offset(left, top),
                    size = Size(wPx, hPx),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = if (active) 2f else 1.5f)
                )
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 10.sp.toPx()
                        this.color = Mist100.toArgb()
                    }
                    val lines = wrapLabel(n.label, wPx, paint)
                    val lineHeight = paint.textSize * 1.2f
                    val startY = top + hPx / 2f - (lines.size - 1) * lineHeight / 2f + paint.textSize / 3f
                    lines.forEachIndexed { li, line ->
                        drawText(line, left + wPx / 2f, startY + li * lineHeight, paint)
                    }
                }
            }
        }

        val active = nodes.firstOrNull { it.id == activeId }
        if (active != null) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(active.label, color = Mist100, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                active.resourceTitle?.let { Text(it, color = Violet400, fontSize = 11.sp) }
                active.whyThisOne?.let { Text(it, color = Mist400, fontSize = 11.sp) }
                active.practice?.let { Text("✎ $it", color = Emerald400, fontSize = 11.sp) }
            }
        }

        if (path.sequencingMode != null || path.timeline != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(path.sequencingMode?.let { "$it sequencing" }, path.timeline).joinToString(" · "),
                color = Mist400,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Start)
            )
        }
    }
}

@Composable
private fun ChartView(chart: ChartInfo) {
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
                Text(bar.label, color = Mist400, fontSize = 11.sp, modifier = Modifier.width(72.dp))
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
