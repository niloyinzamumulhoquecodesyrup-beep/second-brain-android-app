package com.secondbrain.lock.ui.screens.mind

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.network.dto.MindInsight
import com.secondbrain.lock.network.dto.MindLibraryEntry
import com.secondbrain.lock.network.dto.MindTopic
import com.secondbrain.lock.ui.screens.organize.SourceRefsView
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Emerald500
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.Sky400
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Isometric 3D port of the "Interest Cluster City Map" design: each domain-level mind_topic is a
 * city on a ring, its descendant topics are buildings on a smaller ring around it. Height/heat
 * come from the same goal/library-match heuristic MindKnowledgeGalaxy used for its bubble graph —
 * no real per-topic interest score exists server-side, so this reuses that exact signal rather
 * than inventing a new one. A small hand-rolled 3D pipeline (rotate -> perspective divide ->
 * painter's algorithm) stands in for CSS 3D, which Compose has no equivalent of.
 */
@Composable
fun InterestClusterCityMap(
    topics: List<MindTopic>,
    goals: List<MindInsight>,
    library: List<MindLibraryEntry>,
    onOpenNote: (String) -> Unit
) {
    val cities = remember(topics, goals, library) { buildCities(topics, goals, library) }
    val connections = remember(cities) { buildConnections(cities) }

    var zoom by remember(cities) { mutableFloatStateOf(1f) }
    var fitZoom by remember(cities) { mutableFloatStateOf(1f) }
    var pan by remember(cities) { mutableStateOf(Offset.Zero) }
    var hasFitted by remember(cities) { mutableStateOf(false) }
    var selected by remember(cities) { mutableStateOf<Building?>(null) }

    StreakSurface {
        SbSectionTitle("Interest cluster", color = Mist300)
        Spacer(Modifier.height(4.dp))
        Text("drag to pan · pinch to zoom for names", color = Mist400, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))

        if (cities.isEmpty()) {
            Text(
                "No topic map yet — this fills in once a mind cycle runs.",
                color = Mist400,
                style = SecondBrainTypography.bodySmall
            )
            return@StreakSurface
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Ink950)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cities) {
                        if (!hasFitted && size.width > 0 && size.height > 0) {
                            val anchor = Offset(size.width / 2f, size.height * 0.56f)
                            val points = cities.flatMap { it.extentPoints() }
                            val projected = points.map { projectPoint(it, 1f, Offset.Zero, anchor) }
                            val minX = projected.minOf { it.first.x }
                            val maxX = projected.maxOf { it.first.x }
                            val minY = projected.minOf { it.first.y }
                            val maxY = projected.maxOf { it.first.y }
                            val bboxW = maxOf(40f, maxX - minX)
                            val bboxH = maxOf(40f, maxY - minY)
                            val fz = (minOf(size.width / bboxW, size.height / bboxH) * 0.82f).coerceIn(0.05f, 6f)
                            val bboxCenter = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
                            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
                            fitZoom = fz
                            zoom = fz
                            pan = Offset(
                                canvasCenter.x - anchor.x - (bboxCenter.x - anchor.x) * fz,
                                canvasCenter.y - anchor.y - (bboxCenter.y - anchor.y) * fz
                            )
                            hasFitted = true
                        }
                    }
                    .pointerInput(cities) {
                        detectTransformGestures { centroid, panDelta, zoomChange, _ ->
                            val newZoom = (zoom * zoomChange).coerceIn(fitZoom * 0.4f, fitZoom * 6f)
                            pan = Offset(
                                centroid.x - (centroid.x - pan.x) * (newZoom / zoom),
                                centroid.y - (centroid.y - pan.y) * (newZoom / zoom)
                            )
                            zoom = newZoom
                            pan += panDelta
                        }
                    }
                    .pointerInput(cities) {
                        detectTapGestures { tapOffset ->
                            val anchor = Offset(size.width / 2f, size.height * 0.56f)
                            var best: Building? = null
                            var bestDist = Float.MAX_VALUE
                            cities.forEach { city ->
                                city.buildings.forEach { b ->
                                    val (screen, _) = projectPoint(b.base, zoom, pan, anchor)
                                    val d = hypot(tapOffset.x - screen.x, tapOffset.y - screen.y)
                                    val hitR = maxOf(18f, b.width * zoom * 0.6f)
                                    if (d < hitR && d < bestDist) {
                                        bestDist = d
                                        best = b
                                    }
                                }
                            }
                            selected = best
                        }
                    }
            ) {
                val anchor = Offset(size.width / 2f, size.height * 0.56f)
                val drawables = mutableListOf<Pair<Float, () -> Unit>>()

                cities.forEach { city ->
                    val rimPoints = 24
                    val plateColor = heatColor(city.score / 100f)
                    val path = Path()
                    for (k in 0 until rimPoints) {
                        val a = rad(k * 360f / rimPoints)
                        val p = Vec3(
                            city.center.x + cos(a) * city.plateRadius,
                            0f,
                            city.center.z + sin(a) * city.plateRadius
                        )
                        val (screen, _) = projectPoint(p, zoom, pan, anchor)
                        if (k == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
                    }
                    path.close()
                    val (_, centerDepth) = projectPoint(city.center, zoom, pan, anchor)
                    drawables += centerDepth to {
                        drawPath(path, color = plateColor.copy(alpha = 0.2f))
                        drawPath(path, color = plateColor.copy(alpha = 0.45f), style = Stroke(width = 1.2f))
                    }

                    city.buildings.forEach { b ->
                        val t = (b.score / 100f).coerceIn(0f, 1f)
                        val base = heatColor(t)
                        FACES.forEach { face ->
                            val viewNormal = toView(face.normal)
                            if (viewNormal.z > 0.02f) {
                                val projectedCorners = face.corners(b).map { projectPoint(it, zoom, pan, anchor) }
                                val depth = projectedCorners.sumOf { it.second.toDouble() }.toFloat() / projectedCorners.size
                                val color = shade(base, faceBrightness(face.normal))
                                val facePath = Path().apply {
                                    moveTo(projectedCorners[0].first.x, projectedCorners[0].first.y)
                                    for (i in 1 until projectedCorners.size) {
                                        lineTo(projectedCorners[i].first.x, projectedCorners[i].first.y)
                                    }
                                    close()
                                }
                                drawables += depth to { drawPath(facePath, color = color) }
                            }
                        }
                    }
                }

                drawables.sortByDescending { it.first }
                drawables.forEach { it.second() }

                connections.forEach { (a, b) ->
                    val (pa, _) = projectPoint(Vec3(a.base.x, a.height * 0.5f, a.base.z), zoom, pan, anchor)
                    val (pb, _) = projectPoint(Vec3(b.base.x, b.height * 0.5f, b.base.z), zoom, pan, anchor)
                    drawLine(color = Mist300.copy(alpha = 0.35f), start = pa, end = pb, strokeWidth = 1.4f)
                }

                selected?.let { sel ->
                    val (screen, _) = projectPoint(sel.base, zoom, pan, anchor)
                    drawCircle(color = Mist100, radius = 6f, center = screen, style = Stroke(width = 1.5f))
                }

                if (zoom > fitZoom * 1.45f) {
                    cities.forEach { city ->
                        city.buildings.forEach { b ->
                            val (top, _) = projectPoint(b.topCenter, zoom, pan, anchor)
                            val rightward = top.x >= anchor.x
                            val labelX = top.x + if (rightward) 10f else -10f
                            drawContext.canvas.nativeCanvas.apply {
                                val linePaint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    color = Mist300.copy(alpha = 0.7f).toArgb()
                                    strokeWidth = 1.2f
                                }
                                drawLine(top.x, top.y, labelX, top.y, linePaint)
                                val textPaint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    textAlign = if (rightward) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                                    textSize = 10.sp.toPx()
                                    color = Mist100.toArgb()
                                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                                }
                                drawText(b.branch.name, labelX, top.y + textPaint.textSize / 3f, textPaint)
                            }
                        }
                    }
                }

                cities.forEach { city ->
                    val topHeight = city.buildings.maxOfOrNull { it.height } ?: 0f
                    val labelPoint = Vec3(city.center.x, topHeight + 46f, city.center.z)
                    val (screen, _) = projectPoint(labelPoint, zoom, pan, anchor)
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 13.sp.toPx()
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
                            color = Mist100.toArgb()
                            setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
                        }
                        drawText(city.topic.name, screen.x, screen.y, paint)
                    }
                }
            }

            Column(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                ZoomButton("+") { zoom = (zoom + fitZoom * 0.25f).coerceAtMost(fitZoom * 6f) }
                Spacer(Modifier.height(8.dp))
                ZoomButton("−") { zoom = (zoom - fitZoom * 0.25f).coerceAtLeast(fitZoom * 0.4f) }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ink900.copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(Sky400, StreakAccent)))
                )
                Spacer(Modifier.width(6.dp))
                Text("interest", color = Mist400, fontSize = 9.5.sp)
            }
        }

        selected?.let { b ->
            Spacer(Modifier.height(10.dp))
            BuildingDetailPanel(b, onOpenNote, onClose = { selected = null })
        }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Ink900.copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Mist100, fontSize = 17.sp) }
}

@Composable
private fun BuildingDetailPanel(b: Building, onOpenNote: (String) -> Unit, onClose: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(b.branch.name, color = Mist100, style = SecondBrainTypography.bodyMedium)
                Text(b.cityName, color = Mist400, style = SecondBrainTypography.bodySmall)
                val meta = when {
                    b.goal != null -> "${b.goal.sourceRefs.size.coerceAtLeast(1)} note${if (b.goal.sourceRefs.size == 1) "" else "s"}"
                    b.libraryEntry != null -> "explored ${b.libraryEntry.cycleCount}× via field investigation"
                    else -> "no evidence yet"
                }
                Text(meta, color = Mist400, style = SecondBrainTypography.bodySmall)
            }
            TextButton(onClick = onClose) { Text("close", color = Mist400, fontSize = 11.sp) }
        }
        val summary = b.goal?.summary ?: b.libraryEntry?.summary
        if (!summary.isNullOrBlank()) {
            Text(summary, color = Mist300, style = SecondBrainTypography.bodySmall)
        }
        SourceRefsView(b.goal?.sourceRefs.orEmpty(), onOpenNote)
    }
}

// ---- scene data ----------------------------------------------------------

private data class Building(
    val branch: MindTopic,
    val cityName: String,
    val score: Float,
    val lit: Boolean,
    val goal: MindInsight?,
    val libraryEntry: MindLibraryEntry?,
    val noteIds: Set<String>,
    val base: Vec3,
    val width: Float,
    val depth: Float,
    val height: Float
) {
    val topCenter: Vec3 get() = Vec3(base.x, height, base.z)
}

private class City(
    val topic: MindTopic,
    val score: Float,
    val center: Vec3,
    val plateRadius: Float,
    val buildings: List<Building>
)

private fun City.extentPoints(): List<Vec3> {
    val pts = mutableListOf<Vec3>()
    pts += Vec3(center.x - plateRadius, 0f, center.z)
    pts += Vec3(center.x + plateRadius, 0f, center.z)
    pts += Vec3(center.x, 0f, center.z - plateRadius)
    pts += Vec3(center.x, 0f, center.z + plateRadius)
    buildings.forEach { b ->
        pts += b.base
        pts += b.topCenter
    }
    return pts
}

private const val MIN_CITY_RING_RADIUS = 340f
private const val BUILDING_RING_RADIUS = 100f

/** Ports MindKnowledgeGalaxy.runGalaxyLayout's exact lit/strength heuristic (goal-name match
 * against inferred_goal insights, else a knowledge-library title match), rescaled 0..100 —
 * there is no real per-topic interest score anywhere in the API, so this reuses the same signal
 * the bubble graph already shipped rather than fabricating a new one. */
private fun buildCities(topics: List<MindTopic>, goals: List<MindInsight>, library: List<MindLibraryEntry>): List<City> {
    if (topics.isEmpty()) return emptyList()
    val goalByName = goals.mapNotNull { g -> g.metadataString("name")?.let { it to g } }.toMap()
    val libByTitle = library.associateBy { it.title.lowercase() }
    val byParent = topics.filter { it.parentSlug != null }.groupBy { it.parentSlug!! }

    // Real topic trees are frequently a single top-level "everything" root with the actual
    // knowledge domains one level down (unlike a flat multi-root taxonomy) — descend past any
    // single-node level so "cities" land on real sibling domains instead of collapsing the
    // whole tree into one city with dozens of buildings crammed onto one ring. Confirmed against
    // this app's real data: a bare parentSlug==null filter yields 1 root with 42 flattened
    // descendants; unwrapping once yields 4 real domains with 6-12 branches each.
    var candidates = topics.filter { it.parentSlug == null }.sortedBy { it.position }
    var guard = 0
    while (candidates.size == 1 && guard < 4) {
        val children = byParent[candidates[0].slug].orEmpty().sortedBy { it.position }
        if (children.isEmpty()) break
        candidates = children
        guard++
    }
    val roots = candidates
    if (roots.isEmpty()) return emptyList()

    fun descendantsOf(slug: String): List<MindTopic> {
        val direct = byParent[slug].orEmpty()
        return direct + direct.flatMap { descendantsOf(it.slug) }
    }

    fun score(topic: MindTopic): Triple<Float, Boolean, Pair<MindInsight?, MindLibraryEntry?>> {
        val goal = topic.goalName?.let { goalByName[it] }
        val libEntry = libByTitle[topic.name.lowercase()]
        val lit = goal != null || libEntry != null
        val value = when {
            goal != null -> (55f + goal.sourceRefs.size.coerceAtMost(10) * 4f).coerceAtMost(98f)
            libEntry != null -> (35f + libEntry.cycleCount.coerceAtMost(10) * 3f).coerceAtMost(90f)
            else -> 12f
        }
        return Triple(value, lit, goal to libEntry)
    }

    val filtered = roots.mapNotNull { root ->
        val branchTopics = descendantsOf(root.slug).sortedBy { it.position }
        if (branchTopics.isEmpty()) null else root to branchTopics
    }
    if (filtered.isEmpty()) return emptyList()

    // Branch counts per domain are wildly uneven in real data (one domain might have 2
    // sub-topics, another 40) — unlike the mockup's fixed ~8-per-domain grid, so ring radius,
    // ring count, and building footprint all scale with how many buildings actually need to
    // fit, instead of a fixed layout that collapses into one solid mass once a city has more
    // than a handful of buildings.
    data class CityPlan(val root: MindTopic, val buildings: List<Building>, val plateRadius: Float, val score: Float)

    val plans = filtered.map { (root, branchTopics) ->
        val n = branchTopics.size
        val ringCount = ceil(sqrt(n / 5f)).toInt().coerceIn(1, 5)
        val slotsPerRing = ceil(n.toFloat() / ringCount).toInt()
        val sizeScale = (9f / n).coerceIn(0.3f, 1f)
        val ringBase = BUILDING_RING_RADIUS * (1f + n / 18f)

        val buildings = branchTopics.mapIndexed { j, topic ->
            val (topicScore, lit, refs) = score(topic)
            val ring = j % ringCount
            val slot = j / ringCount
            val angle = rad(slot * (360f / slotsPerRing) + ring * 17f)
            val rr = ringBase * (0.45f + ring.toFloat() / ringCount * 0.65f)
            val noteIds = buildSet {
                refs.first?.sourceRefs?.forEach { r -> if (r.type == "note" && r.id != null) add(r.id) }
                refs.second?.sourceRefs?.forEach { r -> if (r.type == "note" && r.id != null) add(r.id) }
            }
            Building(
                branch = topic,
                cityName = root.name,
                score = topicScore,
                lit = lit,
                goal = refs.first,
                libraryEntry = refs.second,
                noteIds = noteIds,
                base = Vec3(cos(angle) * rr, 0f, sin(angle) * rr), // city-local; shifted to world below
                width = (24f + (j % 4) * 3f) * sizeScale,
                depth = (24f + ((j + 2) % 4) * 3f) * sizeScale,
                height = (18f + topicScore * 1.2f) * (0.6f + 0.4f * sizeScale)
            )
        }
        val citySc = buildings.map { it.score }.average().toFloat()
        val plateRadius = ringBase * 1.2f + (citySc / 100f) * 30f
        CityPlan(root, buildings, plateRadius, citySc)
    }

    val n = plans.size
    val maxPlate = plans.maxOf { it.plateRadius }
    val cityRingRadius = if (n <= 1) 0f else
        maxOf(MIN_CITY_RING_RADIUS, (maxPlate * 2.4f) / (2f * sin(PI.toFloat() / n)))

    return plans.mapIndexed { i, plan ->
        val angle = rad(-90f + i * (360f / n.coerceAtLeast(1)))
        val cx = if (n <= 1) 0f else cos(angle) * cityRingRadius
        val cz = if (n <= 1) 0f else sin(angle) * cityRingRadius
        val worldBuildings = plan.buildings.map { b -> b.copy(base = Vec3(cx + b.base.x, 0f, cz + b.base.z)) }
        City(
            topic = plan.root,
            score = plan.score,
            center = Vec3(cx, 0f, cz),
            plateRadius = plan.plateRadius,
            buildings = worldBuildings
        )
    }
}

/** A building's evidence (which notes its matched goal/library entry cites) is the only real
 * cross-topic signal available — there's no "these two branches are related" field anywhere in
 * the API. Two buildings in different cities are drawn as connected only when they're actually
 * backed by at least one of the same notes, never as a fabricated or purely visual link. */
private fun buildConnections(cities: List<City>): List<Pair<Building, Building>> {
    val tagged = cities.flatMapIndexed { cityIndex, city -> city.buildings.map { cityIndex to it } }
    val connections = mutableListOf<Pair<Building, Building>>()
    for (i in tagged.indices) {
        val (cityA, a) = tagged[i]
        if (a.noteIds.isEmpty()) continue
        for (j in i + 1 until tagged.size) {
            val (cityB, b) = tagged[j]
            if (cityA == cityB || b.noteIds.isEmpty()) continue
            if (a.noteIds.any { it in b.noteIds }) connections += a to b
        }
    }
    return connections
}

// ---- 3D math --------------------------------------------------------------

private data class Vec3(val x: Float, val y: Float, val z: Float)

private const val SPIN_DEG = 24f
private const val TILT_DEG = 56f
private const val FOCAL = 760f
private const val CAMERA_DIST = 900f
private val SPIN_RAD = rad(SPIN_DEG)
private val TILT_RAD = rad(TILT_DEG)
private val LIGHT_DIR = run {
    val l = Vec3(-0.4f, 0.85f, 0.4f)
    val len = sqrt(l.x * l.x + l.y * l.y + l.z * l.z)
    Vec3(l.x / len, l.y / len, l.z / len)
}

private fun rad(deg: Float) = deg * (PI.toFloat() / 180f)

private fun rotateY(v: Vec3, r: Float): Vec3 {
    val c = cos(r); val s = sin(r)
    return Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
}

private fun rotateX(v: Vec3, r: Float): Vec3 {
    val c = cos(r); val s = sin(r)
    return Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c)
}

private fun toView(v: Vec3): Vec3 = rotateX(rotateY(v, SPIN_RAD), TILT_RAD)

private fun dot(a: Vec3, b: Vec3) = a.x * b.x + a.y * b.y + a.z * b.z

/** Screen offset + view-space depth (for painter's-algorithm sorting) of a world point. Depth
 * doesn't depend on zoom/pan, so screen position is linear in zoom exactly like a plain 2D
 * pan/zoom — the same centroid-anchored zoom math the bubble graph already used still applies. */
private fun projectPoint(v: Vec3, zoom: Float, pan: Offset, anchor: Offset): Pair<Offset, Float> {
    val view = toView(v)
    val depth = view.z + CAMERA_DIST
    val persp = FOCAL / (FOCAL + depth).coerceAtLeast(1f)
    val s = zoom * persp
    return Offset(anchor.x + view.x * s + pan.x, anchor.y - view.y * s + pan.y) to depth
}

private fun faceBrightness(normal: Vec3): Float =
    (0.48f + 0.72f * dot(normal, LIGHT_DIR).coerceIn(0f, 1f)).coerceIn(0.4f, 1.2f)

private fun shade(color: Color, brightness: Float): Color = Color(
    red = (color.red * brightness).coerceIn(0f, 1f),
    green = (color.green * brightness).coerceIn(0f, 1f),
    blue = (color.blue * brightness).coerceIn(0f, 1f),
    alpha = color.alpha
)

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt
    )
}

/** Same heat scale the app already ships via its theme tokens, not the design's hardcoded hex —
 * they happen to match exactly (Sky400/Emerald500/Gold500/StreakAccent dark values are the
 * design's own stop colors), so reusing the tokens keeps this in sync with future palette edits
 * and correct in both light/dark automatically. */
private fun heatColor(t: Float): Color {
    val stops = listOf(0f to Sky400, 0.4f to Emerald500, 0.65f to Gold500, 1f to StreakAccent)
    val tt = t.coerceIn(0f, 1f)
    for (i in 0 until stops.size - 1) {
        val (t0, c0) = stops[i]
        val (t1, c1) = stops[i + 1]
        if (tt in t0..t1) {
            val local = if (t1 == t0) 0f else (tt - t0) / (t1 - t0)
            return lerpColor(c0, c1, local)
        }
    }
    return stops.last().second
}

// ---- building geometry -----------------------------------------------------

private class Face(val normal: Vec3, val corners: (Building) -> List<Vec3>)

private val FACES = listOf(
    Face(Vec3(0f, 1f, 0f)) { b -> // top
        val hw = b.width / 2f; val hd = b.depth / 2f
        listOf(
            Vec3(b.base.x - hw, b.height, b.base.z - hd),
            Vec3(b.base.x + hw, b.height, b.base.z - hd),
            Vec3(b.base.x + hw, b.height, b.base.z + hd),
            Vec3(b.base.x - hw, b.height, b.base.z + hd)
        )
    },
    Face(Vec3(0f, 0f, 1f)) { b -> // front (+Z)
        val hw = b.width / 2f; val hd = b.depth / 2f
        listOf(
            Vec3(b.base.x - hw, 0f, b.base.z + hd),
            Vec3(b.base.x + hw, 0f, b.base.z + hd),
            Vec3(b.base.x + hw, b.height, b.base.z + hd),
            Vec3(b.base.x - hw, b.height, b.base.z + hd)
        )
    },
    Face(Vec3(0f, 0f, -1f)) { b -> // back (-Z)
        val hw = b.width / 2f; val hd = b.depth / 2f
        listOf(
            Vec3(b.base.x + hw, 0f, b.base.z - hd),
            Vec3(b.base.x - hw, 0f, b.base.z - hd),
            Vec3(b.base.x - hw, b.height, b.base.z - hd),
            Vec3(b.base.x + hw, b.height, b.base.z - hd)
        )
    },
    Face(Vec3(1f, 0f, 0f)) { b -> // right (+X)
        val hw = b.width / 2f; val hd = b.depth / 2f
        listOf(
            Vec3(b.base.x + hw, 0f, b.base.z + hd),
            Vec3(b.base.x + hw, 0f, b.base.z - hd),
            Vec3(b.base.x + hw, b.height, b.base.z - hd),
            Vec3(b.base.x + hw, b.height, b.base.z + hd)
        )
    },
    Face(Vec3(-1f, 0f, 0f)) { b -> // left (-X)
        val hw = b.width / 2f; val hd = b.depth / 2f
        listOf(
            Vec3(b.base.x - hw, 0f, b.base.z - hd),
            Vec3(b.base.x - hw, 0f, b.base.z + hd),
            Vec3(b.base.x - hw, b.height, b.base.z + hd),
            Vec3(b.base.x - hw, b.height, b.base.z - hd)
        )
    }
)
