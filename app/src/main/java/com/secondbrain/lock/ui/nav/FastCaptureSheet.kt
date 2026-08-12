package com.secondbrain.lock.ui.nav

import android.Manifest
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.CaptureHintPrefs
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.data.repo.MindverseRepository
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.dto.CreateNoteRequest
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Mist500
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.util.Permissions
import com.secondbrain.lock.util.VoiceCapture
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
fun FastCaptureSheet(
    onDismiss: () -> Unit,
    onCaptured: () -> Unit,
    onOpenFullCapture: () -> Unit,
    startListening: Boolean = false
) {
    var text by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(CaptureMode.NOTE) }
    var justSaved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // P12 — gated per spec: hidden (not disabled/erroring) when recognition genuinely isn't
    // available on this device/locale, or when a Mindcord call is active and already holds the
    // mic (MindverseRepository.currentRoom is the same "am I in a call" signal MindverseRoomScreen
    // itself reads — there's no separate global call-state object to duplicate).
    val voiceCapture = remember { VoiceCapture(context) }
    val inCall by MindverseRepository.currentRoom.collectAsState()
    val recognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val micAvailable = recognitionAvailable && inCall == null
    // P6a's discoverability hint, completed here since long-press-to-speak wasn't functional
    // until this prompt — first two sheet opens only, then never again.
    val showVoiceHint = remember { CaptureHintPrefs.recordOpenAndShouldShowVoiceHint(context) }
    var pendingVoiceStart by remember { mutableStateOf(false) }
    val requestMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingVoiceStart) startVoice(voiceCapture, context) { finalText -> text = appendVoiceResult(text, finalText) }
        pendingVoiceStart = false
    }
    val onMicTap: () -> Unit = {
        when {
            voiceCapture.isListening -> voiceCapture.stop()
            Permissions.hasMicrophone(context) -> startVoice(voiceCapture, context) { finalText -> text = appendVoiceResult(text, finalText) }
            else -> {
                pendingVoiceStart = true
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(Unit) {
        LocalCache.load<String>(DRAFT_CACHE_KEY)?.let { if (it.isNotBlank()) text = it }
        // Requesting focus before the sheet's own enter animation finishes gets swallowed on
        // API 30-33 — a short delay first is the fix (matches WakeFlowActivity's dialog-timing
        // workarounds elsewhere in this app).
        delay(120)
        focusRequester.requestFocus()
        keyboardController?.show()
        // P6a's long-press entry: open already listening. If gating blocks voice (unavailable,
        // in a call) or the mic permission isn't granted yet, this just falls through to a normal
        // typing-ready sheet instead — per P6a's own fallback, never a broken/stuck control.
        if (startListening && micAvailable && Permissions.hasMicrophone(context)) {
            startVoice(voiceCapture, context) { finalText -> text = appendVoiceResult(text, finalText) }
        }
    }

    // Persists whatever's left in the field so a captured-but-unsent thought survives a
    // swipe-dismiss or the system back button (both funnel through onDismissRequest) — the whole
    // point of this screen is that nothing typed is ever lost. Dismissing mid-listen merges
    // whatever's been heard SO FAR into the saved draft first — stop()'s own onResults is async
    // and won't land before onDismiss() below returns, so waiting for it risks losing it entirely.
    val persistDraftAndDismiss: () -> Unit = {
        val finalDraft = if (voiceCapture.isListening) {
            voiceCapture.stop()
            appendVoiceResult(text, voiceCapture.partialText)
        } else text
        scope.launch {
            if (finalDraft.isNotBlank()) LocalCache.save(DRAFT_CACHE_KEY, finalDraft) else LocalCache.save(DRAFT_CACHE_KEY, "")
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
                if (micAvailable) {
                    val micFillColor by animateColorAsState(
                        targetValue = StreakAccent.copy(alpha = if (voiceCapture.isListening) 0.3f else 0.15f),
                        label = "micFill"
                    )
                    val micScale = if (voiceCapture.isListening) {
                        val infiniteTransition = rememberInfiniteTransition(label = "mic")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                            label = "micScale"
                        )
                        scale
                    } else 1f
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .scale(micScale)
                            .clip(CircleShape)
                            .background(micFillColor),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onMicTap) {
                            Icon(Icons.Filled.Mic, contentDescription = "Voice capture", tint = StreakAccent)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // While listening, the whole field renders italic/Mist300 to visually mark it as
            // live/uncommitted transcript rather than settled text — reverting to regular Mist100
            // the instant a final result (or a manual stop) commits it into `text` proper.
            val displayText = if (voiceCapture.isListening) {
                if (text.isBlank()) voiceCapture.partialText else "$text ${voiceCapture.partialText}"
            } else text
            OutlinedTextField(
                value = displayText,
                onValueChange = { if (!voiceCapture.isListening) { text = it; error = null } },
                readOnly = voiceCapture.isListening,
                placeholder = { Text("What's on your mind?", color = Mist400) },
                minLines = 3,
                maxLines = 8,
                textStyle = if (voiceCapture.isListening) {
                    SecondBrainTypography.bodyLarge.copy(fontStyle = FontStyle.Italic)
                } else SecondBrainTypography.bodyLarge,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = borderColor,
                    unfocusedBorderColor = Ink500,
                    focusedTextColor = if (voiceCapture.isListening) Mist300 else Mist100,
                    unfocusedTextColor = if (voiceCapture.isListening) Mist300 else Mist100,
                    cursorColor = StreakAccent
                )
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = Rose400, style = SecondBrainTypography.bodySmall)
            }
            if (showVoiceHint && micAvailable) {
                Spacer(Modifier.height(8.dp))
                Text("Tip: hold the button to speak instead.", color = Mist500, style = SecondBrainTypography.bodySmall)
            }
            Spacer(Modifier.height(14.dp))
            val onSave: () -> Unit = save@{
                // Same reasoning as persistDraftAndDismiss — merge whatever's been heard so far
                // rather than losing it because stop()'s onResults won't land before this saves.
                if (voiceCapture.isListening) {
                    voiceCapture.stop()
                    text = appendVoiceResult(text, voiceCapture.partialText)
                }
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

/** [VoiceCapture.start] wrapped with the one error behavior spec requires everywhere it's called
 * from in this file: never touch the field's already-typed text, just toast and drop back to
 * idle so the user can type instead — a capture must never be lost to a recognition failure. */
private fun startVoice(voiceCapture: VoiceCapture, context: android.content.Context, onFinalResult: (String) -> Unit) {
    voiceCapture.start(
        onFinalResult = onFinalResult,
        onError = { Toast.makeText(context, "Didn't catch that — type it?", Toast.LENGTH_SHORT).show() }
    )
}

/** Appends a voice result onto whatever was already typed, rather than replacing it — so typing
 * a bit and then also speaking doesn't throw away the typed part. */
private fun appendVoiceResult(existing: String, spoken: String): String =
    if (existing.isBlank()) spoken else "$existing $spoken"
