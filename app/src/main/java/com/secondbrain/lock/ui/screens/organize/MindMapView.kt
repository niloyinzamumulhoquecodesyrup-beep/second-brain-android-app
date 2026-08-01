package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.network.dto.NoteGraphEdge
import com.secondbrain.lock.network.dto.NoteGraphNode
import com.secondbrain.lock.network.dto.NoteGraphResponse
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SbThemeState
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.ThemeMode
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Ports components/MindMap.js field-for-field: the same minimal dependency-free force layout
 * (pairwise repulsion 2200/distSq, spring edges at 62/88px targets, 0.994 damping, 160-240
 * iterations, settled once per graph load — not re-simulated every frame), node radius scaled
 * by connection degree, MindMap's own PARA palette (which really is different from
 * lib/paraTheme.js's — project reads violet and area reads emerald *here specifically*,
 * confirmed straight from the source), fit-to-view framing, pinch/pan zoom anchored at the
 * gesture focal point, and tap-to-select with an "Open note" + "close" detail panel.
 * Dropped: the on-device title-shortening model (lib/titleShorten.js runs a local ML model in
 * the browser to trim labels to a couple words — no Android equivalent wired up, so full
 * titles are shown instead, truncated by width) and the continuous requestAnimationFrame loop
 * (Compose recomposes on state change instead, which is equivalent for a physics sim that only
 * runs once).
 * Chrome ported to the Streak Section redesign (see RewardPanel/DashboardScreen in
 * ui/screens/work/): a tinted [StreakSurface] card, no icon-chip bullet, StreakAccent eyebrow
 * title and CTA — the force-graph's own PARA node/edge palette is untouched.
 */
@Composable
fun MindMapView(onOpenNote: (String) -> Unit) {
    var graph by remember { mutableStateOf<NoteGraphResponse?>(null) }
    var selected by remember { mutableStateOf<NoteGraphNode?>(null) }
    var showAi by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        NotesRepository.getGraph().onSuccess { graph = it }
    }

    val layout = remember(graph) {
        graph?.takeIf { it.nodes.isNotEmpty() }?.let { runForceLayout(it.nodes, it.edges) }
    }

    var zoom by remember(layout) { mutableFloatStateOf(1f) }
    var offset by remember(layout) { mutableStateOf(Offset.Zero) }
    var fitZoom by remember(layout) { mutableFloatStateOf(1f) }
    var hasFitted by remember(layout) { mutableStateOf(false) }

    StreakSurface {
        val aiEdgeCount = graph?.edges?.count { it.type == "ai" } ?: 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SbSectionTitle("Mind map", color = StreakAccent)
            if (aiEdgeCount > 0) {
                Text(
                    "AI connections: ${if (showAi) "on" else "off"}",
                    color = if (showAi) StreakAccent else Mist400,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { showAi = !showAi }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("drag to pan · scroll or pinch to zoom · click a note", color = Mist400, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))

        when {
            graph == null -> Text("Loading…", color = Mist400, style = SecondBrainTypography.bodySmall)
            layout == null -> Text(
                "Capture a few notes and this fills in. A note's connections come from links you type and ones the AI notices in the embeddings.",
                color = Mist400,
                style = SecondBrainTypography.bodySmall
            )
            else -> {
                val (nodes, edges) = layout
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .pointerInput(layout) {
                            if (!hasFitted && size.width > 0 && size.height > 0) {
                                val minX = nodes.minOf { it.x - it.r }
                                val maxX = nodes.maxOf { it.x + it.r }
                                val minY = nodes.minOf { it.y - it.r }
                                val maxY = nodes.maxOf { it.y + it.r }
                                val bboxW = maxOf(40f, maxX - minX)
                                val bboxH = maxOf(40f, maxY - minY)
                                val fz = (minOf(size.width / bboxW, size.height / bboxH) * 0.85f).coerceIn(0.05f, 4f)
                                fitZoom = fz
                                zoom = fz
                                offset = Offset(
                                    size.width / 2f - (minX + bboxW / 2f) * fz,
                                    size.height / 2f - (minY + bboxH / 2f) * fz
                                )
                                hasFitted = true
                            }
                        }
                        .pointerInput(layout) {
                            detectTransformGestures { centroid, pan, zoomChange, _ ->
                                val newZoom = (zoom * zoomChange).coerceIn(fitZoom * 0.35f, fitZoom * 14f)
                                offset = Offset(
                                    centroid.x - (centroid.x - offset.x) * (newZoom / zoom),
                                    centroid.y - (centroid.y - offset.y) * (newZoom / zoom)
                                )
                                zoom = newZoom
                                offset += pan
                            }
                        }
                        .pointerInput(layout) {
                            detectTapGestures { tapOffset ->
                                val wx = (tapOffset.x - offset.x) / zoom
                                val wy = (tapOffset.y - offset.y) / zoom
                                var best: LaidNode? = null
                                var bestDist = Float.MAX_VALUE
                                nodes.forEach { n ->
                                    val d = hypot(n.x - wx, n.y - wy)
                                    if (d < maxOf(n.r, 10f) && d < bestDist) {
                                        bestDist = d
                                        best = n
                                    }
                                }
                                selected = best?.node
                            }
                        }
                ) {
                    fun toScreen(x: Float, y: Float) = Offset(x * zoom + offset.x, y * zoom + offset.y)

                    edges.forEach { e ->
                        if (e.type == "ai" && !showAi) return@forEach
                        val touchesSelection = selected != null && (e.a.node.id == selected!!.id || e.b.node.id == selected!!.id)
                        val color = mindMapParaColor(e.b.node.para)
                        val alpha = if (touchesSelection) 0.85f else if (e.type == "ai") 0.18f else 0.35f
                        drawLine(
                            color = color.copy(alpha = alpha),
                            start = toScreen(e.a.x, e.a.y),
                            end = toScreen(e.b.x, e.b.y),
                            strokeWidth = if (touchesSelection) 3f else 2f,
                            pathEffect = if (e.type == "ai") PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
                        )
                    }

                    nodes.forEach { n ->
                        val screenPos = toScreen(n.x, n.y)
                        val r = maxOf(1f, n.r * zoom)
                        val dim = selected != null && selected!!.id != n.node.id
                        val color = mindMapParaColor(n.node.para)
                        if (!dim) {
                            drawCircle(color = color.copy(alpha = 0.25f), radius = r * 1.8f, center = screenPos)
                        }
                        drawCircle(color = color.copy(alpha = if (dim) 0.35f else 0.9f), radius = r, center = screenPos)
                        if (selected?.id == n.node.id) {
                            drawCircle(
                                color = Mist100,
                                radius = r + 4f,
                                center = screenPos,
                                style = Stroke(width = 1.5f)
                            )
                        }
                    }

                    nodes.forEach { n ->
                        val screenPos = toScreen(n.x, n.y)
                        val r = n.r * zoom
                        val isSelected = selected?.id == n.node.id
                        if (r > 8f || isSelected) {
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    textSize = if (isSelected) 13f * zoom.coerceIn(0.6f, 1.4f) + 12f else 11f + 2f
                                    color = if (isSelected) Mist100.copy(alpha = 0.95f).toArgb() else Mist300.copy(alpha = 0.75f).toArgb()
                                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                                }
                                drawText(n.node.title, screenPos.x, screenPos.y + r + 16f, paint)
                            }
                        }
                    }
                }

                selected?.let { node ->
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(node.title, color = Mist100, style = SecondBrainTypography.bodyMedium)
                            val meta = node.para + if (node.tags.isNotEmpty()) " · ${node.tags.joinToString(", ")}" else ""
                            Text(meta, color = Mist400, style = SecondBrainTypography.bodySmall)
                        }
                        Row {
                            TextButton(onClick = { onOpenNote(node.id) }) { Text("Open note →", color = StreakAccent) }
                            TextButton(onClick = { selected = null }) { Text("close", color = Mist400) }
                        }
                    }
                }
            }
        }
    }
}

private data class LaidNode(val node: NoteGraphNode, var x: Float, var y: Float, var r: Float = 8f)
private data class SimEdge(val a: LaidNode, val b: LaidNode, val type: String)

private const val REPULSION = 2200f
private const val LINK_TARGET = 62f
private const val AI_TARGET = 88f
private const val LINK_SPRING = 0.07f
private const val AI_SPRING = 0.04f
private const val DAMPING = 0.994f

private fun runForceLayout(nodes: List<NoteGraphNode>, edges: List<NoteGraphEdge>): Pair<List<LaidNode>, List<SimEdge>> {
    val random = Random(nodes.size * 31 + edges.size)
    val laid = nodes.map { LaidNode(it, (random.nextFloat() - 0.5f) * 320f, (random.nextFloat() - 0.5f) * 320f) }
    val byId = laid.associateBy { it.node.id }
    val simEdges = edges.mapNotNull { e ->
        val a = byId[e.from]
        val b = byId[e.to]
        if (a != null && b != null) SimEdge(a, b, e.type) else null
    }

    val degree = HashMap<String, Int>()
    edges.forEach {
        degree[it.from] = (degree[it.from] ?: 0) + 1
        degree[it.to] = (degree[it.to] ?: 0) + 1
    }
    laid.forEach { it.r = 5f + sqrt((degree[it.node.id] ?: 0).toFloat()) * 3.2f }

    val iterations = if (laid.size > 80) 160 else 240
    repeat(iterations) {
        for (i in laid.indices) {
            for (j in i + 1 until laid.size) {
                val a = laid[i]
                val b = laid[j]
                val dx = a.x - b.x
                val dy = a.y - b.y
                val distSq = maxOf(dx * dx + dy * dy, 0.02f)
                val dist = sqrt(distSq)
                val force = REPULSION / distSq
                val fx = (dx / dist) * force
                val fy = (dy / dist) * force
                a.x += fx; a.y += fy
                b.x -= fx; b.y -= fy
            }
        }
        simEdges.forEach { e ->
            val dx = e.b.x - e.a.x
            val dy = e.b.y - e.a.y
            val distRaw = sqrt(dx * dx + dy * dy)
            val dist = if (distRaw == 0f) 0.02f else distRaw
            val target = if (e.type == "link") LINK_TARGET else AI_TARGET
            val springK = if (e.type == "link") LINK_SPRING else AI_SPRING
            val diff = (dist - target) * springK
            val fx = (dx / dist) * diff
            val fy = (dy / dist) * diff
            e.a.x += fx; e.a.y += fy
            e.b.x -= fx; e.b.y -= fy
        }
        laid.forEach { it.x *= DAMPING; it.y *= DAMPING }
    }
    return laid to simEdges
}

/**
 * MindMap.js's own PARA_RGB/PARA_RGB_LIGHT — deliberately distinct from lib/paraTheme.js's
 * mapping (project reads emerald there, violet here); ported per-source rather than unified,
 * since that's what the web actually renders.
 */
private fun mindMapParaColor(para: String): Color {
    val light = SbThemeState.mode == ThemeMode.LIGHT
    return when (para) {
        "inbox" -> if (light) Color(0xFFB8305A) else Color(0xFFFB7192)
        "project" -> if (light) Color(0xFF7C4FD1) else Color(0xFFC091FC)
        "area" -> if (light) Color(0xFF15803D) else Color(0xFF6EE796)
        "resource" -> if (light) Color(0xFFA07828) else Color(0xFFE0C07E)
        else -> if (light) Color(0xFF5A626E) else Color(0xFF94A3B8)
    }
}
