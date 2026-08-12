package com.secondbrain.lock.ui.screens.work

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.data.FocusState
import com.secondbrain.lock.data.PendingOp
import com.secondbrain.lock.data.SyncQueue
import com.secondbrain.lock.data.repo.StatsRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.network.dto.TaskPiece
import com.secondbrain.lock.ui.components.BreakdownRow
import com.secondbrain.lock.ui.components.BreakdownSuggestions
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold400
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.Violet400
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// P14: READY (breakdown-before-timer anti-avoidance screen) -> RUNNING -> MICRO_DONE (only for
// the "Just 2 minutes" path, offering to keep going before the dialog closes). A regular session
// finishing goes straight from RUNNING to calling onCompleted() same as before P14.
private enum class FocusUiState { READY, RUNNING, MICRO_DONE }

private val DURATION_CHOICES = listOf(15, 25, 45)

/**
 * Starts a real focus/Pomodoro session via POST /api/focus/state — the same state
 * MonitorService's focus poll already reads to drive the Shield-side lock overlay, and logs
 * completion via POST /api/activity/focus on the way out.
 *
 * Renders full-screen (a Dialog with usePlatformDefaultWidth = false rather than a small modal
 * card) since a running focus clock is meant to take over the screen, matching the web app's
 * FocusPomodoro view. The READY screen (P14) puts "break it into steps" BEFORE the timer starts,
 * rather than below a running clock the user already committed to — the AI-breakdown suggestions
 * there land in this task's own `pieces` checklist, NOT new sibling tasks (contrast with
 * TasksPanel/AllTasksScreen's breakdown, which schedules real tasks — see
 * [TaskBreakdownController]'s KDoc). An escape hatch redirects the same suggestions through that
 * scheduled-task path when the task genuinely is several separate work items.
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
    var state by remember { mutableStateOf(if (resume?.active == true) FocusUiState.RUNNING else FocusUiState.READY) }
    var isMicroSession by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf(resume?.sessionId) }
    var endsAtMillis by remember { mutableLongStateOf(resume?.endsAtMillis ?: 0L) }
    var remainingMs by remember { mutableLongStateOf(0L) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // pieces[0] used to be just "whatever the user typed first" — P14 gives it a dedicated
    // "first physical thing" field instead (see below), so a pre-existing first piece is read
    // into that field and the rest of the checklist starts from index 1. Degrades sensibly for
    // tasks whose pieces predate this change; nothing is lost, just re-homed.
    var firstThingText by remember { mutableStateOf(task.pieces?.firstOrNull()?.text ?: "") }
    var pieces by remember { mutableStateOf(task.pieces?.drop(1) ?: emptyList()) }
    var stepsExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // AI-breakdown state for the "append to this task's pieces" destination — separate from
    // TaskBreakdownController (TasksPanel/AllTasksScreen), which schedules real sibling tasks.
    // Kept as plain composable state, single-consumer, rather than a second controller class.
    var suggestLoading by remember { mutableStateOf(false) }
    var suggestError by remember { mutableStateOf<String?>(null) }
    var suggestRows by remember { mutableStateOf<List<BreakdownRow>>(emptyList()) }
    var suggestRemaining by remember { mutableStateOf<Int?>(null) }
    var suggestFurtherLoadingId by remember { mutableStateOf<String?>(null) }
    // Escape hatch ("Make these separate tasks instead") redirects the SAME already-fetched rows
    // into the scheduled-task destination without a second AI call.
    val escapeController = rememberTaskBreakdownController()

    fun updatePieces(next: List<TaskPiece>) {
        pieces = next
        scope.launch { TasksRepository.setPieces(task.id, next) }
    }

    // Committed once, when a session actually starts — not on every "+" — per P13/P14's shared
    // rule that adding a suggestion is a local, free action until the user commits to it.
    fun commitPieces() {
        val finalPieces = if (firstThingText.isNotBlank()) {
            listOf(TaskPiece(id = "first-${task.id}-${System.currentTimeMillis()}", text = firstThingText)) + pieces
        } else pieces
        pieces = finalPieces
        scope.launch { TasksRepository.setPieces(task.id, finalPieces) }
    }

    fun requestSuggestions() {
        if (suggestLoading) return
        if (suggestRemaining == 0) {
            suggestError = "0 task breakdowns left today"
            return
        }
        suggestError = null
        suggestLoading = true
        scope.launch {
            val result = TasksRepository.breakdown(task.title)
            suggestLoading = false
            result.onSuccess { resp ->
                suggestRows = resp.subtasks.map { BreakdownRow(subtask = it) }
                suggestRemaining = resp.remainingToday
                // Suggest, never enforce — a one-time nudge toward the closest duration chip if
                // the suggested total won't fit the one currently selected; the user can always
                // pick a different chip afterward, this doesn't re-fire on every recomposition.
                val total = resp.subtasks.sumOf { it.estimatedMinutes }
                if (total > minutes) {
                    minutes = DURATION_CHOICES.minByOrNull { kotlin.math.abs(it - total) } ?: minutes
                }
            }.onFailure {
                suggestError = it.message ?: "Breakdown failed"
            }
        }
    }

    // A suggestion becomes the "first physical thing" if that field is still empty (the field IS
    // conceptually pieces[0] — see commitPieces), otherwise it's appended to the checklist below.
    fun addSuggestion(row: BreakdownRow) {
        if (firstThingText.isBlank()) {
            firstThingText = row.subtask.topic
        } else {
            pieces = pieces + TaskPiece(id = row.id, text = row.subtask.topic)
        }
        suggestRows = suggestRows.filterNot { it.id == row.id }
    }

    fun addAllSuggestions() {
        suggestRows.forEach { row ->
            if (firstThingText.isBlank()) firstThingText = row.subtask.topic
            else pieces = pieces + TaskPiece(id = row.id, text = row.subtask.topic)
        }
        suggestRows = emptyList()
    }

    fun discardSuggestion(row: BreakdownRow) {
        suggestRows = suggestRows.filterNot { it.id == row.id }
    }

    fun breakdownSuggestionFurther(row: BreakdownRow) {
        if (suggestFurtherLoadingId != null) return
        if (suggestRemaining == 0) return
        suggestFurtherLoadingId = row.id
        scope.launch {
            val result = TasksRepository.breakdown(row.subtask.topic)
            suggestFurtherLoadingId = null
            result.onSuccess { resp ->
                val index = suggestRows.indexOfFirst { it.id == row.id }
                if (index == -1) return@onSuccess
                val expanded = resp.subtasks.map { BreakdownRow(subtask = it) }
                suggestRows = suggestRows.toMutableList().apply {
                    removeAt(index)
                    addAll(index, expanded)
                }
                suggestRemaining = resp.remainingToday
            }
        }
    }

    fun makeSuggestionsSeparateTasks() {
        escapeController.seed(task, suggestRows, suggestRemaining)
        suggestRows = emptyList()
    }

    // Recomputed from endsAtMillis/System.currentTimeMillis() (not the ticked remainingMs state)
    // so it's correct even if this fires before RunningFocusTimer's first tick has landed.
    fun elapsedFocusMinutes(): Int {
        val remaining = endsAtMillis - System.currentTimeMillis()
        val elapsedMs = (minutes * 60_000L) - remaining
        return (elapsedMs / 60_000L).toInt().coerceAtLeast(0)
    }

    // Attempts the log directly (covers both "always been online" and "started offline but back
    // online by now"); only on failure does it fall back to SyncQueue, covering "still offline" —
    // one path for every connectivity shape instead of branching on how the session started.
    // sessionId is deliberately NOT queued/resent here: it only ever identifies a *server-known*
    // session (null for one that was started locally, see the offline branch of "Start focus"
    // below), and the queued retry has no server session to correlate against either way.
    fun logFocusActivity(elapsedMinutes: Int, mode: String) {
        if (elapsedMinutes < 1) return
        // Deliberately NOT `scope` (rememberCoroutineScope()): every caller of this — "End early",
        // the X/back close path, natural completion — dismisses the dialog right after calling it,
        // which tears down the composition and cancels `scope` along with it. That raced away the
        // network attempt (and, worse, the offline SyncQueue.enqueue fallback that's supposed to
        // guarantee this doesn't get lost) before either could finish. A short-lived scope of its
        // own survives the dialog closing, same pattern as WakeAlarmReceiver/BootReceiver's
        // fire-and-forget work.
        CoroutineScope(Dispatchers.Default).launch {
            val result = ApiClient.postFocusActivity(mode, elapsedMinutes, task.id.ifBlank { null }, sessionId)
            if (result.isFailure) {
                SyncQueue.enqueue(
                    PendingOp(
                        id = SyncQueue.newOpId(),
                        type = PendingOp.TYPE_LOG_FOCUS_ACTIVITY,
                        createdAt = System.currentTimeMillis(),
                        taskId = task.id.ifBlank { null },
                        mode = mode,
                        minutes = elapsedMinutes
                    )
                )
            }
        }
    }

    // Ending a session before it naturally completes still credits every full minute actually
    // spent — it just doesn't resume later and doesn't count as a finished "session" (no
    // session-count bump, no celebration), since the pomodoro itself wasn't seen through.
    fun logPartialFocus() {
        val elapsed = elapsedFocusMinutes()
        logFocusActivity(elapsed, if (isMicroSession) "micro" else "focus")
        if (elapsed >= 1) StatsRepository.bumpFocusMinutes(elapsed)
    }

    // Closing mid-session (X, back, or tapping outside) abandons the session the same way "End
    // early" does — both cancel the actual /api/focus/state session (so a later resume never picks
    // it back up) rather than just clearing the "don't notify me" signal the reminders evaluator
    // reads. Without the cancel call, the session stayed "active" server-side after the dialog
    // closed, so MonitorService's background poll (which drives the cross-device focus pill) would
    // still report it completed once its original end time arrived, on top of double-crediting the
    // minutes [logPartialFocus] above just logged. Skipped entirely for a session that only exists
    // locally (sessionId == null, started while offline) — there's nothing server-side to cancel.
    fun closeDialog() {
        if (state == FocusUiState.RUNNING) {
            logPartialFocus()
            if (sessionId != null) {
                scope.launch {
                    ApiClient.cancelFocusSession()
                    ApiClient.postFocusState(false, null)
                }
            }
        }
        onDismiss()
    }

    // Shared by the READY screen's "Start"/"Just 2 minutes" and MICRO_DONE's "Another 25" — only
    // the READY-originated calls commit the pieces checklist (once, per commitPieces' own KDoc);
    // "Another 25" starts mid-flow after that already happened.
    fun beginSession(targetMinutes: Int, micro: Boolean, commitFirstThing: Boolean) {
        if (commitFirstThing) commitPieces()
        isMicroSession = micro
        minutes = targetMinutes
        starting = true
        error = null
        scope.launch {
            val result = ApiClient.startFocusSession(targetMinutes, task.id.ifBlank { null }, if (micro) "micro" else "focus")
            starting = false
            result.onSuccess { focusState ->
                sessionId = focusState.sessionId
                endsAtMillis = focusState.endsAtMillis
                    ?: (System.currentTimeMillis() + targetMinutes * 60_000L)
                state = FocusUiState.RUNNING
                val endsAtIso = java.time.Instant.ofEpochMilli(endsAtMillis).toString()
                scope.launch { ApiClient.postFocusState(true, endsAtIso) }
                FocusSounds.start()
            }
            result.onFailure { failure ->
                if (ApiClient.isOffline()) {
                    // No server session to attach to — sessionId stays null, which is what the
                    // cross-device poll and the cancel/postFocusState calls above key off of to
                    // skip themselves for a session the server never knew about.
                    sessionId = null
                    endsAtMillis = System.currentTimeMillis() + targetMinutes * 60_000L
                    state = FocusUiState.RUNNING
                    FocusSounds.start()
                } else {
                    error = failure.message ?: "Couldn't start focus session"
                }
            }
        }
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
                    FocusUiState.READY -> {
                        SbLabel("Focus", color = Violet400)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            task.title,
                            color = Mist100,
                            style = SecondBrainTypography.headlineMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 3
                        )
                        Spacer(Modifier.height(28.dp))

                        Text(
                            "What's the first physical thing you'll do?",
                            color = Mist300,
                            style = SecondBrainTypography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = firstThingText,
                            onValueChange = { firstThingText = it },
                            placeholder = { Text("e.g. open the doc") },
                            singleLine = true,
                            textStyle = SecondBrainTypography.bodyLarge,
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Ink800, focusedContainerColor = Ink800),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(20.dp))
                        Row {
                            DURATION_CHOICES.forEach { m ->
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
                            onClick = { beginSession(minutes, micro = false, commitFirstThing = true) },
                            enabled = !starting,
                            colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Ink950),
                            modifier = Modifier.width(240.dp).height(56.dp)
                        ) {
                            if (starting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Ink950, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Starting…")
                            } else {
                                Text("Start")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { beginSession(2, micro = true, commitFirstThing = true) }, enabled = !starting) {
                            Text("Just 2 minutes →", color = Mist100)
                        }
                        TextButton(onClick = { stepsExpanded = !stepsExpanded }) {
                            Text(if (stepsExpanded) "Hide steps" else "Break it into steps →", color = Mist300)
                        }

                        if (stepsExpanded) {
                            FocusStepsSection(
                                pieces = pieces,
                                onPiecesChange = ::updatePieces,
                                selectedMinutes = minutes,
                                suggestLoading = suggestLoading,
                                suggestError = suggestError,
                                suggestRows = suggestRows,
                                suggestRemaining = suggestRemaining,
                                suggestFurtherLoadingId = suggestFurtherLoadingId,
                                offline = ApiClient.isOffline(),
                                onSuggest = ::requestSuggestions,
                                onAddSuggestion = ::addSuggestion,
                                onDiscardSuggestion = ::discardSuggestion,
                                onAddAllSuggestions = ::addAllSuggestions,
                                onDiscardAllSuggestions = { suggestRows = emptyList() },
                                onBreakdownFurther = ::breakdownSuggestionFurther,
                                onMakeSeparateTasks = ::makeSuggestionsSeparateTasks
                            )
                            if (escapeController.taskId == task.id) {
                                TaskBreakdownBlock(escapeController)
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
                        // No poll for a session that only exists locally (sessionId == null,
                        // started while offline) — there's no server record to drift from yet.
                        if (sessionId != null) {
                            LaunchedEffect(sessionId) {
                                while (isActive) {
                                    delay(8_000)
                                    val remote = ApiClient.getFocusState().getOrNull() ?: continue
                                    val stoppedElsewhere = !remote.active ||
                                        (remote.sessionId != null && remote.sessionId != sessionId)
                                    // P14: a micro session's own onFinished marks the session
                                    // inactive server-side (postFocusState(false, null)) as part of
                                    // normal completion, then moves state to MICRO_DONE WITHOUT
                                    // unmounting this dialog — unlike a regular session, where
                                    // onCompleted() tears the whole dialog down and this effect
                                    // along with it. Without this guard, this poll's very next
                                    // tick sees the inactive session it just watched end and reads
                                    // it as "stopped elsewhere," dismissing the "Want to keep
                                    // going?" screen before it's ever seen. Only treat it as a
                                    // foreign stop while still actually running.
                                    if (stoppedElsewhere && state == FocusUiState.RUNNING) {
                                        onDismiss()
                                        break
                                    }
                                }
                            }
                        }
                        val totalMs = (minutes * 60_000L).coerceAtLeast(1L)
                        RunningFocusTimer(
                            endsAtMillis = endsAtMillis,
                            totalMs = totalMs,
                            onTick = { remainingMs = it },
                            onMilestone = { FeedbackUtil.spinTick(context) },
                            onFinished = {
                                val mode = if (isMicroSession) "micro" else "focus"
                                logFocusActivity(minutes, mode)
                                if (sessionId != null) scope.launch { ApiClient.postFocusState(false, null) }
                                FocusSounds.complete()
                                // Session-count bump happens once, in WorkScreen.handleCompletion("focus") — bumping it
                                // here too would double-count it locally. Minutes aren't known there, so bump those here.
                                StatsRepository.bumpFocusMinutes(minutes)
                                if (isMicroSession) {
                                    // Both "Another 25" and "I'm good" are wins (P14) — the minutes
                                    // are already logged above either way. This just decides
                                    // whether the dialog closes now or a real session follows.
                                    state = FocusUiState.MICRO_DONE
                                } else {
                                    onCompleted()
                                }
                            }
                        )
                        val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
                        val mm = totalSeconds / 60
                        val ss = totalSeconds % 60
                        val remFrac = (remainingMs.coerceIn(0L, totalMs).toFloat() / totalMs.toFloat())

                        Text(
                            task.title,
                            color = Mist100,
                            style = SecondBrainTypography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(36.dp))
                        PomodoroRing(remFrac = remFrac, remainingMs = remainingMs, modifier = Modifier.size(280.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "%02d:%02d".format(mm, ss),
                                    // Smaller than before (was 56sp) — the shrinking ring is the
                                    // primary signal now, not the digits (P14: less clock-watching).
                                    style = SecondBrainTypography.displayLarge.copy(fontSize = 32.sp),
                                    color = Emerald400
                                )
                                Spacer(Modifier.height(4.dp))
                                SbLabel("Focus", color = Mist300)
                            }
                        }
                        val currentStep = pieces.firstOrNull { !it.done }
                        if (currentStep != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Now: ${currentStep.text}",
                                color = Mist300,
                                style = SecondBrainTypography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(40.dp))
                        OutlinedButton(
                            onClick = {
                                logPartialFocus()
                                if (sessionId != null) {
                                    scope.launch {
                                        ApiClient.cancelFocusSession()
                                        ApiClient.postFocusState(false, null)
                                    }
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist300)
                        ) { Text("End early") }
                    }

                    FocusUiState.MICRO_DONE -> {
                        Text(
                            task.title,
                            color = Mist100,
                            style = SecondBrainTypography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Want to keep going?",
                            color = Mist100,
                            style = SecondBrainTypography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = { beginSession(25, micro = false, commitFirstThing = false) },
                            enabled = !starting,
                            colors = ButtonDefaults.buttonColors(containerColor = Violet400, contentColor = Ink950),
                            modifier = Modifier.width(240.dp).height(56.dp)
                        ) {
                            if (starting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Ink950, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Starting…")
                            } else {
                                Text("Another 25")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onCompleted) { Text("I'm good", color = Mist300) }
                    }
                }
            }

            IconButton(onClick = ::closeDialog, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Mist300)
            }
        }
    }
}

/** The upgraded "Break it into steps" panel (P14): a manual checklist (unchanged from the old
 * PiecesSection) plus AI-generated suggestions sharing [BreakdownSuggestions] with TasksPanel's
 * breakdown flow — but landing in [pieces] (this task's own checklist), never new sibling tasks.
 * [onMakeSeparateTasks] is the escape hatch for when a task genuinely is several work items. */
@Composable
private fun FocusStepsSection(
    pieces: List<TaskPiece>,
    onPiecesChange: (List<TaskPiece>) -> Unit,
    selectedMinutes: Int,
    suggestLoading: Boolean,
    suggestError: String?,
    suggestRows: List<BreakdownRow>,
    suggestRemaining: Int?,
    suggestFurtherLoadingId: String?,
    offline: Boolean,
    onSuggest: () -> Unit,
    onAddSuggestion: (BreakdownRow) -> Unit,
    onDiscardSuggestion: (BreakdownRow) -> Unit,
    onAddAllSuggestions: () -> Unit,
    onDiscardAllSuggestions: () -> Unit,
    onBreakdownFurther: (BreakdownRow) -> Unit,
    onMakeSeparateTasks: () -> Unit
) {
    var newPiece by remember { mutableStateOf("") }

    fun addPiece() {
        val text = newPiece.trim()
        if (text.isEmpty()) return
        onPiecesChange(pieces + TaskPiece(id = System.currentTimeMillis().toString(), text = text))
        newPiece = ""
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SbLabel("STEPS", color = Mist300)
            Spacer(Modifier.weight(1f))
            // Never a broken/dead control: hidden (not disabled) when offline or out of quota,
            // with the manual checklist below always fully usable either way.
            if (!offline && suggestRemaining != 0) {
                TextButton(onClick = onSuggest, enabled = !suggestLoading) {
                    Text(if (suggestLoading) "Suggesting…" else "✨ Suggest", color = Violet400)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (pieces.isEmpty()) {
            Text(
                "Split this task into smaller steps if it helps.",
                color = Mist300,
                style = SecondBrainTypography.bodySmall
            )
        }
        pieces.forEach { piece ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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

        if (suggestRemaining == 0) {
            Spacer(Modifier.height(8.dp))
            Text("Out of AI breakdowns today — add your own steps.", color = Mist300, style = SecondBrainTypography.bodySmall)
        }
        if (suggestLoading) {
            Spacer(Modifier.height(10.dp))
            BreakdownLoadingDots()
        }
        if (suggestError != null) {
            Spacer(Modifier.height(8.dp))
            Text(suggestError, color = Rose400, style = SecondBrainTypography.bodySmall)
        }
        if (suggestRows.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SbLabel("SUGGESTED", color = Mist300)
            val suggestedTotal = suggestRows.sumOf { it.subtask.estimatedMinutes }
            if (suggestedTotal > selectedMinutes) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "These add up to about ${formatFocusMinutes(suggestedTotal)}.",
                    color = Mist300,
                    style = SecondBrainTypography.bodySmall
                )
            }
            Spacer(Modifier.height(6.dp))
            BreakdownSuggestions(
                rows = suggestRows,
                remainingToday = suggestRemaining,
                onAdd = onAddSuggestion,
                onDiscard = onDiscardSuggestion,
                onAddAll = onAddAllSuggestions,
                onDiscardAll = onDiscardAllSuggestions,
                onBreakdownFurther = onBreakdownFurther,
                breakdownFurtherLoadingId = suggestFurtherLoadingId
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Make these separate tasks instead →",
                color = Mist300,
                style = SecondBrainTypography.bodySmall,
                modifier = Modifier.clickable(onClick = onMakeSeparateTasks)
            )
        }
    }
}

@Composable
private fun PomodoroRing(remFrac: Float, remainingMs: Long, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val trackColor = Ink700
    // P14: colour ramps down as time runs out, plus a slow pulse in the final minute — always
    // computed (never conditionally called) so the infinite transition doesn't violate Compose's
    // "same composables every recomposition" rule; only its effect is gated on the time window.
    val transition = rememberInfiniteTransition(label = "ringPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "ringPulseAlpha"
    )
    val alpha = if (remainingMs in 1..60_000L) pulse else 1f
    val progressColor = when {
        remFrac > 0.5f -> StreakAccent
        remFrac > 0.2f -> Gold400
        else -> Rose400
    }
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
            // A Time Timer's disappearing wedge: full circle at the start, sweeping COUNTER-
            // clockwise (negative sweep) from 12 o'clock as [remFrac] (remaining, not elapsed)
            // shrinks toward 0 — the "eaten" gap grows clockwise from 12 as time passes.
            drawArc(
                color = progressColor.copy(alpha = alpha),
                startAngle = -90f,
                sweepAngle = -360f * remFrac.coerceIn(0f, 1f),
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
private fun RunningFocusTimer(
    endsAtMillis: Long,
    totalMs: Long,
    onTick: (Long) -> Unit,
    onMilestone: (Float) -> Unit,
    onFinished: () -> Unit
) {
    LaunchedEffect(endsAtMillis) {
        // Fires once per threshold as remaining-time fraction crosses it, not on every tick —
        // peripheral awareness (P14) without a haptic every second.
        val milestones = mutableSetOf<Float>()
        val thresholds = listOf(0.5f, 0.25f, 0.10f)
        while (true) {
            val remaining = endsAtMillis - System.currentTimeMillis()
            onTick(remaining)
            val frac = (remaining.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            thresholds.forEach { t -> if (frac <= t && milestones.add(t)) onMilestone(t) }
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
