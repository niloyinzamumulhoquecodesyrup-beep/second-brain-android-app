package com.secondbrain.lock.ui.screens.work

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold400
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.StreakCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed class NowCardState {
    data class ActiveTask(val task: Task, val startMin: Int, val durationMin: Int) : NowCardState()
    data class ActiveRoutine(val item: TodayItem.RoutineItem, val startMin: Int, val durationMin: Int) : NowCardState()
    data class UpcomingTask(val task: Task, val startMin: Int) : NowCardState()
    data class UpcomingRoutine(val item: TodayItem.RoutineItem, val startMin: Int) : NowCardState()
    data class Picked(val task: Task) : NowCardState()
    object AllDone : NowCardState()
    object Empty : NowCardState()
}

/**
 * "What do I do" — the single card WorkScreen mounts above [TasksPanel], answering that question
 * directly rather than making the user scan a list. Reuses [buildTodayItems]/[nextUpIndex]
 * verbatim (P16's own instruction) so this can never disagree with the timeline TasksPanel
 * renders from the same data. ONE card, never two, never a list — every branch below renders
 * exactly one of: the active item, the next item, a "pick one for me" prompt, an all-done
 * celebration, or (very briefly, see [firstFrameRendered]) a loading shimmer.
 */
@Composable
internal fun NowCard(onOpenFocus: (Task) -> Unit, onOpenRoutineFocus: (TodayItem.RoutineItem) -> Unit) {
    val tasks by TasksRepository.tasks.collectAsState()
    val routines by PlannerRepository.routines.collectAsState()
    val plannerToday by PlannerRepository.today.collectAsState()
    var nowMinute by remember { mutableStateOf(currentMinuteOfDay()) }
    var pickedTaskId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val breakdownController = rememberTaskBreakdownController()

    // Local-first cold start already guarantees restore() ran before this composes (LockApp),
    // so there's essentially nothing to "load" here — this exists to satisfy P16's spec'd
    // Loading state without a spinner, not because a real network wait is expected.
    var firstFrameRendered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { firstFrameRendered = true }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMinute = currentMinuteOfDay()
        }
    }

    val today = remember { LocalDate.now() }
    val openTasks = remember(tasks) { tasks.filter { !it.done } }
    val todayTasks = remember(openTasks, today) { openTasks.filter { isTodayTask(it, today) } }
    val items = remember(todayTasks, routines, plannerToday, nowMinute) {
        buildTodayItems(todayTasks, routines, plannerToday, nowMinute)
    }

    fun later(task: Task) {
        val start = task.startMin ?: return
        // Silent — no haptic, no toast (P16). Deferring by 30 minutes is enough to drop this
        // task out of "happening now" so the card naturally moves to whatever's next.
        scope.launch { TasksRepository.reschedule(task.id, (start + 30).coerceAtMost(1439), task.durationMin ?: 0) }
    }

    val state = remember(items, nowMinute, pickedTaskId, tasks) {
        val allDone = items.isNotEmpty() && items.all { it.isDoneForToday }
        when {
            allDone -> NowCardState.AllDone
            else -> {
                val activeIndex = items.indices.firstOrNull { items[it].isHappeningNow(nowMinute) && !items[it].isDoneForToday }
                val active = activeIndex?.let { items[it] }
                when {
                    active is TodayItem.TaskItem && active.task.startMin != null ->
                        NowCardState.ActiveTask(active.task, active.task.startMin!!, active.task.durationMin ?: 0)
                    active is TodayItem.RoutineItem && active.startMin != null ->
                        NowCardState.ActiveRoutine(active, active.startMin!!, active.durationMinutes ?: 0)
                    else -> {
                        val nextIndex = nextUpIndex(items, nowMinute)
                        val next = nextIndex?.let { items[it] }
                        when {
                            next is TodayItem.TaskItem && next.task.startMin != null -> NowCardState.UpcomingTask(next.task, next.task.startMin!!)
                            next is TodayItem.RoutineItem && next.startMin != null -> NowCardState.UpcomingRoutine(next, next.startMin!!)
                            else -> {
                                val picked = pickedTaskId?.let { id -> openTasks.firstOrNull { it.id == id } }
                                if (picked != null) NowCardState.Picked(picked) else NowCardState.Empty
                            }
                        }
                    }
                }
            }
        }
    }

    if (!firstFrameRendered) {
        NowCardShimmer()
        return
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val visibleState = remember {
        androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
    }
    androidx.compose.animation.AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { with(density) { -12.dp.roundToPx() } }
    ) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(250)) togetherWith androidx.compose.animation.fadeOut(tween(250)))
                    .using(SizeTransform(clip = false))
            },
            label = "nowCard"
        ) { s ->
            when (s) {
                is NowCardState.ActiveTask -> NowCardActive(
                    title = s.task.title,
                    startMin = s.startMin,
                    durationMin = s.durationMin,
                    nowMinute = nowMinute,
                    eyebrow = "NOW",
                    startLabel = "Start",
                    showTooBig = true,
                    showLater = true,
                    onStart = { FeedbackUtil.longPressTick(context); onOpenFocus(s.task) },
                    onTooBig = { FeedbackUtil.longPressTick(context); breakdownController.request(s.task) },
                    onLater = { later(s.task) }
                )
                is NowCardState.ActiveRoutine -> NowCardActive(
                    title = s.item.routine.title,
                    startMin = s.startMin,
                    durationMin = s.durationMin,
                    nowMinute = nowMinute,
                    eyebrow = "NOW",
                    startLabel = "Start",
                    showTooBig = false,
                    showLater = false,
                    onStart = { FeedbackUtil.longPressTick(context); onOpenRoutineFocus(s.item) },
                    onTooBig = {},
                    onLater = {}
                )
                is NowCardState.UpcomingTask -> NowCardUpcoming(
                    title = s.task.title,
                    startMin = s.startMin,
                    nowMinute = nowMinute,
                    showTooBig = true,
                    onStart = { FeedbackUtil.longPressTick(context); onOpenFocus(s.task) },
                    onTooBig = { FeedbackUtil.longPressTick(context); breakdownController.request(s.task) }
                )
                is NowCardState.UpcomingRoutine -> NowCardUpcoming(
                    title = s.item.routine.title,
                    startMin = s.startMin,
                    nowMinute = nowMinute,
                    showTooBig = false,
                    onStart = { FeedbackUtil.longPressTick(context); onOpenRoutineFocus(s.item) },
                    onTooBig = {}
                )
                is NowCardState.Picked -> NowCardActive(
                    title = s.task.title,
                    startMin = null,
                    durationMin = s.task.durationMin ?: 0,
                    nowMinute = nowMinute,
                    eyebrow = "PICKED FOR YOU",
                    startLabel = "Start",
                    showTooBig = true,
                    showLater = false,
                    onStart = { FeedbackUtil.longPressTick(context); onOpenFocus(s.task) },
                    onTooBig = { FeedbackUtil.longPressTick(context); breakdownController.request(s.task) },
                    onLater = {}
                )
                NowCardState.AllDone -> NowCardAllDone()
                NowCardState.Empty -> NowCardEmpty(
                    onPickForMe = {
                        val smallest = TasksRepository.tasks.value.filter { !it.done }.minByOrNull { it.durationMin ?: Int.MAX_VALUE }
                        pickedTaskId = smallest?.id
                    }
                )
            }
        }

        if (ApiClient.isOffline()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Gold400)
            )
        }
    }
    }

    if (breakdownController.taskId != null) {
        TaskBreakdownBlock(breakdownController)
    }
}

@Composable
private fun NowCardShimmer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StreakCard)
    )
}

@Composable
private fun NowCardActive(
    title: String,
    startMin: Int?,
    durationMin: Int,
    nowMinute: Int,
    eyebrow: String,
    startLabel: String,
    showTooBig: Boolean,
    showLater: Boolean,
    onStart: () -> Unit,
    onTooBig: () -> Unit,
    onLater: () -> Unit
) {
    // Elapsed can exceed 1 (running over) — only the drawn bar width is clamped to [0,1].
    val rawFraction = if (startMin == null || durationMin <= 0) {
        0f
    } else {
        val endMin = startMin + durationMin
        val elapsed = if (endMin <= 1440 || nowMinute >= startMin) nowMinute - startMin else nowMinute + 1440 - startMin
        elapsed.toFloat() / durationMin
    }
    val remainingMin = (durationMin - (rawFraction * durationMin)).toInt().coerceAtLeast(0)
    val over = rawFraction >= 1f
    val barColor = when {
        over -> Rose400
        rawFraction >= 0.85f -> Rose400
        rawFraction >= 0.6f -> Gold400
        else -> StreakAccent
    }
    val animatedFraction by animateFloatAsState(
        targetValue = rawFraction.coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = LinearEasing),
        label = "nowCardProgress"
    )
    val timeLabel = if (startMin != null) "${formatMinuteOfDay(startMin)} – ${formatMinuteOfDay(startMin + durationMin)}" else null

    NowCardShell {
        Text(
            if (timeLabel != null) "$eyebrow · $timeLabel" else eyebrow,
            color = StreakAccent,
            style = SecondBrainTypography.labelSmall,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = Mist100,
            style = SecondBrainTypography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(14.dp))
        if (startMin != null) {
            val barDescription = if (over) "Running over on $title" else "$remainingMin minutes left of $title"
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = barDescription }) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Ink700)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (over) 1f else animatedFraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(barColor)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (over) "running over — that's fine" else "$remainingMin min left",
                    color = Mist300,
                    style = SecondBrainTypography.bodySmall
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1.4f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = androidx.compose.ui.graphics.Color.White)
            ) { Text(startLabel, style = SecondBrainTypography.titleMedium) }
            if (showTooBig) {
                OutlinedButton(
                    onClick = onTooBig,
                    modifier = Modifier.weight(1f).height(56.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Ink500),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist100)
                ) { Text("Too big", style = SecondBrainTypography.bodyMedium) }
            }
            if (showLater) {
                TextButton(
                    onClick = onLater,
                    modifier = Modifier.weight(0.8f).height(56.dp)
                ) { Text("Later", color = Mist300, style = SecondBrainTypography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun NowCardUpcoming(
    title: String,
    startMin: Int,
    nowMinute: Int,
    showTooBig: Boolean,
    onStart: () -> Unit,
    onTooBig: () -> Unit
) {
    val startsInMin = (startMin - nowMinute).coerceAtLeast(0)
    NowCardShell {
        Text(
            "NEXT · ${formatMinuteOfDay(startMin)}",
            color = StreakAccent,
            style = SecondBrainTypography.labelSmall,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(title, color = Mist100, style = SecondBrainTypography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Text("starts in $startsInMin min", color = Mist300, style = SecondBrainTypography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1.4f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = androidx.compose.ui.graphics.Color.White)
            ) { Text("Start early", style = SecondBrainTypography.titleMedium) }
            if (showTooBig) {
                OutlinedButton(
                    onClick = onTooBig,
                    modifier = Modifier.weight(1f).height(56.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Ink500),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist100)
                ) { Text("Too big", style = SecondBrainTypography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun NowCardEmpty(onPickForMe: () -> Unit) {
    NowCardShell {
        Text("Nothing scheduled.", color = Mist100, style = SecondBrainTypography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text("Want me to pick something?", color = Mist300, style = SecondBrainTypography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPickForMe,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = androidx.compose.ui.graphics.Color.White)
        ) { Text("Pick for me", style = SecondBrainTypography.titleMedium) }
    }
}

@Composable
private fun NowCardAllDone() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Emerald400.copy(alpha = 0.12f))
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "That's the day. Genuinely done.",
            color = Emerald400,
            style = SecondBrainTypography.headlineMedium,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun NowCardShell(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StreakCard)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        content = content
    )
}

