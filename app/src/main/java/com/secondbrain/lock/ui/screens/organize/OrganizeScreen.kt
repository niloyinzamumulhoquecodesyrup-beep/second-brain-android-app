package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.MindRepository
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.GradientText
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.launch

/** Mirrors pages/index.js ("Organize"): PARACube + GraduatedSection + MindMap + capture + insights. */
@Composable
fun OrganizeScreen(
    onOpenNote: (String) -> Unit,
    tagFilter: String? = null,
    onClearTag: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues()
) {
    val notes by NotesRepository.paraNotes.collectAsState()
    val graduated by NotesRepository.graduatedNotes.collectAsState()
    val packets by NotesRepository.packets.collectAsState()
    val insights by MindRepository.insights.collectAsState()
    var showCapture by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tagFilter) { NotesRepository.refreshPara(tagFilter) }
    LaunchedEffect(Unit) {
        launch { NotesRepository.refreshGraduated() }
        launch { NotesRepository.refreshPackets() }
        launch { MindRepository.refreshInsights() }
    }

    Box(Modifier.fullAuraBackground().fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                )
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SbLabel("Organize")
                    Spacer(Modifier.height(4.dp))
                    GradientText("Sort, distill, act", fontSize = 26.sp)
                }
                Button(
                    onClick = { showCapture = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Ink950)
                ) { Text("+ Capture") }
            }
            if (!tagFilter.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                FilterChip(
                    selected = true,
                    onClick = onClearTag,
                    label = { Text("tag: $tagFilter ✕") }
                )
            }
            Spacer(Modifier.height(16.dp))
            ParaCubeView(
                notes = notes,
                onOpenNote = onOpenNote,
                onMove = { note, para -> scope.launch { NotesRepository.moveToPara(note.id, para) } },
                onGraduate = { note -> scope.launch { NotesRepository.graduate(note.id) } }
            )
            Spacer(Modifier.height(16.dp))
            GraduatedSection(graduated, onOpenNote)
            Spacer(Modifier.height(16.dp))
            PacketsSection(packets, onOpenNote)
            Spacer(Modifier.height(16.dp))
            MindMapView(onOpenNote)
            Spacer(Modifier.height(16.dp))
            GoalArrowChart(insights?.byKind?.inferredGoal.orEmpty(), onOpenNote)
            Spacer(Modifier.height(16.dp))
            FieldInvestigationReport(insights?.byKind?.recommendation.orEmpty(), onOpenNote)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showCapture) {
        CaptureSheet(
            onDismiss = { showCapture = false },
            // Web keeps the capture form open after saving so you can burst-capture several
            // notes in a row (CaptureModal.js:5-7) — only refresh the list, don't dismiss.
            onCaptured = { scope.launch { NotesRepository.refreshPara(tagFilter) } }
        )
    }
}
