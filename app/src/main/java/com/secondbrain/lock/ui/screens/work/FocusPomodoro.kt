package com.secondbrain.lock.ui.screens.work

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.secondbrain.lock.data.FocusState
import com.secondbrain.lock.data.repo.StatsRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.network.dto.TaskPiece
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.Violet400
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class FocusUiState { PICK, RUNNING }

/**
 * Starts a real focus/Pomodoro session via POST /api/focus/state — the same state
 * MonitorService's focus poll already reads to drive the Shield-side lock overlay, and logs
 * completion via POST /api/activity/focus on the way out.
 *
 * Renders full-screen (a Dialog with usePlatformDefaultWidth = false rather than a small modal
 * card) since a running focus clock is meant to take over the screen, matching the web app's
 * FocusPomodoro view. Also mirrors the web's "break it into pieces" checklist (persisted on the
 * task's `pieces` field via PUT /api/tasks/:id), visible in both the picker and running screens.
 */
@Composable
fun FocusPomodoroDialog(
    task: Task,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
    // Cross-device mirroring (TodayCards.js's remote-focus poll): when a session for this exact
    // task is already running on another client, WorkScreen/TasksPanel pass it here so the dialog
    // opens straight into the running clock instead of the picker — no second startFocusSession
    // call, since one's already live server-side.
    resume: FocusState? = null
) {
    var minutes by remember {
        mutableStateOf(
            resume?.let { r ->
                val started = r.startedAtMillis
                val ends = r.endsAtMillis
                if (started != null && ends != null) (((ends - started) / 60_000L).toInt()).coerceAtLeast(1) else 25
            } ?: 25
        )
    }
    var state by remember { mutableStateOf(if (resume?.active == true) FocusUiState.RUNNING else FocusUiState.PICK) }
    var sessionId by remember { mutableStateOf(resume?.sessionId) }
    var endsAtMillis by remember { mutableLongStateOf(resume?.endsAtMillis ?: 0L) }
    var remainingMs by remember { mutableLongStateOf(0L) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pieces by remember { mutableStateOf(task.pieces ?: emptyList()) }
    val scope = rememberCoroutineScope()

    fun updatePieces(next: List<TaskPiece>) {
        pieces = next
        scope.launch { TasksRepository.setPieces(task.id, next) }
    }

    // Closing mid-session (X, back, or tapping outside) abandons the session the same way "End
    // early" does — both cancel the actual /api/focus/state session, not just clear the "don't
    // notify me" signal the reminders evaluator reads. Without the cancel call, the session stayed
    // "active" server-side after the dialog closed, so MonitorService's background poll (which
    // drives the cross-device focus pill) would still report it completed once its original end
    // time arrived, silently crediting focus minutes for a session the user had actually left.
    fun closeDialog() {
        if (state == FocusUiState.RUNNING) {
            scope.launch {
                ApiClient.cancelFocusSession()
                ApiClient.postFocusState(false, null)
            }
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::closeDialog,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().fullAuraBackground()) {
            // Declared *after* the Column below (both are Box siblings) so it draws on top of —
            // and, crucially, hit-tests before — the Column's own fillMaxSize()+verticalScroll()
            // bounds, which otherwise cover this exact corner too and silently swallowed every tap
            // here: the icon was visible (the Column paints nothing under its own top padding) but
            // untouchable, since Compose dispatches a tap to whichever sibling is on top, and a
            // same-size sibling declared later always wins there regardless of what it paints.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 64.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    FocusUiState.PICK -> {
                        SbLabel("Focus", color = Violet400)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            task.title,
                            color = Mist100,
                            style = SecondBrainTypography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(28.dp))
                        Row {
                            listOf(15, 25, 45).forEach { m ->
                                FilterChip(
                                    selected = minutes == m,
                                    onClick = { minutes = m },
                                    label = { Text("$m min") },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Violet400.copy(alpha = 0.3f))
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(error!!, color = Mist300, style = SecondBrainTypography.bodySmall)
                        }
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = {
                                starting = true
                                error = null
                                scope.launch {
                                    val result = ApiClient.startFocusSession(minutes, task.id.ifBlank { null })
                                    starting = false
                                    result.onSuccess { focusState ->
                                        sessionId = focusState.sessionId
                                        endsAtMillis = focusState.endsAtMillis
                                            ?: (System.currentTimeMillis() + minutes * 60_000L)
                                        state = FocusUiState.RUNNING
                                        val endsAtIso = java.time.Instant.ofEpochMilli(endsAtMillis).toString()
                                        scope.launch { ApiClient.postFocusState(true, endsAtIso) }
                                        FocusSounds.start()
                                    }
                                    result.onFailure { error = it.message ?: "Couldn't start focus session" }
                                }
                            },
                            enabled = !starting,
                            colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Ink950),
                            modifier = Modifier.width(200.dp)
                        ) {
                            if (starting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Ink950, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Starting…")
                            } else {
                                Text("Start focus")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = ::closeDialog) { Text("Cancel", color = Mist300) }
                    }

                    FocusUiState.RUNNING -> {
                        // Cross-device: once running, this dialog only counted down locally with
                        // no way to notice the session being stopped/cancelled from another
                        // client (e.g. the web app) — it would just keep ticking until its own
                        // local clock ran out. Poll the server's focus state so an out-of-band
                        // stop closes this dialog instead of silently drifting from reality.
                        LaunchedEffect(sessionId) {
                            while (isActive) {
                                delay(8_000)
                                val remote = ApiClient.getFocusState().getOrNull() ?: continue
                                val stoppedElsewhere = !remote.active ||
                                    (sessionId != null && remote.sessionId != null && remote.sessionId != sessionId)
                                if (stoppedElsewhere) {
                                    onDismiss()
                                    break
                                }
                            }
                        }
                        RunningFocusTimer(
                            endsAtMillis = endsAtMillis,
                            onTick = { remainingMs = it },
                            onFinished = {
                                scope.launch {
                                    ApiClient.postFocusActivity("focus", minutes, task.id.ifBlank { null }, sessionId)
                                    ApiClient.postFocusState(false, null)
                                }
                                FocusSounds.complete()
                                // Session-count bump happens once, in WorkScreen.handleCompletion("focus") — bumping it
                                // here too would double-count it locally. Minutes aren't known there, so bump those here.
                                StatsRepository.bumpFocusMinutes(minutes)
                                onCompleted()
                            }
                        )
                        val totalMs = (minutes * 60_000L).coerceAtLeast(1L)
                        val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
                        val mm = totalSeconds / 60
                        val ss = totalSeconds % 60
                        val pct = (1f - remainingMs.coerceIn(0L, totalMs).toFloat() / totalMs.toFloat())

                        Text(
                            task.title,
                            color = Mist100,
                            style = SecondBrainTypography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(36.dp))
                        PomodoroRing(pct = pct, modifier = Modifier.size(280.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "%02d:%02d".format(mm, ss),
                                    style = SecondBrainTypography.displayLarge.copy(fontSize = 56.sp),
                                    color = Emerald400
                                )
                                Spacer(Modifier.height(4.dp))
                                SbLabel("Focus", color = Mist300)
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    ApiClient.cancelFocusSession()
                                    ApiClient.postFocusState(false, null)
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist300)
                        ) { Text("End early") }
                    }
                }

                Spacer(Modifier.height(40.dp))
                PiecesSection(pieces = pieces, onPiecesChange = ::updatePieces)
            }

            IconButton(onClick = ::closeDialog, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Mist300)
            }
        }
    }
}

/** Mirrors web FocusPomodoro.js's "Break it into pieces" checklist. */
@Composable
private fun PiecesSection(pieces: List<TaskPiece>, onPiecesChange: (List<TaskPiece>) -> Unit) {
    var newPiece by remember { mutableStateOf("") }

    fun addPiece() {
        val text = newPiece.trim()
        if (text.isEmpty()) return
        onPiecesChange(pieces + TaskPiece(id = System.currentTimeMillis().toString(), text = text))
        newPiece = ""
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        SbLabel("Break it into pieces", color = Mist300)
        Spacer(Modifier.height(12.dp))

        if (pieces.isEmpty()) {
            Text(
                "Split this task into smaller steps if it helps.",
                color = Mist300,
                style = SecondBrainTypography.bodySmall
            )
        }
        pieces.forEach { piece ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = piece.done,
                    onCheckedChange = { checked ->
                        onPiecesChange(pieces.map { if (it.id == piece.id) it.copy(done = checked) else it })
                    },
                    colors = CheckboxDefaults.colors(checkedColor = Emerald400)
                )
                Text(
                    piece.text,
                    color = if (piece.done) Mist300 else Mist100,
                    style = SecondBrainTypography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onPiecesChange(pieces.filterNot { it.id == piece.id }) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Mist300)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPiece,
                onValueChange = { newPiece = it },
                placeholder = { Text("Add a piece…") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Ink900, focusedContainerColor = Ink900),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = ::addPiece) { Text("Add") }
        }
    }
}

@Composable
private fun PomodoroRing(pct: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val trackColor = Ink700
    val progressColor = Emerald400
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 14.dp.toPx()
            val diameter = size.minDimension - strokeWidthPx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * pct.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
private fun RunningFocusTimer(endsAtMillis: Long, onTick: (Long) -> Unit, onFinished: () -> Unit) {
    LaunchedEffect(endsAtMillis) {
        while (true) {
            val remaining = endsAtMillis - System.currentTimeMillis()
            onTick(remaining)
            if (remaining <= 0) {
                onFinished()
                break
            }
            delay(1000)
        }
    }
}

/** Stand-in for lib/sounds.js's pomodoroStart/pomodoroEnd cues — two short system tones rather
 * than porting the full cue set (pause/resume/paused-reminder), which needs real audio assets. */
private object FocusSounds {
    private fun beep(type: Int, durationMs: Int) {
        runCatching {
            val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(type, durationMs)
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ runCatching { tg.release() } }, durationMs + 200L)
        }
    }

    fun start() = beep(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
    fun complete() = beep(android.media.ToneGenerator.TONE_PROP_BEEP2, 400)
}
