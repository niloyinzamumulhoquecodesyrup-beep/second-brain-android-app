package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.network.dto.CreateNoteRequest
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import kotlinx.coroutines.launch

private const val TITLE_MAX = 30

private val PARA_CAPTURE_OPTIONS = listOf(
    "project" to "project",
    "area" to "area",
    "resource" to "resource",
    "archive" to "archive"
)

/**
 * POST /api/notes — the "+ Capture" entry point from Organize's header. Mirrors
 * CaptureSection.js: title (30-char limit + counter), content, PARA destination picker,
 * source URL, tags. Stays open after a successful save (form resets, shows a "Saved…"
 * status) so you can burst-capture several notes — closed only via the explicit X or back.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureSheet(onDismiss: () -> Unit, onCaptured: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var para by remember { mutableStateOf("project") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Emerald400.copy(alpha = 0.6f),
        unfocusedBorderColor = Ink500,
        focusedTextColor = Mist100,
        unfocusedTextColor = Mist100,
        cursorColor = Emerald400
    )

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink900) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SbLabel("Capture")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Mist400)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= TITLE_MAX) title = it },
                    placeholder = { Text("Title", color = Mist400) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
                Text(
                    "${title.length}/$TITLE_MAX",
                    color = Mist400,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("What's on your mind?", color = Mist400) },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                colors = fieldColors
            )
            Spacer(Modifier.height(10.dp))
            Text("Where should this go?", color = Mist400, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PARA_CAPTURE_OPTIONS.forEach { (value, label) ->
                    FilterChip(selected = para == value, onClick = { para = value }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = sourceUrl,
                onValueChange = { sourceUrl = it },
                placeholder = { Text("Source URL (optional)", color = Mist400) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                placeholder = { Text("Tags (comma separated)", color = Mist400) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = Rose400, style = SecondBrainTypography.bodySmall)
            }
            if (status != null) {
                Spacer(Modifier.height(8.dp))
                Text(status!!, color = Emerald400, style = SecondBrainTypography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val finalTitle = title.trim().ifBlank { "Untitled" }
                    saving = true
                    error = null
                    status = null
                    scope.launch {
                        val result = NotesRepository.create(
                            CreateNoteRequest(
                                title = finalTitle,
                                content = content.ifBlank { null },
                                tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { null },
                                para = para,
                                sourceUrl = sourceUrl.trim().ifBlank { null }
                            )
                        )
                        saving = false
                        result.onSuccess {
                            title = ""; content = ""; tags = ""; sourceUrl = ""; para = "project"
                            status = "Saved as a knowledge asset."
                            onCaptured()
                        }
                        result.onFailure { error = it.message ?: "Couldn't save — try again" }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Ink950)
            ) { Text(if (saving) "Saving…" else "Capture") }
        }
    }
}
