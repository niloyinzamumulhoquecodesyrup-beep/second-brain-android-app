package com.secondbrain.lock.ui.screens.organize

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.data.repo.StatsRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.dto.Note
import com.secondbrain.lock.ui.screens.mindverse.relativeTime
import com.secondbrain.lock.ui.screens.work.CompletionCelebration
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist500
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.StreakCard
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SESSION_CAP = 15
private const val MAX_UNDO_STREAK = 5

private enum class SortTarget(val paraValue: String?) {
    PROJECT("project"), ARCHIVE("archive"), RESOURCE("resource"),
    // TASK isn't a PARA bucket — it converts the note into a real Task instead of moving it.
    TASK(null)
}

private data class PendingSortAction(val note: Note, val target: SortTarget)

private fun sortCardTitle(note: Note): String =
    note.title.ifBlank { note.content?.lineSequence()?.firstOrNull()?.take(80)?.trim().orEmpty() }
        .ifBlank { "Untitled" }

/** Fire-and-forget, same reasoning as FocusPomodoro's logFocusActivity: this must survive the
 * screen closing (onDone unmounts SortPassScreen right after calling this), so it can't use a
 * rememberCoroutineScope() tied to the composable's own lifecycle. */
private fun flushSortActions(actions: List<PendingSortAction>) {
    if (actions.isEmpty()) return
    CoroutineScope(Dispatchers.Default).launch {
        for (action in actions) {
            if (action.target == SortTarget.TASK) {
                TasksRepository.create(title = sortCardTitle(action.note), noteId = action.note.id)
                    .onSuccess { NotesRepository.graduate(action.note.id) }
            } else {
                NotesRepository.moveToPara(action.note.id, action.target.paraValue!!)
            }
        }
    }
}

/**
 * Batched inbox-sorting flow (P15): every capture lands in `para = "inbox"` with no decision made
 * at capture time (P6) — this is where that decision happens, as a fast, swipe-driven, opt-in
 * activity, not a chore. Moves are accumulated locally and only sent to the server at the end of
 * a batch (POST /api/para/batch will replace this sequential flush once it ships — see P23) so
 * the UI never blocks on a network round trip mid-swipe.
 */
@Composable
fun SortPassScreen(onDone: () -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current

    var sessionCards by remember {
        mutableStateOf(NotesRepository.paraNotes.value.filter { it.para == "inbox" }.take(SESSION_CAP))
    }
    var index by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<List<PendingSortAction>>(emptyList()) }
    var undoStreak by remember { mutableStateOf(0) }
    var totalSortedSoFar by remember { mutableStateOf(0) }
    var showSummary by remember { mutableStateOf(sessionCards.isEmpty()) }
    var showCelebration by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { NotesRepository.refreshPara() }
    // Nothing to sort at all — never show an empty pass, just bounce straight back.
    LaunchedEffect(Unit) { if (sessionCards.isEmpty()) onDone() }

    fun commit(note: Note, target: SortTarget) {
        pending = pending + PendingSortAction(note, target)
        undoStreak = 0
        FeedbackUtil.spinTick(context)
        index += 1
        if (index >= sessionCards.size) showSummary = true
    }

    fun undo() {
        if (pending.isEmpty() || undoStreak >= MAX_UNDO_STREAK) return
        pending = pending.dropLast(1)
        undoStreak += 1
        index = (index - 1).coerceAtLeast(0)
        showSummary = false
    }

    fun remainingInboxCount(): Int =
        (NotesRepository.paraNotes.value.count { it.para == "inbox" } - pending.size).coerceAtLeast(0)

    fun startNewBatch() {
        // flushSortActions is fire-and-forget (Dispatchers.Default), so the just-decided notes'
        // moveToPara/create calls may not have landed in NotesRepository.paraNotes yet — exclude
        // them explicitly, or they'd still read as para == "inbox" and reappear in the next batch.
        val justSorted = pending.map { it.note.id }.toSet()
        flushSortActions(pending)
        totalSortedSoFar += pending.size
        pending = emptyList()
        undoStreak = 0
        val nextBatch = NotesRepository.paraNotes.value
            .filter { it.para == "inbox" && it.id !in justSorted }
            .take(SESSION_CAP)
        sessionCards = nextBatch
        index = 0
        showSummary = nextBatch.isEmpty()
        if (nextBatch.isEmpty()) onDone()
    }

    fun finishSession() {
        flushSortActions(pending)
        totalSortedSoFar += pending.size
        pending = emptyList()
        if (totalSortedSoFar > 0) StatsRepository.bumpTaskDone()
        showCelebration = true
    }

    Box(Modifier.fullAuraBackground().fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp
                )
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "↶ Undo",
                    color = if (pending.isNotEmpty() && undoStreak < MAX_UNDO_STREAK) Mist100 else Mist500,
                    style = SecondBrainTypography.bodyMedium,
                    modifier = Modifier.clickable(enabled = pending.isNotEmpty() && undoStreak < MAX_UNDO_STREAK) { undo() }
                )
                ProgressDots(total = sessionCards.size, filled = index)
            }
            Spacer(Modifier.height(24.dp))

            if (!showSummary) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val next = sessionCards.getOrNull(index + 1)
                    if (next != null) {
                        SortCardSurface(
                            note = next,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; alpha = 0.6f }
                        )
                    }
                    val current = sessionCards.getOrNull(index)
                    if (current != null) {
                        ActiveSortCard(
                            key = current.id,
                            note = current,
                            onCommit = { target -> commit(current, target) }
                        )
                    }
                }
            } else {
                SortPassSummary(
                    sorted = totalSortedSoFar,
                    stillWaiting = remainingInboxCount(),
                    onDone = ::finishSession,
                    onKeepGoing = ::startNewBatch
                )
            }
        }

        if (showCelebration) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CompletionCelebration(message = "That's a good pass.", bonus = false, onDone = onDone)
            }
        }
    }
}

@Composable
private fun ProgressDots(total: Int, filled: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (i < filled) Mist100 else Ink600)
            )
            if (i != total - 1) Spacer(Modifier.width(5.dp))
        }
    }
}

@Composable
private fun SortPassSummary(sorted: Int, stillWaiting: Int, onDone: () -> Unit, onKeepGoing: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("That's a good pass.", color = Mist100, style = SecondBrainTypography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            if (stillWaiting > 0) "$sorted sorted · $stillWaiting still waiting" else "$sorted sorted",
            color = Mist300,
            style = SecondBrainTypography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Ink950)
            ) { Text("Done") }
            if (stillWaiting > 0) {
                Button(
                    onClick = onKeepGoing,
                    colors = ButtonDefaults.buttonColors(containerColor = StreakCard, contentColor = Mist100)
                ) { Text("Keep going →") }
            }
        }
    }
}

/** Static, non-interactive peek of the note underneath the active card — gives the stack its
 * depth. Becomes a real [ActiveSortCard] (springing from these exact same scale/alpha values up
 * to 1f/1f) the moment it's this session's current card, so the transition reads as continuous
 * even though it's actually two different composable instances. */
@Composable
private fun SortCardSurface(note: Note, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 280.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(StreakCard)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            note.content?.takeIf { it.isNotBlank() } ?: note.title,
            color = Mist100,
            style = SecondBrainTypography.titleLarge,
            maxLines = 6,
            textAlign = TextAlign.Start
        )
        Text("captured ${relativeTime(note.createdAt)}", color = Mist500, style = SecondBrainTypography.bodySmall)
    }
}

/** The one card the user can actually act on: drag in any of the 4 directions, or tap one of the
 * direction labels around it, to commit — both paths run the identical exit animation and call
 * [onCommit]. [key] pins recomposition identity to the note so a fresh instance (and a fresh
 * entrance spring from the peeking scale) mounts for each new card. */
@Composable
private fun ActiveSortCard(key: String, note: Note, onCommit: (SortTarget) -> Unit) {
    androidx.compose.runtime.key(key) {
        val scope = rememberCoroutineScope()
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        var scale by remember { mutableFloatStateOf(0.94f) }
        var alpha by remember { mutableFloatStateOf(0.6f) }
        var cardWidthPx by remember { mutableFloatStateOf(1080f) }
        var cardHeightPx by remember { mutableFloatStateOf(1400f) }
        var committing by remember { mutableStateOf(false) }

        // Entrance: springs from the peeking scale/alpha (matching SortCardSurface's static
        // preview) up to full size/opacity.
        LaunchedEffect(key) {
            launch {
                androidx.compose.animation.core.Animatable(0.94f).animateTo(
                    1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) { scale = value }
            }
            androidx.compose.animation.core.Animatable(0.6f).animateTo(1f, tween(220)) { alpha = value }
        }

        fun exitTo(target: SortTarget) {
            if (committing) return
            committing = true
            val (tx, ty) = when (target) {
                SortTarget.PROJECT -> 1.6f * cardWidthPx to offsetY
                SortTarget.ARCHIVE -> -1.6f * cardWidthPx to offsetY
                SortTarget.TASK -> offsetX to -1.6f * cardHeightPx
                SortTarget.RESOURCE -> offsetX to 1.6f * cardHeightPx
            }
            scope.launch {
                launch {
                    androidx.compose.animation.core.Animatable(offsetX).animateTo(tx, tween(300, easing = LinearOutSlowInEasing)) { offsetX = value }
                }
                launch {
                    androidx.compose.animation.core.Animatable(offsetY).animateTo(ty, tween(300, easing = LinearOutSlowInEasing)) { offsetY = value }
                }
                // Fades over the last 40% of the 300ms exit (180ms in, 120ms duration).
                androidx.compose.animation.core.Animatable(1f).animateTo(0f, tween(120, delayMillis = 180)) { alpha = value }
                onCommit(target)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 280.dp)
                .onSizeChanged { cardWidthPx = it.width.toFloat(); cardHeightPx = it.height.toFloat() }
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(RoundedCornerShape(20.dp))
                .background(StreakCard)
                .pointerInput(key, committing) {
                    if (committing) return@pointerInput
                    // Hand-rolled dx/dt velocity, same reasoning as ParaCubeView's spin gesture:
                    // Compose's VelocityTracker was found unreliable for a clean fast drag on
                    // this device.
                    var velocityX = 0f
                    var velocityY = 0f
                    var lastEventTimeMillis = 0L
                    detectDragGestures(
                        onDragStart = {
                            velocityX = 0f
                            velocityY = 0f
                            lastEventTimeMillis = System.currentTimeMillis()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                            val now = System.currentTimeMillis()
                            val dt = (now - lastEventTimeMillis).coerceAtLeast(1)
                            velocityX = dragAmount.x / dt * 1000f
                            velocityY = dragAmount.y / dt * 1000f
                            lastEventTimeMillis = now
                        },
                        onDragEnd = {
                            val horizontalDominant = abs(offsetX) >= abs(offsetY)
                            val committed = if (horizontalDominant) {
                                abs(offsetX) >= cardWidthPx * 0.25f || abs(velocityX) >= 400f
                            } else {
                                abs(offsetY) >= cardHeightPx * 0.25f || abs(velocityY) >= 400f
                            }
                            if (committed) {
                                val target = when {
                                    horizontalDominant && offsetX > 0 -> SortTarget.PROJECT
                                    horizontalDominant -> SortTarget.ARCHIVE
                                    offsetY < 0 -> SortTarget.TASK
                                    else -> SortTarget.RESOURCE
                                }
                                exitTo(target)
                            } else {
                                scope.launch {
                                    launch {
                                        androidx.compose.animation.core.Animatable(offsetX)
                                            .animateTo(0f, spring(dampingRatio = 0.6f)) { offsetX = value }
                                    }
                                    androidx.compose.animation.core.Animatable(offsetY)
                                        .animateTo(0f, spring(dampingRatio = 0.6f)) { offsetY = value }
                                }
                            }
                        }
                    )
                }
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                note.content?.takeIf { it.isNotBlank() } ?: note.title,
                color = Mist100,
                style = SecondBrainTypography.titleLarge,
                maxLines = 6,
                textAlign = TextAlign.Start
            )
            Text("captured ${relativeTime(note.createdAt)}", color = Mist500, style = SecondBrainTypography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Text("↑ Task", color = Mist300, style = SecondBrainTypography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable(enabled = !committing) { exitTo(SortTarget.TASK) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("← Archive", color = Mist300, style = SecondBrainTypography.bodySmall, modifier = Modifier.clickable(enabled = !committing) { exitTo(SortTarget.ARCHIVE) })
            Text("Project →", color = Mist300, style = SecondBrainTypography.bodySmall, modifier = Modifier.clickable(enabled = !committing) { exitTo(SortTarget.PROJECT) })
        }
        Text("↓ Resource", color = Mist300, style = SecondBrainTypography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable(enabled = !committing) { exitTo(SortTarget.RESOURCE) })
    }
}
