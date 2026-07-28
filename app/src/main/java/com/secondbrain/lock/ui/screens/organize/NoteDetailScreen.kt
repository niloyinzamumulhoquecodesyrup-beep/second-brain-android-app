package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.Note
import com.secondbrain.lock.network.dto.NoteLinksResponse
import com.secondbrain.lock.network.dto.RelatedNote
import com.secondbrain.lock.network.dto.UpdateNoteRequest
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.Violet400
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.launch

/** Mirrors pages/notes/[id].js: view/edit toggle, links, related, delete-with-confirm. */
@Composable
fun NoteDetailScreen(
    noteId: String,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenTag: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues()
) {
    var note by remember { mutableStateOf<Note?>(null) }
    var links by remember { mutableStateOf<NoteLinksResponse?>(null) }
    var related by remember { mutableStateOf<List<RelatedNote>>(emptyList()) }
    var editing by remember { mutableStateOf(false) }
    var distilling by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(noteId) {
        launch { NotesRepository.getNote(noteId).onSuccess { note = it }.onFailure { error = it.message ?: "Couldn't load note" } }
        launch { NotesRepository.getLinks(noteId).onSuccess { links = it } }
        launch { NotesRepository.getRelated(noteId).onSuccess { related = it } }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Mist300)
                }
                Spacer(Modifier.width(4.dp))
                SbLabel("Note")
                Spacer(Modifier.weight(1f))
                if (note != null) {
                    TextButton(onClick = { distilling = !distilling; if (distilling) editing = false }) {
                        Text(if (distilling) "Cancel" else "Distill", color = Gold500)
                    }
                    TextButton(onClick = { editing = !editing; if (editing) distilling = false }) {
                        Text(if (editing) "Cancel" else "Edit", color = Violet400)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete note", tint = Rose400)
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = Rose400, style = SecondBrainTypography.bodySmall)
            }

            val current = note
            when {
                current == null && error == null -> {
                    Spacer(Modifier.height(24.dp))
                    Text("Loading…", color = Mist400, style = SecondBrainTypography.bodyMedium)
                }
                current != null && editing -> {
                    Spacer(Modifier.height(12.dp))
                    NoteEditForm(
                        note = current,
                        onCancel = { editing = false },
                        onSave = { request ->
                            scope.launch {
                                NotesRepository.updateNote(noteId, request)
                                    .onSuccess { updated -> note = updated; editing = false }
                                    .onFailure { error = it.message ?: "Couldn't save changes" }
                            }
                        }
                    )
                }
                current != null && distilling -> {
                    Spacer(Modifier.height(12.dp))
                    DistillForm(
                        note = current,
                        onCancel = { distilling = false },
                        onDistilled = { updated -> note = updated }
                    )
                }
                current != null -> {
                    Spacer(Modifier.height(12.dp))
                    NoteViewBody(current, links, related, onOpenNote, onOpenTag)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this note?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        NotesRepository.deleteNote(noteId).onSuccess { onBack() }
                            .onFailure { error = it.message ?: "Couldn't delete" }
                    }
                }) { Text("Delete", color = Rose400) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteViewBody(
    note: Note,
    links: NoteLinksResponse?,
    related: List<RelatedNote>,
    onOpenNote: (String) -> Unit,
    onOpenTag: (String) -> Unit
) {
    Text(note.title, color = Mist100, style = SecondBrainTypography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Row {
        SbLabel(note.para.uppercase(), color = paraAccent(note.para))
        if (note.distilled) { Spacer(Modifier.width(10.dp)); SbLabel("Distilled", color = Emerald400) }
        if (note.graduated) { Spacer(Modifier.width(10.dp)); SbLabel("Graduated", color = com.secondbrain.lock.ui.theme.Gold500) }
        if (note.pinned) { Spacer(Modifier.width(10.dp)); SbLabel("Pinned", color = Violet400) }
    }

    note.executiveSummary?.takeIf { it.isNotBlank() }?.let { summary ->
        Spacer(Modifier.height(16.dp))
        SbCard(topBorderColor = Emerald400) {
            SbLabel("Executive summary")
            Spacer(Modifier.height(8.dp))
            Text(summary, color = Mist100, style = SecondBrainTypography.bodyMedium)
        }
    }

    note.content?.takeIf { it.isNotBlank() }?.let { content ->
        Spacer(Modifier.height(16.dp))
        SbCard {
            Text(content, color = Mist100, style = SecondBrainTypography.bodyMedium)
        }
    }

    if (note.tags.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            note.tags.forEach { tag ->
                Text(
                    "#$tag",
                    color = Mist400,
                    style = SecondBrainTypography.bodySmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(BorderStroke(1.dp, Ink500), RoundedCornerShape(50))
                        .clickable { onOpenTag(tag) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }

    val hasLinks = links != null && (links.outgoing.isNotEmpty() || links.incoming.isNotEmpty())
    if (hasLinks || related.isNotEmpty()) {
        // One card for all three "linking your thinking" groups instead of three stacked cards —
        // each SbCard's own 20dp padding/border/label was tripling up for what's really one
        // concept (this note's connections), which is what made it feel like it ate the screen.
        Spacer(Modifier.height(16.dp))
        SbCard {
            SbLabel("Linked notes", color = Violet400)
            if (hasLinks) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    LinkGroup(modifier = Modifier.weight(1f), title = "Links to", titles = links!!.outgoing.map { it.title })
                    Spacer(Modifier.width(16.dp))
                    LinkGroup(modifier = Modifier.weight(1f), title = "Linked from", titles = links.incoming.map { it.title })
                }
            }
            if (related.isNotEmpty()) {
                Spacer(Modifier.height(if (hasLinks) 10.dp else 8.dp))
                Text("RELATED", color = Mist400, fontSize = 10.sp, style = SecondBrainTypography.labelSmall)
                Spacer(Modifier.height(4.dp))
                related.take(5).forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenNote(r.id) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            r.title,
                            color = Mist100,
                            style = SecondBrainTypography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${(r.similarity * 100).toInt()}%", color = Mist400, style = SecondBrainTypography.bodySmall)
                    }
                }
            }
        }
    }
}

private const val LINK_GROUP_MAX = 5

@Composable
private fun LinkGroup(modifier: Modifier = Modifier, title: String, titles: List<String>) {
    Column(modifier) {
        Text(title.uppercase(), color = Mist400, fontSize = 10.sp, style = SecondBrainTypography.labelSmall)
        Spacer(Modifier.height(4.dp))
        if (titles.isEmpty()) {
            Text("None yet", color = Mist400, style = SecondBrainTypography.bodySmall)
        } else {
            // Capped like the RELATED list below it — a heavily-linked note listing every
            // connection unbounded is exactly what made this section "take the whole space".
            titles.take(LINK_GROUP_MAX).forEach { title ->
                Text(
                    title,
                    color = Mist100,
                    style = SecondBrainTypography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp).fillMaxWidth()
                )
            }
            if (titles.size > LINK_GROUP_MAX) {
                Text(
                    "+${titles.size - LINK_GROUP_MAX} more",
                    color = Mist400,
                    style = SecondBrainTypography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

private val PARA_OPTIONS = listOf("project", "area", "resource", "archive")

@Composable
private fun NoteEditForm(note: Note, onCancel: () -> Unit, onSave: (UpdateNoteRequest) -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content.orEmpty()) }
    var sourceUrl by remember { mutableStateOf(note.sourceUrl.orEmpty()) }
    var tags by remember { mutableStateOf(note.tags.joinToString(", ")) }
    var para by remember { mutableStateOf(note.para) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Violet400.copy(alpha = 0.6f),
        unfocusedBorderColor = Ink500,
        focusedTextColor = Mist100,
        unfocusedTextColor = Mist100,
        cursorColor = Violet400
    )

    Column {
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("Title") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = content, onValueChange = { content = it },
            label = { Text("Content") },
            modifier = Modifier.fillMaxWidth().height(160.dp), colors = fieldColors
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            PARA_OPTIONS.forEach { option ->
                FilterChip(
                    selected = para == option,
                    onClick = { para = option },
                    label = { Text(option) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = tags, onValueChange = { tags = it },
            label = { Text("Tags (comma separated)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = sourceUrl, onValueChange = { sourceUrl = it },
            label = { Text("Source URL") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = fieldColors
        )
        Spacer(Modifier.height(16.dp))
        Row {
            Button(
                onClick = {
                    onSave(
                        UpdateNoteRequest(
                            title = title.trim().ifBlank { null },
                            content = content,
                            para = para,
                            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            sourceUrl = sourceUrl.ifBlank { null }
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Ink950)
            ) { Text("Save") }
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = onCancel) { Text("Cancel", color = Mist300) }
        }
    }
}

/**
 * Mirrors NoteActionModal.js's 'distill' mode: an executive-summary textarea that PUTs
 * {executive_summary, distilled: true}, plus (once distilled) inline "turn this into
 * something real" actions — add a task with note_id set, or save the summary as a packet.
 */
@Composable
private fun DistillForm(note: Note, onCancel: () -> Unit, onDistilled: (Note) -> Unit) {
    var summary by remember(note.id) { mutableStateOf(note.executiveSummary.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var taskTitle by remember(note.id) { mutableStateOf(note.title) }
    var addingTask by remember { mutableStateOf(false) }
    var addedTasks by remember { mutableStateOf(listOf<String>()) }
    var savingPacket by remember { mutableStateOf(false) }
    var packetSaved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Gold500.copy(alpha = 0.6f),
        unfocusedBorderColor = Ink500,
        focusedTextColor = Mist100,
        unfocusedTextColor = Mist100,
        cursorColor = Gold500
    )

    Column {
        SbLabel("Executive summary", color = Gold500)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = summary, onValueChange = { summary = it },
            placeholder = { Text("Only what feels important, in a few sentences.", color = Mist400) },
            modifier = Modifier.fillMaxWidth().height(140.dp), colors = fieldColors
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error!!, color = Rose400, style = SecondBrainTypography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (summary.isBlank()) return@Button
                    saving = true
                    error = null
                    scope.launch {
                        NotesRepository.updateNote(note.id, UpdateNoteRequest(executiveSummary = summary, distilled = true))
                            .onSuccess { updated -> saving = false; onDistilled(updated) }
                            .onFailure { saving = false; error = it.message ?: "Couldn't save" }
                    }
                },
                enabled = !saving && summary.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Ink950)
            ) { Text(if (saving) "Saving…" else "Save distillation") }
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = onCancel) { Text("Back", color = Mist300) }
        }

        if (note.distilled) {
            Spacer(Modifier.height(20.dp))
            SbLabel("Turn this into something real", color = Mist400)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = taskTitle, onValueChange = { taskTitle = it },
                    placeholder = { Text("Task title", color = Mist400) }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet400.copy(alpha = 0.6f),
                        unfocusedBorderColor = Ink500,
                        focusedTextColor = Mist100,
                        unfocusedTextColor = Mist100,
                        cursorColor = Violet400
                    )
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = !addingTask && taskTitle.isNotBlank(),
                    onClick = {
                        val title = taskTitle.trim()
                        addingTask = true
                        scope.launch {
                            TasksRepository.create(title, noteId = note.id)
                                .onSuccess { addedTasks = addedTasks + title; taskTitle = ""; addingTask = false }
                                .onFailure { addingTask = false; error = it.message ?: "Couldn't add task" }
                        }
                    }
                ) { Text("+ add task", color = Violet400) }
            }
            addedTasks.forEach { t ->
                Text("✓ task added: $t", color = Emerald400, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                enabled = !savingPacket && !packetSaved,
                onClick = {
                    savingPacket = true
                    scope.launch {
                        ApiClient.createPacket(note.id, note.title, summary.ifBlank { note.content })
                            .onSuccess { savingPacket = false; packetSaved = true }
                            .onFailure { savingPacket = false; error = it.message ?: "Couldn't save packet" }
                    }
                }
            ) { Text(if (packetSaved) "✓ saved as packet" else "save as packet", color = Gold500) }
        }
    }
}
