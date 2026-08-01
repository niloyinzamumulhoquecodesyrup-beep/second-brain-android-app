package com.secondbrain.lock.ui.screens.organize

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
    var activeIndex by remember { mutableStateOf(DEFAULT_FACE_INDEX) }

    StreakSurface {
        SbSectionTitle("P . A . R . A .", color = StreakAccent)
        Spacer(Modifier.height(16.dp))

        val density = LocalDensity.current
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(StackHeight)
                .pointerInput(FACES.size) {
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onDragEnd = {
                            val threshold = with(density) { 56.dp.toPx() }
                            when {
                                dragTotal <= -threshold -> activeIndex = (activeIndex + 1).coerceAtMost(FACES.lastIndex)
                                dragTotal >= threshold -> activeIndex = (activeIndex - 1).coerceAtLeast(0)
                            }
                            dragTotal = 0f
                        },
                        onDragCancel = { dragTotal = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        dragTotal += dragAmount
                    }
                }
        ) {
            val flapWidth = maxWidth * FlapWidthFraction
            val sideOffset = maxWidth * SideOffsetFraction
            FACES.forEachIndexed { index, face ->
                Flap3D(
                    face = face,
                    offset = index - activeIndex,
                    flapWidth = flapWidth,
                    sideOffset = sideOffset,
                    notes = notes.filter { it.para == face.key },
                    onActivate = { activeIndex = index },
                    onOpenNote = onOpenNote,
                    onMove = onMove,
                    onGraduate = onGraduate,
                    onDistill = onDistill,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
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
    offset: Int,
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
    val isCenter = offset == 0
    val isSide = offset == -1 || offset == 1
    val densityScale = LocalDensity.current.density

    val translateX by animateDpAsState(
        targetValue = when (offset) { -1 -> -sideOffset; 1 -> sideOffset; else -> 0.dp },
        animationSpec = tween(550, easing = StackEasing),
        label = "flapX"
    )
    val rotation by animateFloatAsState(
        targetValue = when (offset) { -1 -> SideTiltDeg; 1 -> -SideTiltDeg; else -> 0f },
        animationSpec = tween(550, easing = StackEasing),
        label = "flapRotation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isCenter) 1f else SideScale,
        animationSpec = tween(550, easing = StackEasing),
        label = "flapScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isCenter) 1f else if (isSide) SideAlpha else 0f,
        animationSpec = tween(500),
        label = "flapAlpha"
    )

    Box(
        modifier
            .width(flapWidth)
            .fillMaxHeight()
            .zIndex(if (isCenter) 3f else if (isSide) 2f else 1f)
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
