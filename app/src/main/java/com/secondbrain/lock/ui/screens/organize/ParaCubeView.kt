package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.network.dto.Note
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import kotlinx.coroutines.launch

private data class ParaFace(val key: String, val heading: String, val name: String, val empty: String, val glow: Color)

private val FACES = listOf(
    ParaFace("project", "Jump into a project", "Projects", "No active projects yet. Mark a note as a Project in Organize and it'll show up here.", Color(0xFF34D399)),
    ParaFace("area", "Jump into an area", "Areas", "No areas yet. Mark a note as an Area in Organize.", Color(0xFFA78BFA)),
    ParaFace("resource", "Jump into a resource", "Resources", "No resources yet. Mark a note as a Resource in Organize.", Color(0xFFFACC15)),
    ParaFace("archive", "Jump into the archive", "Archives", "Nothing archived yet.", Color(0xFF94A3B8))
)

/**
 * Ports PARACube.js as a genuine rotating cube: 4 faces (project/area/resource/archive),
 * swipeable with a 3D rotationY transform per page, prev/next chevrons, and dot indicators —
 * built on HorizontalPager (which already solves swipe-vs-tap gesture disambiguation, the
 * same problem the web solves by hand with pointer-capture jitter thresholds) rather than a
 * hand-rolled drag detector. The literal CSS translateZ/perspective cube math is approximated
 * with Compose's graphicsLayer rotationY + cameraDistance, which is a real 3D transform, not
 * a slide — flagged simplification: exact perspective distances differ from the CSS version,
 * and the "peek" of the neighboring face at rest isn't reproduced.
 * No inbox: every note gets filed straight into one of the 4 faces at capture time, so there's
 * nothing left to triage here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ParaCubeView(
    notes: List<Note>,
    onOpenNote: (String) -> Unit,
    onMove: (Note, String) -> Unit,
    onGraduate: (Note) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { FACES.size })
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val activeFace = FACES[pagerState.currentPage]

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink900)
            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(16.dp))
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(paraAccent(activeFace.key)))
        Column(Modifier.padding(20.dp)) {
            SbLabel("Organize")
            Spacer(Modifier.height(10.dp))

            Text(activeFace.heading, color = paraAccent(activeFace.key), style = SecondBrainTypography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Box(Modifier.fillMaxWidth().height(320.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(activeFace.glow.copy(alpha = 0.18f), Color.Transparent),
                                radius = 500f
                            )
                        )
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val face = FACES[page]
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(paraCubePageModifier(pagerState, page, density))
                    ) {
                        FaceCard(
                            face = face,
                            notes = notes.filter { it.para == face.key },
                            onOpenNote = onOpenNote,
                            onMove = onMove,
                            onGraduate = onGraduate
                        )
                    }
                }
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1 + FACES.size) % FACES.size) } },
                    modifier = Modifier.align(Alignment.CenterStart).size(32.dp)
                ) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Ink950.copy(alpha = 0.7f)).border(BorderStroke(1.dp, Ink600), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("‹", color = Mist300, fontSize = 16.sp) }
                }
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1) % FACES.size) } },
                    modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
                ) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Ink950.copy(alpha = 0.7f)).border(BorderStroke(1.dp, Ink600), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("›", color = Mist300, fontSize = 16.sp) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FACES.forEachIndexed { i, face ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (i == pagerState.currentPage) paraAccent(face.key) else Ink600)
                            .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                    )
                }
            }
        }
    }
}

/**
 * rotationY = this page's angular offset from front-facing (in units of 90°, matching the
 * cube's per-face spacing), so a full page-swipe reads as a quarter-turn of the cube.
 * Off-screen pages (|offset| > ~1.2) are made fully transparent — Compose's graphicsLayer
 * doesn't backface-cull the way the CSS `backfaceVisibility:hidden` cube does, so without
 * this a face rotated past 90° would show through mirrored instead of disappearing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun paraCubePageModifier(pagerState: androidx.compose.foundation.pager.PagerState, page: Int, density: Float): Modifier {
    val offset = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
    return Modifier.graphicsLayer {
        rotationY = offset * 90f
        cameraDistance = 10f * density
        alpha = if (kotlin.math.abs(offset) > 1.2f) 0f else 1f
    }
}

@Composable
private fun FaceCard(
    face: ParaFace,
    notes: List<Note>,
    onOpenNote: (String) -> Unit,
    onMove: (Note, String) -> Unit,
    onGraduate: (Note) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink900)
            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(paraAccent(face.key)))
            Spacer(Modifier.width(6.dp))
            Text(face.name.uppercase(), color = paraAccent(face.key), fontSize = 12.sp, style = SecondBrainTypography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        NoteTileGrid(notes, face.empty, onOpenNote, onMove, onGraduate)
    }
}

@Composable
private fun NoteTileGrid(
    notes: List<Note>,
    emptyText: String,
    onOpenNote: (String) -> Unit,
    onMove: (Note, String) -> Unit,
    onGraduate: (Note) -> Unit
) {
    if (notes.isEmpty()) {
        Text(emptyText, color = Mist400, style = SecondBrainTypography.bodySmall)
        return
    }
    notes.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth()) {
            pair.forEach { note ->
                NoteTile(
                    note = note,
                    modifier = Modifier.weight(1f).padding(4.dp),
                    onOpen = { onOpenNote(note.id) },
                    onMove = { para -> onMove(note, para) },
                    onGraduate = { onGraduate(note) }
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NoteTile(note: Note, modifier: Modifier, onOpen: () -> Unit, onMove: (String) -> Unit, onGraduate: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(12.dp))
            .background(Ink950.copy(alpha = 0.4f))
            .clickable(onClick = onOpen)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(iconForNote(note.id), fontSize = 22.sp)
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Note actions", tint = Mist400)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf("project" to "Projects", "area" to "Areas", "resource" to "Resources", "archive" to "Archive")
                        .filter { it.first != note.para }
                        .forEach { (key, label) ->
                            DropdownMenuItem(text = { Text("Move to $label") }, onClick = { menuOpen = false; onMove(key) })
                        }
                    // Web gates Graduate on distilled && !graduated (NoteActionModal.js:126-130) —
                    // notes here are already pre-filtered to non-graduated, so just add distilled.
                    if (note.para != "inbox" && note.distilled) {
                        DropdownMenuItem(text = { Text("Graduate") }, onClick = { menuOpen = false; onGraduate() })
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            note.title,
            color = Mist100,
            style = SecondBrainTypography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
