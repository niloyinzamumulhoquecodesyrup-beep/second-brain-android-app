package com.secondbrain.lock.ui.screens.mind

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.network.dto.MindInsight
import com.secondbrain.lock.network.dto.MindLibraryEntry
import com.secondbrain.lock.network.dto.MindTopic
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.Sky400
import com.secondbrain.lock.ui.theme.Violet400
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Simplified port of components/KnowledgeGalaxy.js: a force-directed layout of the mind_topics
 * taxonomy tree (same minimal dependency-free physics as organize/MindMapView.kt, adapted for
 * parent/child edges instead of note links), lit where a leaf's name join-matches an
 * inferred_goal's metadata.name or a knowledge-library entry's title, pan/pinch-zoom, tap to
 * select. Dropped vs. the web: the heat-gradient overlay toggle and the always-on star-field
 * background — cosmetic, not load-bearing.
 */
@Composable
fun MindKnowledgeGalaxy(
    topics: List<MindTopic>,
    goals: List<MindInsight>,
    library: List<MindLibraryEntry>,
    onOpenNote: (String) -> Unit
) {
    val layout = remember(topics, goals, library) {
        if (topics.isEmpty()) null else runGalaxyLayout(topics, goals, library)
    }

    // Keyed by `layout` (not just Unit) so a data refresh can't leave `selected` pointing at a
    // node instance from the previous list — same reset-on-relayout pattern as zoom/offset below.
    var selected by remember(layout) { mutableStateOf<GalaxyNode?>(null) }
    var zoom by remember(layout) { mutableFloatStateOf(1f) }
    var offset by remember(layout) { mutableStateOf(Offset.Zero) }
    var fitZoom by remember(layout) { mutableFloatStateOf(1f) }
    var hasFitted by remember(layout) { mutableStateOf(false) }

    SbCard(topBorderColor = Violet400) {
        SbLabel("Interest cluster", color = Violet400)
        Spacer(Modifier.height(4.dp))
        Text("drag to pan · pinch to zoom · tap a lit node", color = Mist400, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))

        if (layout == null) {
            Text(
                "No topic map yet — this fills in once a mind cycle runs.",
                color = Mist400,
                style = SecondBrainTypography.bodySmall
            )
            return@SbCard
        }
        val nodes = layout

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .pointerInput(layout) {
                    if (!hasFitted && size.width > 0 && size.height > 0) {
                        // Lit nodes draw a name label below them (and a wider halo ring) that
                        // extends well past their circle radius — a bbox built from `r` alone
                        // fits the circles but leaves labels of edge nodes clipped outside the
                        // canvas, which read as "nodes missing" even though they're technically
                        // laid out. Pad each node's extent to cover the label/halo before fitting.
                        fun xExtent(n: GalaxyNode) = if (n.lit) maxOf(n.r * 1.7f, 70f) else n.r
                        fun yExtent(n: GalaxyNode) = if (n.lit) n.r * 1.7f + 24f else n.r
                        val minX = nodes.minOf { it.x - xExtent(it) }
                        val maxX = nodes.maxOf { it.x + xExtent(it) }
                        val minY = nodes.minOf { it.y - yExtent(it) }
                        val maxY = nodes.maxOf { it.y + yExtent(it) }
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
                        val newZoom = (zoom * zoomChange).coerceIn(fitZoom * 0.25f, fitZoom * 10f)
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
                        var best: GalaxyNode? = null
                        var bestDist = Float.MAX_VALUE
                        nodes.forEach { n ->
                            val d = hypot(n.x - wx, n.y - wy)
                            if (n.lit && d < maxOf(n.r, 12f) && d < bestDist) {
                                bestDist = d
                                best = n
                            }
                        }
                        selected = best
                    }
                }
        ) {
            fun toScreen(x: Float, y: Float) = Offset(x * zoom + offset.x, y * zoom + offset.y)

            nodes.forEach { n ->
                n.parent?.let { parent ->
                    drawLine(
                        color = Mist400.copy(alpha = if (n.lit || parent.lit) 0.28f else 0.12f),
                        start = toScreen(parent.x, parent.y),
                        end = toScreen(n.x, n.y),
                        strokeWidth = 1.5f
                    )
                }
            }

            nodes.forEach { n ->
                val screenPos = toScreen(n.x, n.y)
                val r = maxOf(1f, n.r * zoom)
                val dim = selected != null && selected !== n
                val color = clusterColor(n.topic.cluster)
                val alpha = when {
                    !n.lit -> if (dim) 0.15f else 0.3f
                    dim -> 0.4f
                    else -> 0.9f
                }
                if (n.lit) {
                    drawCircle(color = color.copy(alpha = alpha * 0.35f), radius = r * 1.7f, center = screenPos)
                }
                drawCircle(color = color.copy(alpha = alpha), radius = r, center = screenPos)
                if (selected === n) {
                    drawCircle(color = Mist100, radius = r + 4f, center = screenPos, style = Stroke(width = 1.5f))
                }
            }

            nodes.forEach { n ->
                val screenPos = toScreen(n.x, n.y)
                val r = n.r * zoom
                val isSelected = selected === n
                if ((n.lit && r > 6f) || isSelected) {
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = if (isSelected) 13f * zoom.coerceIn(0.6f, 1.4f) + 12f else 20f
                            color = if (isSelected) Mist100.copy(alpha = 0.95f).toArgb() else Mist300.copy(alpha = 0.85f).toArgb()
                            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                        }
                        drawText(n.topic.name, screenPos.x, screenPos.y + r + 16f, paint)
                    }
                }
            }
        }

        selected?.let { node ->
            Spacer(Modifier.height(10.dp))
            GalaxyDetailPanel(node, onOpenNote, onClose = { selected = null })
        }
    }
}

@Composable
private fun GalaxyDetailPanel(node: GalaxyNode, onOpenNote: (String) -> Unit, onClose: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(node.topic.name, color = Mist100, style = SecondBrainTypography.bodyMedium)
                val meta = when {
                    node.goal != null -> "${node.goal.sourceRefs.size.coerceAtLeast(1)} note${if (node.goal.sourceRefs.size == 1) "" else "s"}"
                    node.libraryEntry != null -> "explored ${node.libraryEntry.cycleCount}× via field investigation"
                    else -> "no evidence yet"
                }
                Text(meta, color = Mist400, style = SecondBrainTypography.bodySmall)
            }
            TextButton(onClick = onClose) { Text("close", color = Mist400, fontSize = 11.sp) }
        }
        val summary = node.goal?.summary ?: node.libraryEntry?.summary
        if (!summary.isNullOrBlank()) {
            Text(summary, color = Mist300, style = SecondBrainTypography.bodySmall)
        }
        node.goal?.sourceRefs?.filter { it.type == "note" }?.forEach { ref ->
            val id = ref.id
            Text(
                "↳ ${ref.title ?: "note"}",
                color = Emerald400,
                fontSize = 11.sp,
                modifier = if (id != null) Modifier.clickable { onOpenNote(id) } else Modifier
            )
        }
    }
}

private fun clusterColor(cluster: String?): Color = when (cluster) {
    "science" -> Emerald400
    "technology" -> Sky400
    "business" -> Rose400
    "humanities" -> Violet400
    else -> Mist400
}

class GalaxyNode(
    val topic: MindTopic,
    var x: Float,
    var y: Float,
    var r: Float,
    val lit: Boolean,
    val goal: MindInsight?,
    val libraryEntry: MindLibraryEntry?,
    var parent: GalaxyNode?
)

private const val REPULSION = 1600f
private const val EDGE_TARGET = 52f
private const val EDGE_SPRING = 0.07f
private const val DAMPING = 0.992f

/** Builds nodes (lit/heat via goalName + library-title join, mirroring KnowledgeGalaxy.js) then
 * runs the same pairwise-repulsion/spring-edge layout as MindMapView.kt over parent/child edges. */
private fun runGalaxyLayout(topics: List<MindTopic>, goals: List<MindInsight>, library: List<MindLibraryEntry>): List<GalaxyNode> {
    val goalByName = goals.mapNotNull { g -> g.metadataString("name")?.let { it to g } }.toMap()
    val libByTitle = library.associateBy { it.title.lowercase() }

    val random = Random(topics.size * 31)
    val nodes = topics.map { topic ->
        val goal = topic.goalName?.let { goalByName[it] }
        val libEntry = libByTitle[topic.name.lowercase()]
        val lit = goal != null || libEntry != null
        val hasChildren = topics.any { it.parentSlug == topic.slug }
        val radius = when {
            hasChildren -> 12f
            goal != null -> 8f + (goal.sourceRefs.size.coerceAtMost(6)) * 1.5f
            libEntry != null -> 7f
            else -> 4f
        }
        GalaxyNode(
            topic = topic,
            x = (random.nextFloat() - 0.5f) * 280f,
            y = (random.nextFloat() - 0.5f) * 280f,
            r = radius,
            lit = lit,
            goal = goal,
            libraryEntry = libEntry,
            parent = null
        )
    }
    val bySlug = nodes.associateBy { it.topic.slug }
    nodes.forEach { it.parent = it.topic.parentSlug?.let { p -> bySlug[p] } }
    val edges = nodes.mapNotNull { n -> n.parent?.let { p -> n to p } }

    val iterations = if (nodes.size > 60) 160 else 220
    repeat(iterations) {
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
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
        edges.forEach { (n, p) ->
            val dx = p.x - n.x
            val dy = p.y - n.y
            val distRaw = sqrt(dx * dx + dy * dy)
            val dist = if (distRaw == 0f) 0.02f else distRaw
            val diff = (dist - EDGE_TARGET) * EDGE_SPRING
            val fx = (dx / dist) * diff
            val fy = (dy / dist) * diff
            n.x += fx; n.y += fy
            p.x -= fx; p.y -= fy
        }
        nodes.forEach { it.x *= DAMPING; it.y *= DAMPING }
    }
    return nodes
}
