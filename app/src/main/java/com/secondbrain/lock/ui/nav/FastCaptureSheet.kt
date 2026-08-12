package com.secondbrain.lock.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.dto.CreateNoteRequest
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val DRAFT_CACHE_KEY = "draft_capture"

private enum class CaptureMode { NOTE, TASK }

/**
 * The bottom nav's "+" button opens this directly now — one field, one decision (note or task),
 * no PARA picker. Replaces [QuickAddChooserSheet]'s two-step "choose a type, then fill five
 * fields" flow. Every capture files to `para = null` (server defaults new notes to "inbox");
 * sorting into project/area/resource/archive happens later in a batch (P15), never here — that
 * deferred decision is the entire point of this screen.
 *
 * Offline: routes straight through [NotesRepository.create]/[TasksRepository.create], both of
 * which already queue through [com.secondbrain.lock.data.SyncQueue] on a connectivity failure
 * (P1b/P1) — this sheet does NOT build a second, parallel offline mechanism, it just reflects
 * whatever those repositories report back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastCaptureSheet(onDismiss: () -> Unit, onCaptured: () -> Unit, onOpenFullCapture: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(CaptureMode.NOTE) }
    var justSaved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        LocalCache.load<String>(DRAFT_CACHE_KEY)?.let { if (it.isNotBlank()) text = it }
        // Requesting focus before the sheet's own enter animation finishes gets swallowed on
        // API 30-33 — a short delay first is the fix (matches WakeFlowActivity's dialog-timing
        // workarounds elsewhere in this app).
        delay(120)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Persists whatever's left in the field so a captured-but-unsent thought survives a
    // swipe-dismiss or the system back button (both funnel through onDismissRequest) — the whole
    // point of this screen is that nothing typed is ever lost.
    val persistDraftAndDismiss: () -> Unit = {
        scope.launch {
            if (text.isNotBlank()) LocalCache.save(DRAFT_CACHE_KEY, text) else LocalCache.save(DRAFT_CACHE_KEY, "")
        }
        onDismiss()
    }

    val borderColor by animateColorAsState(
        targetValue = if (justSaved) Emerald400 else StreakAccent.copy(alpha = 0.6f),
        animationSpec = tween(200),
        label = "captureFieldBorder"
    )
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(200)
            justSaved = false
        }
    }

    ModalBottomSheet(onDismissRequest = persistDraftAndDismiss, containerColor = Ink900, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("CAPTURE", color = Mist300, style = SecondBrainTypography.labelSmall)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(StreakAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Stub until P12 wires real speech recognition — tapping does nothing yet
                    // rather than half-implementing a broken mic.
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.Mic, contentDescription = "Voice capture", tint = StreakAccent)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                placeholder = { Text("What's on your mind?", color = Mist400) },
                minLines = 3,
                maxLines = 8,
                textStyle = SecondBrainTypography.bodyLarge,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = borderColor,
                    unfocusedBorderColor = Ink500,
                    focusedTextColor = Mist100,
                    unfocusedTextColor = Mist100,
                    cursorColor = StreakAccent
                )
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = Rose400, style = SecondBrainTypography.bodySmall)
            }
            Spacer(Modifier.height(14.dp))
            val onSave: () -> Unit = save@{
                val capturedText = text.trim()
                if (capturedText.isBlank()) return@save
                val title = capturedText.lineSequence().first().trim().take(80).ifBlank { "Untitled" }

                // Optimistic: clear immediately so burst-capture feels instant. Restored
                // below only if the save turns out to have genuinely failed — offline isn't a
                // failure here, NotesRepository/TasksRepository.create already queue it and
                // report success.
                text = ""
                error = null
                justSaved = true
                FeedbackUtil.longPressTick(context)
                scope.launch { LocalCache.save(DRAFT_CACHE_KEY, "") }

                scope.launch {
                    val result = if (mode == CaptureMode.TASK) {
                        TasksRepository.create(title, dueDate = LocalDate.now().toString()).map { }
                    } else {
                        val sourceUrl = extractFirstUrl(capturedText)
                        NotesRepository.create(
                            CreateNoteRequest(
                                title = title,
                                content = capturedText,
                                para = null,
                                sourceUrl = sourceUrl
                            )
                        ).map { }
                    }
                    result.onSuccess { onCaptured() }
                    result.onFailure {
                        text = capturedText
                        error = it.message ?: "Couldn't save — try again"
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1.4f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
                ) { Text("Save") }
                Spacer(Modifier.width(10.dp))
                FilterChip(
                    selected = mode == CaptureMode.NOTE,
                    onClick = { mode = CaptureMode.NOTE },
                    label = { Text("Note") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StreakAccent.copy(alpha = 0.3f))
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = mode == CaptureMode.TASK,
                    onClick = { mode = CaptureMode.TASK },
                    label = { Text("Task") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StreakAccent.copy(alpha = 0.3f))
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenFullCapture) {
                Text("Add details", color = Mist300, style = SecondBrainTypography.bodySmall, fontSize = 12.sp)
            }
        }
    }
}

/** First http(s) URL found in [text], if any — kept in the body rather than stripped out, since
 * users expect to still see what they pasted. */
private fun extractFirstUrl(text: String): String? {
    val matcher = android.util.Patterns.WEB_URL.matcher(text)
    return if (matcher.find()) matcher.group() else null
}
