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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.network.dto.NoteGraphEdge
import com.secondbrain.lock.network.dto.NoteGraphNode
import com.secondbrain.lock.network.dto.NoteGraphResponse
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SbThemeState
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.ThemeMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Coggle-style radial mind map: a central "Notes" bubble, one curved colored branch per PARA
 * category (Inbox/Projects/Areas/Resources/Archive), and each note as a leaf twig off its
 * category's branch — swapped in for the old force-directed hairball (components/MindMap.js's
 * physics sim), which read as a dense dot cluster rather than a map you could actually read.
 * The underlying note-to-note graph (typed links + AI-inferred connections) is kept as thin
 * cross-link curves drawn under the branch tree, toggleable via the same "AI connections" switch
 * as before. Branch curves taper (thick trunk near center, thin at the leaf) by sampling the
 * cubic bezier and drawing round-capped segments of decreasing width — Compose's Canvas has no
 * native variable-width stroke. Pinch/pan zoom, fit-to-view framing, and tap-to-select with an
 * "Open note" detail panel are carried over unchanged from the force-layout version.
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
        graph?.takeIf { it.nodes.isNotEmpty() }?.let { runRadialLayout(it.nodes, it.edges) }
    }

    var zoom by remember(layout) { mutableFloatStateOf(1f) }
    var offset by remember(layout) { mutableStateOf(Offset.Zero) }
    var fitZoom by remember(layout) { mutableFloatStateOf(1f) }
    var hasFitted by remember(layout) { mutableStateOf(false) }
    var canvasSize by remember(layout) { mutableStateOf(IntSize.Zero) }

    // Driven by onSizeChanged + LaunchedEffect rather than a one-shot check inside pointerInput:
    // the pointerInput coroutine can start before the Canvas's first layout pass reports a real
    // size, and with no retry that left zoom/offset stuck at their unfitted defaults (content
    // drawn at raw local-unit scale, overflowing the card entirely) on an unlucky composition.
    LaunchedEffect(layout, canvasSize) {
        val l = layout
        if (l != null && !hasFitted && canvasSize.width > 0 && canvasSize.height > 0) {
            val bounds = computeContentBounds(l.nodes, l.branches)
            val bboxW = maxOf(40f, bounds.maxX - bounds.minX)
            val bboxH = maxOf(40f, bounds.maxY - bounds.minY)
            val fz = (minOf(canvasSize.width / bboxW, canvasSize.height / bboxH) * 0.88f).coerceIn(0.05f, 4f)
            fitZoom = fz
            zoom = fz
            offset = Offset(
                canvasSize.width / 2f - (bounds.minX + bboxW / 2f) * fz,
                canvasSize.height / 2f - (bounds.minY + bboxH / 2f) * fz
            )
            hasFitted = true
        }
    }

    StreakSurface {
        val aiEdgeCount = graph?.edges?.count { it.type == "ai" } ?: 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SbSectionTitle("Notes map", color = StreakAccent)
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
        Text("drag to pan · scroll or pinch to zoom · tap a note", color = Mist400, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))

        when {
            graph == null -> Text("Loading…", color = Mist400, style = SecondBrainTypography.bodySmall)
            layout == null -> Text(
                "Capture a few notes and this fills in. A note's connections come from links you type and ones the AI notices in the embeddings.",
                color = Mist400,
                style = SecondBrainTypography.bodySmall
            )
            else -> {
                val (nodes, branches, crossLinks) = layout
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .onSizeChanged { canvasSize = it }
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
                                // Hit-tested in screen space against the label's own bounding box
                                // rather than a small world-space circle around the bare node
                                // point: leaves have no visible dot marker (Coggle doesn't either),
                                // so the tap target a user actually aims for is the text itself,
                                // which sits offset from — and can extend well past — that point.
                                var best: RadialNode? = null
                                var bestDist = Float.MAX_VALUE
                                nodes.forEach { n ->
                                    if (n.depth != 2) return@forEach
                                    val screenX = n.x * zoom + offset.x
                                    val screenY = n.y * zoom + offset.y
                                    val dirX = cos(n.angle)
                                    val dirY = sin(n.angle)
                                    val labelGap = 7f * zoom
                                    val fontSize = 11f * zoom.coerceIn(0.5f, 1.6f) + 1f
                                    val textWidth = n.label.length * fontSize * 0.55f
                                    val anchorX = screenX + dirX * labelGap
                                    val anchorY = screenY + dirY * labelGap
                                    val left = if (dirX >= 0) anchorX - 12f else anchorX - textWidth - 12f
                                    val right = if (dirX >= 0) anchorX + textWidth + 12f else anchorX + 12f
                                    val top = anchorY - fontSize - 10f
                                    val bottom = anchorY + 10f
                                    if (tapOffset.x in left..right && tapOffset.y in top..bottom) {
                                        val d = hypot(anchorX - tapOffset.x, anchorY - tapOffset.y)
                                        if (d < bestDist) {
                                            bestDist = d
                                            best = n
                                        }
                                    }
                                }
                                selected = best?.note
                            }
                        }
                ) {
                    fun toScreen(x: Float, y: Float) = Offset(x * zoom + offset.x, y * zoom + offset.y)

                    // Cross-links (typed + AI-inferred note connections) sit under the branch tree.
                    crossLinks.forEach { link ->
                        if (link.type == "ai" && !showAi) return@forEach
                        val touchesSelection = selected != null &&
                            (link.a.note?.id == selected!!.id || link.b.note?.id == selected!!.id)
                        val alpha = if (touchesSelection) 0.7f else if (link.type == "ai") 0.14f else 0.22f
                        drawLine(
                            color = link.a.color.copy(alpha = alpha),
                            start = toScreen(link.a.x, link.a.y),
                            end = toScreen(link.b.x, link.b.y),
                            strokeWidth = if (touchesSelection) 2.5f else 1.4f,
                            pathEffect = if (link.type == "ai") PathEffect.dashPathEffect(floatArrayOf(5f, 5f)) else null
                        )
                    }

                    // Radial branch tree: root -> category -> leaf, curved and tapered.
                    branches.forEach { br ->
                        val parentPos = Offset(br.parent.x, br.parent.y)
                        val childPos = Offset(br.child.x, br.child.y)
                        // Root has no inherent direction of its own, so it launches tangent to
                        // the child's angle instead — reads as a clean radial spray from center.
                        val parentAngle = if (br.parent.depth == 0) br.child.angle else br.parent.angle
                        val (c1, c2) = branchControlPoints(parentPos, parentAngle, childPos, br.child.angle)
                        val isTrunk = br.parent.depth == 0
                        val widthStart = if (isTrunk) 6.5f else 3f
                        val widthEnd = if (isTrunk) 3f else 1.1f
                        val alpha = when {
                            isTrunk -> 0.9f
                            selected == null -> 0.85f
                            br.child.note?.id == selected!!.id -> 1f
                            else -> 0.25f
                        }
                        drawTaperedBranch(
                            p0 = parentPos, p1 = c1, p2 = c2, p3 = childPos,
                            color = br.child.color, alpha = alpha,
                            widthStart = widthStart, widthEnd = widthEnd,
                            zoom = zoom, toScreen = ::toScreen
                        )
                    }

                    drawRootBubble(toScreen(0f, 0f), zoom)

                    nodes.forEach { n ->
                        if (n.depth == 0) return@forEach
                        val screenPos = toScreen(n.x, n.y)
                        val isSelectedLeaf = n.depth == 2 && selected?.id == n.note?.id
                        val dim = n.depth == 2 && selected != null && !isSelectedLeaf
                        drawRadialLabel(n, screenPos, zoom, dim, isSelectedLeaf)
                        if (isSelectedLeaf) {
                            drawCircle(color = Mist100, radius = 5f * zoom, center = screenPos, style = Stroke(width = 1.5f))
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

/** A black drop shadow makes light text pop off a dark background, but the same shadow reads as
 * a grey smudge around already-dark text on the light theme's pale card — so the shadow itself
 * (not just its color) is dark-mode-only rather than swapping to a "light" shadow color. */
private fun applyLabelShadow(paint: android.graphics.Paint, radius: Float) {
    if (SbThemeState.mode == ThemeMode.LIGHT) {
        paint.clearShadowLayer()
    } else {
        paint.setShadowLayer(radius, 0f, 0f, android.graphics.Color.BLACK)
    }
}

private fun DrawScope.drawRootBubble(screenPos: Offset, zoom: Float) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 13f * zoom.coerceIn(0.6f, 1.6f) + 3f
        color = Mist100.toArgb()
        applyLabelShadow(this, 3f)
    }
    val textWidth = paint.measureText(ROOT_LABEL)
    val w = textWidth + 28f * zoom
    val h = 30f * zoom
    drawRoundRect(
        color = Ink800,
        topLeft = Offset(screenPos.x - w / 2f, screenPos.y - h / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f, h / 2f)
    )
    drawRoundRect(
        color = Ink600,
        topLeft = Offset(screenPos.x - w / 2f, screenPos.y - h / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f, h / 2f),
        style = Stroke(width = 1.2f)
    )
    drawContext.canvas.nativeCanvas.drawText(ROOT_LABEL, screenPos.x, screenPos.y + h * 0.16f, paint)
}

/** Category and leaf labels sit beside the branch tip, flowing left or right of center like a
 * real mind map so text never runs back over its own branch. */
private fun DrawScope.drawRadialLabel(n: RadialNode, screenPos: Offset, zoom: Float, dim: Boolean, isSelected: Boolean) {
    val isCategory = n.depth == 1
    val dirX = cos(n.angle)
    val dirY = sin(n.angle)
    val labelGap = (if (isCategory) 10f else 7f) * zoom
    val anchorX = screenPos.x + dirX * labelGap
    val anchorY = screenPos.y + dirY * labelGap

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = if (dirX >= 0) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
        textSize = when {
            isSelected -> 13f * zoom.coerceIn(0.6f, 1.4f) + 10f
            isCategory -> 13f * zoom.coerceIn(0.5f, 1.6f) + 2f
            else -> 11f * zoom.coerceIn(0.5f, 1.6f) + 1f
        }
        isFakeBoldText = isCategory || isSelected
        val alpha = if (isSelected) 0.98f else if (dim) 0.32f else if (isCategory) 0.95f else 0.85f
        color = (if (isSelected) Mist100 else n.color).copy(alpha = alpha).toArgb()
        applyLabelShadow(this, 3f)
    }
    drawContext.canvas.nativeCanvas.drawText(n.label, anchorX, anchorY + paint.textSize * 0.32f, paint)
}

/** Samples the cubic bezier and draws round-capped segments of interpolated width — the cheap
 * way to get a tapering brush-stroke branch out of a canvas whose stroke width is otherwise flat. */
private fun DrawScope.drawTaperedBranch(
    p0: Offset, p1: Offset, p2: Offset, p3: Offset,
    color: Color, alpha: Float, widthStart: Float, widthEnd: Float,
    zoom: Float, toScreen: (Float, Float) -> Offset, segments: Int = 14
) {
    var prevX = p0.x
    var prevY = p0.y
    for (i in 1..segments) {
        val t = i / segments.toFloat()
        val mt = 1f - t
        val a = mt * mt * mt
        val b = 3f * mt * mt * t
        val c = 3f * mt * t * t
        val d = t * t * t
        val x = a * p0.x + b * p1.x + c * p2.x + d * p3.x
        val y = a * p0.y + b * p1.y + c * p2.y + d * p3.y
        val w = (widthStart + (widthEnd - widthStart) * t) * zoom
        drawLine(
            color = color.copy(alpha = alpha),
            start = toScreen(prevX, prevY),
            end = toScreen(x, y),
            strokeWidth = w.coerceAtLeast(1f),
            cap = StrokeCap.Round
        )
        prevX = x
        prevY = y
    }
}

/** Control points give each branch a radial launch tangent at both ends — parent heads straight
 * out along its own angle, child arrives straight out along its own — which is what produces the
 * gentle swooping-arm look instead of a straight ruler line between two points. */
private fun branchControlPoints(parent: Offset, parentAngle: Float, child: Offset, childAngle: Float): Pair<Offset, Offset> {
    val dx = child.x - parent.x
    val dy = child.y - parent.y
    val dist = sqrt(dx * dx + dy * dy)
    val k = dist * 0.45f
    val c1 = Offset(parent.x + cos(parentAngle) * k, parent.y + sin(parentAngle) * k)
    val c2 = Offset(child.x - cos(childAngle) * k, child.y - sin(childAngle) * k)
    return c1 to c2
}

private class Bounds(var minX: Float, var maxX: Float, var minY: Float, var maxY: Float)

/** Fit-to-view must bound the actual curve geometry, not just node positions — a branch's
 * tangent-launched bezier can bulge well past the straight line between its two endpoints
 * (more so the wider a category's angular sector is), and a bbox built from nodes alone left
 * those bulges clipped outside the canvas, overlapping the header row above it. */
private fun computeContentBounds(nodes: List<RadialNode>, branches: List<RadialBranch>): Bounds {
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    fun expand(x: Float, y: Float) {
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }
    nodes.forEach { n ->
        expand(n.x - n.xExtent(), n.y - n.yExtent())
        expand(n.x + n.xExtent(), n.y + n.yExtent())
    }
    branches.forEach { br ->
        val parentPos = Offset(br.parent.x, br.parent.y)
        val childPos = Offset(br.child.x, br.child.y)
        val parentAngle = if (br.parent.depth == 0) br.child.angle else br.parent.angle
        val (c1, c2) = branchControlPoints(parentPos, parentAngle, childPos, br.child.angle)
        for (i in 0..8) {
            val t = i / 8f
            val mt = 1f - t
            val x = mt * mt * mt * parentPos.x + 3f * mt * mt * t * c1.x + 3f * mt * t * t * c2.x + t * t * t * childPos.x
            val y = mt * mt * mt * parentPos.y + 3f * mt * mt * t * c1.y + 3f * mt * t * t * c2.y + t * t * t * childPos.y
            expand(x, y)
        }
    }
    return Bounds(minX, maxX, minY, maxY)
}

private const val ROOT_LABEL = "Notes"
private val PARA_ORDER = listOf("inbox", "project", "area", "resource", "archive")
private const val CATEGORY_RADIUS = 92f
private const val LEAF_RADIUS_NEAR = 168f
private const val LEAF_RADIUS_FAR = 206f
private const val SECTOR_GAP_DEG = 6f

private data class RadialNode(
    val id: String,
    val label: String,
    val angle: Float,
    val x: Float,
    val y: Float,
    val depth: Int, // 0 root, 1 category, 2 leaf note
    val color: Color,
    val note: NoteGraphNode?
) {
    fun xExtent(): Float = when (depth) {
        0 -> 60f
        1 -> maxOf(80f, label.length * 7f)
        else -> maxOf(50f, label.length * 6f)
    }
    fun yExtent(): Float = if (depth == 0) 24f else 28f
}

private data class RadialBranch(val parent: RadialNode, val child: RadialNode)
private data class CrossLink(val a: RadialNode, val b: RadialNode, val type: String)
private data class RadialLayout(val nodes: List<RadialNode>, val branches: List<RadialBranch>, val crossLinks: List<CrossLink>)

private fun paraLabel(key: String): String = when (key) {
    "inbox" -> "Inbox"
    "project" -> "Projects"
    "area" -> "Areas"
    "resource" -> "Resources"
    "archive" -> "Archive"
    else -> key.replaceFirstChar { it.uppercase() }
}

private fun truncateLabel(title: String, max: Int = 26): String =
    if (title.length <= max) title else title.take(max - 1).trimEnd() + "…"

/** Builds a two-level radial tree — root at origin, one branch per PARA category sized by note
 * count, notes as leaf twigs spread across their category's angular sector (alternating radius
 * so a busy category doesn't collapse into an unreadable ring) — then reattaches the original
 * note-to-note link/AI graph as cross-links resolved against the leaf positions. */
private fun runRadialLayout(nodes: List<NoteGraphNode>, edges: List<NoteGraphEdge>): RadialLayout? {
    if (nodes.isEmpty()) return null
    val grouped = nodes.groupBy { it.para }
    val order = PARA_ORDER.filter { it in grouped.keys } + (grouped.keys - PARA_ORDER.toSet()).sorted()
    if (order.isEmpty()) return null

    val root = RadialNode("root", ROOT_LABEL, 0f, 0f, 0f, 0, Mist100, null)
    val weights = order.map { key -> key to maxOf(1, grouped.getValue(key).size) }
    val totalWeight = weights.sumOf { it.second }
    val gapRad = (SECTOR_GAP_DEG * PI.toFloat() / 180f)
    val availableAngle = (2f * PI.toFloat()) - gapRad * order.size

    var cursor = -PI.toFloat() / 2f // start at 12 o'clock, sweep clockwise
    val categoryNodes = mutableListOf<RadialNode>()
    val leafNodes = mutableListOf<RadialNode>()
    val branches = mutableListOf<RadialBranch>()

    for ((key, weight) in weights) {
        val sectorAngle = availableAngle * weight / totalWeight
        val start = cursor
        val end = cursor + sectorAngle
        val mid = (start + end) / 2f
        val color = mindMapParaColor(key)
        val catNode = RadialNode(
            "cat:$key", paraLabel(key), mid,
            cos(mid) * CATEGORY_RADIUS, sin(mid) * CATEGORY_RADIUS,
            1, color, null
        )
        categoryNodes += catNode
        branches += RadialBranch(root, catNode)

        val leaves = grouped.getValue(key)
        val innerPad = if (leaves.size > 1) sectorAngle * 0.12f else 0f
        val leafStart = start + innerPad
        val leafEnd = end - innerPad
        leaves.forEachIndexed { i, n ->
            val t = if (leaves.size == 1) 0.5f else i / (leaves.size - 1).toFloat()
            val angle = leafStart + (leafEnd - leafStart) * t
            val radius = if (i % 2 == 0) LEAF_RADIUS_NEAR else LEAF_RADIUS_FAR
            val leafNode = RadialNode(
                n.id, truncateLabel(n.title), angle,
                cos(angle) * radius, sin(angle) * radius,
                2, color, n
            )
            leafNodes += leafNode
            branches += RadialBranch(catNode, leafNode)
        }
        cursor = end + gapRad
    }

    val leafById = leafNodes.associateBy { it.note!!.id }
    val crossLinks = edges.mapNotNull { e ->
        val a = leafById[e.from]
        val b = leafById[e.to]
        if (a != null && b != null) CrossLink(a, b, e.type) else null
    }

    return RadialLayout(listOf(root) + categoryNodes + leafNodes, branches, crossLinks)
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
