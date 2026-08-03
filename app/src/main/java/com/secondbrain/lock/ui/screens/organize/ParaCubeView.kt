package com.secondbrain.lock.ui.screens.organize

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.network.dto.Note
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.StreakCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private data class ParaFace(val key: String, val label: String, val empty: String)

private val FACES = listOf(
    ParaFace("resource", "Resources", "No resources yet. Mark a note as a Resource in Organize."),
    ParaFace("project", "Projects", "No active projects yet. Mark a note as a Project in Organize and it'll show up here."),
    ParaFace("area", "Areas", "No areas yet. Mark a note as an Area in Organize."),
    ParaFace("archive", "Archive", "Nothing archived yet.")
)
private const val DEFAULT_FACE_INDEX = 1

private val StackHeight = 340.dp
private val FlapWidthFraction = 0.60f
private val SideOffsetFraction = 0.30f
private const val SideTiltDeg = 42f
private const val SideScale = 0.88f
private const val SideAlpha = 0.55f
private val StackEasing = CubicBezierEasing(0.22f, 0.9f, 0.32f, 1f)

// Fraction of an index-unit the drag must cross (or release velocity must clear) to commit to a
// face change instead of springing back to where it started.
private const val CommitFractionThreshold = 0.18f
private const val MinCommitVelocityPxPerSec = 250f
// A real fidget spinner has near-zero rolling friction, so a flick keeps spinning — rapid ticks
// at first, gradually spacing out — for a good while before it actually stops, rather than the
// "abrupt handoff to a snap tween" a normal fling-to-scroll feels like. Coasting in real screen
// pixels lets a strong flick spin through several full loops of the 4 faces before it lands,
// exactly like a flicked wheel losing momentum. Lower frictionMultiplier = longer spin.
private const val SpinFrictionMultiplier = 1.1f
private const val SpinAbsVelocityThresholdPx = 60f
private const val MaxPixelVelocity = 20000f
private const val SettleDurationMs = 220

/** Wraps a continuous reel position into a 0-until-count face index (e.g. -0.6 -> count-1). */
private fun wrapIndex(position: Float, count: Int): Int {
    val m = position.roundToInt() % count
    return if (m < 0) m + count else m
}

/** Shortest signed circular distance from `position` to `index`, in range (-count/2, count/2]. */
private fun wrappedOffset(index: Int, position: Float, count: Int): Float {
    var raw = index - position
    val half = count / 2f
    while (raw > half) raw -= count
    while (raw <= -half) raw += count
    return raw
}

/**
 * Either springs back to the nearest face (drag was too short/slow to count) or, once committed,
 * coasts like a flicked wheel — real exponential friction in screen-pixel space, converted back
 * to index units every frame — before a final [settleToNearest] snap onto whichever face the
 * spin lands closest to. A tick fires on every face-boundary crossed in either phase, so a fast
 * flick ticks rapidly at first and tapers off as it slows, and a gentle one just ticks once.
 */
private suspend fun coastAndSettle(
    getPosition: () -> Float,
    setPosition: (Float) -> Unit,
    releaseVelocityPxPerSec: Float,
    flapWidthPx: Float,
    onTick: () -> Unit
) {
    val startPosition = getPosition()
    val nearestStart = startPosition.roundToInt()
    val fraction = abs(startPosition - nearestStart)
    val committed = fraction >= CommitFractionThreshold || abs(releaseVelocityPxPerSec) >= MinCommitVelocityPxPerSec

    if (!committed) {
        settleToNearest(getPosition, setPosition, nearestStart.toFloat(), onTick)
        return
    }

    val pixelVelocity = releaseVelocityPxPerSec.coerceIn(-MaxPixelVelocity, MaxPixelVelocity)
    val decay = Animatable(0f)
    var lastFloor = floor(startPosition).toInt()
    decay.animateDecay(
        pixelVelocity,
        exponentialDecay(frictionMultiplier = SpinFrictionMultiplier, absVelocityThreshold = SpinAbsVelocityThresholdPx)
    ) {
        val newPosition = startPosition + this.value / flapWidthPx
        setPosition(newPosition)
        val currentFloor = floor(newPosition).toInt()
        if (currentFloor != lastFloor) {
            onTick()
            lastFloor = currentFloor
        }
    }

    settleToNearest(getPosition, setPosition, getPosition().roundToInt().toFloat(), onTick)
}

/** Final short snap onto [target], ticking if it crosses a face boundary to get there. */
private suspend fun settleToNearest(
    getPosition: () -> Float,
    setPosition: (Float) -> Unit,
    target: Float,
    onTick: () -> Unit
) {
    val start = getPosition()
    if (abs(target - start) < 0.001f) return
    val anim = Animatable(start)
    var lastFloor = floor(start).toInt()
    anim.animateTo(target, animationSpec = tween(SettleDurationMs, easing = StackEasing)) {
        setPosition(this.value)
        val currentFloor = floor(this.value).toInt()
        if (currentFloor != lastFloor) {
            onTick()
            lastFloor = currentFloor
        }
    }
}

/** Tap-to-activate a visible neighbor flap — always a single step, same tick-on-boundary path. */
private suspend fun animateToIndex(
    getPosition: () -> Float,
    setPosition: (Float) -> Unit,
    index: Int,
    count: Int,
    onTick: () -> Unit
) {
    val position = getPosition()
    settleToNearest(getPosition, setPosition, position + wrappedOffset(index, position, count), onTick)
}

/**
 * 3D card stack: one card centered, its neighbors peeking from behind at an angle (prev to the
 * left, next to the right), the rest hidden. Tapping a side card brings it to center; the
 * centered card's own note tiles are the only ones interactive, matching the mock's
 * `pointer-events:none` on off-center item grids. CSS's `translateZ` depth recession has no
 * direct Compose equivalent, so it's approximated with a scale-down on the side cards instead —
 * combined with rotationY + alpha this reads the same without literal 3D translation. Desaturating
 * side cards (CSS `filter:saturate(0.9)`) is dropped as a minor flagged simplification.
 */
@Composable
fun ParaCubeView(
    notes: List<Note>,
    onOpenNote: (String) -> Unit,
    onMove: (Note, String) -> Unit,
    onGraduate: (Note) -> Unit,
    onDistill: (Note) -> Unit
) {
    var position by remember { mutableFloatStateOf(DEFAULT_FACE_INDEX.toFloat()) }
    val scope = rememberCoroutineScope()
    var motionJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val onTick: () -> Unit = { FeedbackUtil.spinTick(context) }

    StreakSurface {
        SbSectionTitle("P . A . R . A .", color = StreakAccent)
        Spacer(Modifier.height(16.dp))

        val density = LocalDensity.current
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(StackHeight)
        ) {
            val flapWidth = maxWidth * FlapWidthFraction
            val sideOffset = maxWidth * SideOffsetFraction
            val flapWidthPx = with(density) { flapWidth.toPx() }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(FACES.size, flapWidthPx) {
                        var lastTickFloor = floor(position).toInt()
                        // A hand-rolled EMA velocity from the same deltas driving the live drag,
                        // rather than Compose's VelocityTracker — on-device testing found it
                        // reliably returning zero for otherwise-clean fast drags on this device.
                        var velocityPxPerSec = 0f
                        var lastEventTimeMillis = 0L

                        detectHorizontalDragGestures(
                            onDragStart = {
                                motionJob?.cancel()
                                velocityPxPerSec = 0f
                                lastEventTimeMillis = 0L
                                lastTickFloor = floor(position).toInt()
                            },
                            onDragEnd = {
                                val forwardVelocity = velocityPxPerSec
                                motionJob = scope.launch {
                                    coastAndSettle(
                                        getPosition = { position },
                                        setPosition = { position = it },
                                        releaseVelocityPxPerSec = forwardVelocity,
                                        flapWidthPx = flapWidthPx,
                                        onTick = onTick
                                    )
                                }
                            },
                            onDragCancel = {
                                motionJob = scope.launch {
                                    settleToNearest({ position }, { position = it }, position.roundToInt().toFloat(), onTick)
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val nowMillis = change.uptimeMillis
                            if (lastEventTimeMillis != 0L) {
                                val dtSeconds = (nowMillis - lastEventTimeMillis).coerceAtLeast(1) / 1000f
                                // Sign-flipped to match the forward-positive convention below:
                                // dragging left (negative dragAmount) moves the reel forward.
                                val instVelocity = -dragAmount / dtSeconds
                                velocityPxPerSec = velocityPxPerSec * 0.7f + instVelocity * 0.3f
                            }
                            lastEventTimeMillis = nowMillis
                            position += -dragAmount / flapWidthPx

                            val currentFloor = floor(position).toInt()
                            if (currentFloor != lastTickFloor) {
                                onTick()
                                lastTickFloor = currentFloor
                            }
                        }
                    }
            ) {
                FACES.forEachIndexed { index, face ->
                    Flap3D(
                        face = face,
                        offset = wrappedOffset(index, position, FACES.size),
                        flapWidth = flapWidth,
                        sideOffset = sideOffset,
                        notes = notes.filter { it.para == face.key },
                        onActivate = {
                            motionJob?.cancel()
                            motionJob = scope.launch {
                                animateToIndex({ position }, { position = it }, index, FACES.size, onTick)
                            }
                        },
                        onOpenNote = onOpenNote,
                        onMove = onMove,
                        onGraduate = onGraduate,
                        onDistill = onDistill,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            val activeIndex = wrapIndex(position, FACES.size)
            FACES.forEachIndexed { index, _ ->
                val isActive = index == activeIndex
                val dotWidth by animateDpAsState(if (isActive) 16.dp else 6.dp, tween(250), label = "dotWidth")
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(dotWidth)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isActive) StreakAccent else Ink600)
                )
            }
        }
    }
}

@Composable
private fun Flap3D(
    face: ParaFace,
    offset: Float,
    flapWidth: Dp,
    sideOffset: Dp,
    notes: List<Note>,
    onActivate: () -> Unit,
    onOpenNote: (String) -> Unit,
    onMove: (Note, String) -> Unit,
    onGraduate: (Note) -> Unit,
    onDistill: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCenter = abs(offset) < 0.01f
    val densityScale = LocalDensity.current.density

    // Position is already driven frame-by-frame by the reel's own Animatable (live drag-follow
    // or the gear-fling settle), so these are direct continuous formulas rather than a second
    // layer of animateFloatAsState — a second tween here would fight the reel's own timing.
    val sideFraction = offset.coerceIn(-1f, 1f)
    val beyondFraction = (abs(offset) - 1f).coerceIn(0f, 1f)

    val translateX = sideOffset * sideFraction
    val rotation = -SideTiltDeg * sideFraction
    val scale = 1f - (1f - SideScale) * abs(sideFraction)
    val alpha = (1f - (1f - SideAlpha) * abs(sideFraction)) * (1f - beyondFraction)

    Box(
        modifier
            .width(flapWidth)
            .fillMaxHeight()
            .zIndex(3f - abs(offset).coerceIn(0f, 2f))
            .graphicsLayer {
                translationX = translateX.toPx()
                rotationY = rotation
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                cameraDistance = 12f * densityScale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Ink900)
            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(24.dp))
            .let { if (isCenter) it else it.clickable(onClick = onActivate) }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(StreakAccent))
                Spacer(Modifier.width(8.dp))
                Text(
                    face.label.uppercase(),
                    color = StreakAccent,
                    fontSize = 11.5.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            // Matches the mock's `.pos-prev/.pos-next/.pos-hidden .item-grid { pointer-events:none }`
            // — only the centered card's tiles are actually interactive.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                NoteTileGrid(notes, face.empty, enabled = isCenter, onOpenNote, onMove, onGraduate, onDistill)
            }
        }
    }
}

@Composable
private fun NoteTileGrid(
    notes: List<Note>,
    emptyText: String,
    enabled: Boolean,
    onOpenNote: (String) -> Unit,
    onMove: (Note, String) -> Unit,
    onGraduate: (Note) -> Unit,
    onDistill: (Note) -> Unit
) {
    if (notes.isEmpty()) {
        Text(emptyText, color = Mist300, style = SecondBrainTypography.bodySmall)
        return
    }
    notes.chunked(2).forEachIndexed { rowIndex, pair ->
        Row(Modifier.fillMaxWidth()) {
            pair.forEachIndexed { colIndex, note ->
                NoteTile(
                    note = note,
                    bg = tileRowBg(rowIndex * 2 + colIndex),
                    enabled = enabled,
                    modifier = Modifier.weight(1f).padding(4.dp),
                    onOpen = { onOpenNote(note.id) },
                    onMove = { para -> onMove(note, para) },
                    onGraduate = { onGraduate(note) },
                    onDistill = { onDistill(note) }
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Matches `PacketsSection.packetRowBg`'s 4-color cycle — flat tinted tiles instead of a
 * bordered/alpha grid. */
private fun tileRowBg(index: Int): Color = listOf(Ink700, StreakCard, Ink800, Ink600)[index % 4]

@Composable
private fun NoteTile(note: Note, bg: Color, enabled: Boolean, modifier: Modifier, onOpen: () -> Unit, onMove: (String) -> Unit, onGraduate: () -> Unit, onDistill: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            // No clickable at all when disabled (not clickable(enabled=false)) — a disabled
            // clickable still consumes the touch, whereas the mock's `pointer-events:none` lets
            // it fall through to the card's own onActivate. Omitting the modifier does the same.
            .let { if (enabled) it.clickable(onClick = onOpen) else it }
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(iconForNote(note.id), fontSize = 20.sp)
            if (enabled) Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Note actions", tint = Mist300)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf("project" to "Projects", "area" to "Areas", "resource" to "Resources", "archive" to "Archive")
                        .filter { it.first != note.para }
                        .forEach { (key, label) ->
                            DropdownMenuItem(text = { Text("Move to $label") }, onClick = { menuOpen = false; onMove(key) })
                        }
                    // Distill needs a typed executive summary (see NoteDetailScreen's DistillForm),
                    // so this just opens the note rather than distilling inline from the tile.
                    DropdownMenuItem(text = { Text("Distill") }, onClick = { menuOpen = false; onDistill() })
                    // Web gates Graduate on distilled && !graduated (NoteActionModal.js:126-130) —
                    // notes here are already pre-filtered to non-graduated, so just add distilled.
                    if (note.para != "inbox" && note.distilled) {
                        DropdownMenuItem(text = { Text("Graduate") }, onClick = { menuOpen = false; onGraduate() })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            note.title,
            color = Mist100,
            style = SecondBrainTypography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
