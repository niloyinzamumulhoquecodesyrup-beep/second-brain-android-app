package com.secondbrain.lock.ui.screens.work

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.data.FocusState
import com.secondbrain.lock.data.RoutineRepository
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.CreatePlannerBlockRequest
import com.secondbrain.lock.network.dto.PlannerDayResponse
import com.secondbrain.lock.network.dto.PlannerRoutine
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.network.dto.UpdatePlannerBlockRequest
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Mist500
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.StreakCard
import com.secondbrain.lock.ui.theme.StreakSilver
import com.secondbrain.lock.ui.theme.Violet400
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)

internal fun formatDueDate(raw: String): String =
    runCatching { LocalDate.parse(raw.take(10)).format(DUE_DATE_FORMAT) }.getOrDefault(raw)

internal fun dueDateOf(task: Task): LocalDate? =
    task.dueDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

internal fun createdDateOf(task: Task): LocalDate? =
    task.createdAt?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** An undated task only belongs in "today" on the day it was captured — after that it's a
 * [isDraftTask], parked in AllTasksScreen's "More tasks" tab until it's scheduled. A dated task
 * belongs in Today from its due date onward — `due <= today`, not `due == today` — so an overdue
 * task keeps surfacing where the user will actually see it instead of silently falling out of
 * view. This is deliberately NOT a rewrite of due_date (that would destroy the original
 * scheduling intent and fight across devices/clients); [isOverdueTask] below just annotates which
 * of these rows are overdue so the UI can present them neutrally rather than excluding them. */
internal fun isTodayTask(task: Task, today: LocalDate): Boolean {
    val due = dueDateOf(task)
    return if (due != null) due <= today else createdDateOf(task) == today
}

/** No longer used to exclude a task from Today (see [isTodayTask]) — only to flag which Today
 * rows are overdue, so the UI can annotate them ("waiting since ...", no red) instead of hiding
 * them in a separate section. */
internal fun isOverdueTask(task: Task, today: LocalDate): Boolean {
    val due = dueDateOf(task)
    return due != null && due < today
}

internal fun isDraftTask(task: Task, today: LocalDate): Boolean {
    val due = dueDateOf(task)
    return due == null && createdDateOf(task) != today
}

internal data class TaskSubtitleInfo(val text: String, val overdue: Boolean)

internal fun taskSubtitleInfo(task: Task): TaskSubtitleInfo {
    val dueRaw = task.dueDate
    val dueDateLocal = dueDateOf(task)
    val overdue = dueDateLocal != null && dueDateLocal < LocalDate.now() && !task.done
    val dateText = when {
        dueDateLocal == null -> "No due date"
        // Neutral framing, no red, no exclamation — P8 treats an overdue task as information,
        // not a failure. "waiting since ..." rather than "overdue: ...".
        overdue -> "waiting since ${formatDueDate(dueRaw!!)}"
        else -> formatDueDate(dueRaw!!)
    }
    // A task with a chosen time (set via the quick-add sheet's "Time" picker) shows its window
    // the same way a routine does, so the picked time is actually visible somewhere.
    val startMin = task.startMin
    val text = if (startMin != null) {
        val timeRange = "${formatMinuteOfDay(startMin)} – ${formatMinuteOfDay(startMin + (task.durationMin ?: 0))}"
        if (dueDateLocal != null) "$timeRange · $dateText" else timeRange
    } else dateText
    return TaskSubtitleInfo(text, overdue)
}

/** Opens the platform date picker anchored on [initial], reporting the picked day. Shared with
 * QuickAddSheet's task-creation flow now that the inline add-task field has moved to the nav
 * bar's "+" button. [minDate], when set, greys out every day before it in the calendar — used to
 * block "today" when scheduling a breakdown suggestion that no longer fits today. */
internal fun pickDate(context: android.content.Context, initial: LocalDate, minDate: LocalDate? = null, onPicked: (LocalDate) -> Unit) {
    val dialog = DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth
    )
    if (minDate != null) {
        dialog.datePicker.minDate = minDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    dialog.show()
}

/** A row in the merged "Today" list — either a real task or a routine occurrence (materialized
 * or virtual-for-today), sorted together by [startMin]. Shared between the compact TasksPanel
 * card and the full AllTasksScreen. */
internal sealed class TodayItem(val startMin: Int?, val title: String) {
    internal class TaskItem(val task: Task) : TodayItem(task.startMin, task.title)
    internal class RoutineItem(val routine: PlannerRoutine, val block: com.secondbrain.lock.network.dto.PlannerBlock?) :
        TodayItem(block?.startMin ?: routine.startMin, routine.title)
}

internal fun formatMinuteOfDay(minute: Int): String {
    val h = (minute / 60) % 24
    val m = minute % 60
    return "%02d:%02d".format(h, m)
}

internal fun routineSubtitle(item: TodayItem.RoutineItem): String {
    val startMin = item.startMin ?: item.routine.startMin
    return "${formatMinuteOfDay(startMin)} – ${formatMinuteOfDay(startMin + item.routine.durationMin)}"
}

internal fun currentMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

/** Whether [startMin]..[startMin]+[durationMin] contains [nowMin], wrapping past midnight for
 * overnight spans (e.g. a "Sleep" routine running 23:00-07:00). [nowMin] is passed in (rather
 * than read via `LocalTime.now()` here) because a plain wall-clock read wouldn't ever trigger a
 * recomposition on its own — callers hold it in a ticking `remember`d state instead so the
 * "current" dot actually updates as time passes, not just when unrelated state changes. */
/** Elapsed fraction of [startMin]..[startMin]+[durationMin], with the same overnight-wrap
 * handling as [isCurrentNow] — deliberately NOT coerced to [0,1] so a caller can tell "still
 * running, almost done" (near 1) apart from "running over" (past 1) if it ever needs to. */
internal fun elapsedFraction(startMin: Int, durationMin: Int, nowMin: Int): Float {
    if (durationMin <= 0) return 1f
    val endMin = startMin + durationMin
    val elapsed = if (endMin <= 1440 || nowMin >= startMin) nowMin - startMin else nowMin + 1440 - startMin
    return elapsed.toFloat() / durationMin
}

internal fun isCurrentNow(startMin: Int?, durationMin: Int?, nowMin: Int): Boolean {
    if (startMin == null) return false
    val duration = durationMin ?: 0
    val endMin = startMin + duration
    return if (endMin <= 1440) nowMin in startMin until endMin
    else nowMin >= startMin || nowMin < endMin - 1440
}

private fun previousWeekday(weekday: Int): Int = ((weekday - 1) + 7) % 7

/** Same wraparound idea as [isCurrentNow], but for a *recurring* routine occurrence: the early-
 * morning "tail" before [startMin] only counts as still running if the routine actually ran
 * yesterday too (i.e. its [days] include yesterday's weekday) — a Mon-only routine shouldn't
 * read as "still running" on a Tuesday morning just because its window happens to cross midnight. */
internal fun isRoutineCurrentlyRunning(startMin: Int?, durationMin: Int?, nowMin: Int, days: List<Int>, todayWeekday: Int): Boolean {
    if (startMin == null) return false
    val duration = durationMin ?: 0
    val endMin = startMin + duration
    if (endMin <= 1440) return nowMin in startMin until endMin
    val tail = endMin - 1440
    return nowMin >= startMin || (nowMin < tail && days.contains(previousWeekday(todayWeekday)))
}

/** The item whose scheduled window contains right now gets the "current" accent — matches the
 * bottom nav's "+" button color. */
internal fun TodayItem.isHappeningNow(nowMin: Int): Boolean = when (this) {
    is TodayItem.TaskItem -> isCurrentNow(task.startMin, task.durationMin, nowMin)
    is TodayItem.RoutineItem -> isRoutineCurrentlyRunning(
        startMin,
        block?.durationMin ?: routine.durationMin,
        nowMin,
        routine.days,
        RoutineRepository.currentDayOfWeekIndex()
    )
}

internal val TodayItem.isDoneForToday: Boolean
    get() = when (this) {
        is TodayItem.TaskItem -> task.done
        is TodayItem.RoutineItem -> block?.status == "done"
    }

/** Scheduled duration, when known — NowCard (P16) needs this alongside [TodayItem.startMin] to
 * compute a progress fraction; nothing before it needed duration on its own. */
internal val TodayItem.durationMinutes: Int?
    get() = when (this) {
        is TodayItem.TaskItem -> task.durationMin
        is TodayItem.RoutineItem -> block?.durationMin ?: routine.durationMin
    }

/** Index, in an already-[buildTodayItems]-sorted list, of the "next up" item — the first
 * not-yet-done item whose window hasn't started yet. Only meaningful when nothing is currently
 * happening: once breakfast ends and nothing else is active until evening, the timeline shouldn't
 * sit fully idle/gray — the next thing coming up (e.g. evening reading) gets the same accent dot
 * "happening now" items get, so there's always a clear "what's next" pointer. */
internal fun nextUpIndex(items: List<TodayItem>, nowMin: Int): Int? {
    if (items.any { it.isHappeningNow(nowMin) }) return null
    return items.indices.firstOrNull { index ->
        val item = items[index]
        val start = item.startMin
        start != null && start > nowMin && !item.isDoneForToday
    }
}

/** Where this item belongs in the timeline's chronological order. Ordinarily just [startMin], but
 * an overnight routine that's still running from yesterday (see [isRoutineCurrentlyRunning]) sorts
 * as if it started before midnight rather than at its literal (tonight's) start time — otherwise a
 * routine like "Sleep 23:00-07:00" would land near the *bottom* of this morning's list, as if it
 * were hours away, instead of at the top where "already in progress" belongs. */
private fun TodayItem.sortKey(nowMin: Int, todayWeekday: Int): Int {
    val start = startMin ?: return Int.MAX_VALUE
    if (this !is TodayItem.RoutineItem) return start
    val duration = block?.durationMin ?: routine.durationMin
    val endMin = start + duration
    if (endMin <= 1440) return start
    val tail = endMin - 1440
    val stillRunningFromYesterday = nowMin < tail && routine.days.contains(previousWeekday(todayWeekday))
    return if (stillRunningFromYesterday) start - 1440 else start
}

/** Matches lib/plannerDay.js's dayEntries(): a routine active today (weekday match) shows as a
 * "virtual" occurrence unless a concrete planner_blocks row already materializes it for today
 * (that materialized row "wins" so completing/dismissing today never edits the routine). */
internal fun buildTodayItems(
    todayTasks: List<Task>,
    routines: List<PlannerRoutine>,
    plannerToday: PlannerDayResponse,
    nowMin: Int
): List<TodayItem> {
    val todayStr = LocalDate.now().toString()
    val weekday = RoutineRepository.currentDayOfWeekIndex()
    val real = plannerToday.blocks.filter {
        it.planDate.take(10) == todayStr && it.routineId != null && it.status != "dismissed"
    }
    val coveredRoutineIds = real.mapNotNull { it.routineId }.toSet()
    val materialized = real.mapNotNull { block ->
        val routine = routines.firstOrNull { it.id == block.routineId } ?: return@mapNotNull null
        TodayItem.RoutineItem(routine, block)
    }
    val virtual = routines
        .filter { it.active && it.days.contains(weekday) && it.id !in coveredRoutineIds }
        .map { routine -> TodayItem.RoutineItem(routine, block = null) }
    val routineItems = materialized + virtual
    return (todayTasks.map { TodayItem.TaskItem(it) } + routineItems)
        .sortedWith(compareBy({ it.sortKey(nowMin, weekday) }, { it.title }))
}

/** The timeline row's cycling background palette — matches the Tasks Section design's BG_CYCLE
 * under the light theme. Uses [Ink800] rather than the page background's own [Ink950] token so
 * row tinting stays independent of how dark the page background itself is. */
internal fun rowBg(index: Int): Color = listOf(Ink700, StreakCard, Ink800, Ink600)[index % 4]

internal enum class RowIcon { TASK, ROUTINE }

/**
 * Ports the "Tasks Section" design: a header ("TASKS" + pill "See all" button) over a
 * dot-and-line timeline of today's tasks and routine occurrences. Tapping "See all" opens
 * [AllTasksScreen] for the full breakdown (this week/this month, completed tasks, full
 * untruncated titles) — the compact card here only ever shows today's items, capped and with
 * single-line ellipsized titles, mirroring the design's compact vs. all-tasks split.
 */
@Composable
fun TasksPanel(
    onCompletion: (String) -> Unit,
    highlightTaskId: String? = null,
    onHighlightConsumed: () -> Unit = {},
    onSeeAll: () -> Unit = {}
) {
    val tasks by TasksRepository.tasks.collectAsState()
    val error by TasksRepository.error.collectAsState()
    val routines by PlannerRepository.routines.collectAsState()
    val plannerToday by PlannerRepository.today.collectAsState()
    var focusTask by remember { mutableStateOf<Task?>(null) }
    var focusResume by remember { mutableStateOf<FocusState?>(null) }
    var remoteFocus by remember { mutableStateOf<FocusState?>(null) }
    var glowTaskId by remember { mutableStateOf<String?>(null) }
    var nowMinute by remember { mutableStateOf(currentMinuteOfDay()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Owns the whole "break this task down" flow (request/loading/error/suggestions/add/discard/
    // recurse) — see TaskBreakdownController's KDoc for why this is a shared controller rather
    // than per-screen state. At most one task's breakdown is open at a time.
    val breakdownController = rememberTaskBreakdownController()

    // Ticks the "current" accent dot forward as real time passes — without this, a task/routine
    // entering or leaving its scheduled window would only repaint whenever something unrelated
    // (tasks, routines, a toggle) happened to trigger a recomposition.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMinute = currentMinuteOfDay()
        }
    }

    fun openFocus(task: Task) {
        focusTask = task
        focusResume = remoteFocus?.takeIf { it.active && it.taskId == task.id }
    }

    // A routine occurrence isn't backed by a real Task row, so it's focused with a blank-id
    // stand-in — ApiClient.startFocusSession treats a blank id as an untracked session (no task
    // to attribute it to), which is exactly right for a routine.
    fun openRoutineFocus(item: TodayItem.RoutineItem) {
        openFocus(Task(id = "", title = item.routine.title))
    }

    // Mirrors the task "done" toggle for a routine occurrence: a materialized block is PATCHed
    // to done/active; a still-virtual occurrence is only materialized (via createBlock) the
    // moment it's first marked done.
    fun toggleRoutineDone(item: TodayItem.RoutineItem, done: Boolean) {
        scope.launch {
            val block = item.block
            if (block != null) {
                PlannerRepository.updateBlock(block.id, UpdatePlannerBlockRequest(status = if (done) "done" else "active"))
            } else if (done) {
                PlannerRepository.createBlock(
                    CreatePlannerBlockRequest(
                        title = item.routine.title,
                        planDate = LocalDate.now().toString(),
                        startMin = item.routine.startMin,
                        durationMin = item.routine.durationMin,
                        category = item.routine.category,
                        routineId = item.routine.id,
                        status = "done"
                    )
                )
            }
            if (done) onCompletion("task")
        }
    }

    // Cross-device Pomodoro sync (TodayCards.js): while no dialog of our own is open, poll for a
    // session started on another client so tapping a task's focus button can jump straight to the
    // already-running clock instead of re-offering the picker.
    LaunchedEffect(Unit) {
        while (true) {
            if (focusTask == null) {
                ApiClient.getFocusState().onSuccess { remoteFocus = if (it.active) it else null }
            }
            delay(15_000)
        }
    }

    // A reminder's "Open" (NudgesStrip -> WorkScreen) lands here as [highlightTaskId] -- glow its
    // row briefly, then hand control back to WorkScreen so a recomposition doesn't replay it.
    LaunchedEffect(highlightTaskId) {
        val id = highlightTaskId ?: return@LaunchedEffect
        glowTaskId = id
        onHighlightConsumed()
        delay(2500)
        glowTaskId = null
    }

    val today = remember { LocalDate.now() }
    val openTasks = remember(tasks) { tasks.filter { !it.done } }
    val todayTasks = remember(openTasks, today) {
        openTasks.filter { isTodayTask(it, today) }
    }
    // Every one of today's items shows here — no cap. "See all" is for the week/month/drafts/
    // done breakdown, not for hiding today's own items.
    val visibleItems = remember(todayTasks, routines, plannerToday, nowMinute) {
        buildTodayItems(todayTasks, routines, plannerToday, nowMinute)
    }
    val nextUp = remember(visibleItems, nowMinute) { nextUpIndex(visibleItems, nowMinute) }

    SbCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SbSectionTitle("Tasks", color = Mist300)
            SeeAllButton(onClick = onSeeAll)
        }
        Spacer(Modifier.height(4.dp))
        // P13: long-press alone wasn't discoverable, so the ⋮ menu on every row is now the primary
        // affordance — this hint just tells people the shortcut still exists.
        Text("Tap ⋮ (or long-press) to break a task down", color = Mist400, style = SecondBrainTypography.bodySmall)
        Spacer(Modifier.height(14.dp))

        if (error != null) {
            Text(error!!, color = Rose400, style = SecondBrainTypography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        if (visibleItems.isEmpty()) {
            if (error == null) {
                Text("No tasks yet — tap + on the nav bar to add one.", color = Mist400, style = SecondBrainTypography.bodySmall)
            }
        } else {
            visibleItems.forEachIndexed { index, item ->
                val isFirst = index == 0
                val isLast = index == visibleItems.lastIndex
                val dotColor = if (item.isHappeningNow(nowMinute) || index == nextUp) StreakAccent else StreakSilver
                when (item) {
                    is TodayItem.TaskItem -> {
                        val info = taskSubtitleInfo(item.task)
                        val isBreakingDown = breakdownController.loading && breakdownController.taskId == item.task.id
                        TimelineRow(
                            icon = RowIcon.TASK,
                            title = item.task.title,
                            subtitle = if (isBreakingDown) "Breaking down…" else if (item.task.pendingSync) "Syncing… · ${info.text}" else info.text,
                            // P8: overdue is information, not a warning — Mist500, no red.
                            subtitleColor = if (isBreakingDown) Mist400 else if (info.overdue) Mist500 else Mist300,
                            bg = rowBg(index),
                            dotColor = if (glowTaskId == item.task.id) StreakAccent else dotColor,
                            showLineAbove = !isFirst,
                            showLineBelow = !isLast,
                            done = false,
                            highlighted = glowTaskId == item.task.id,
                            truncateTitle = true,
                            dimmed = isBreakingDown,
                            onToggle = { scope.launch { TasksRepository.setDone(item.task.id, true).onSuccess { onCompletion("task") } } },
                            onFocus = { openFocus(item.task) },
                            onDelete = null,
                            onLongPress = { breakdownController.request(item.task) },
                            // Same handler as long-press (P13) — the ⋮ menu is the discoverable
                            // entry point, long-press stays as the power-user shortcut.
                            onBreakdownClick = { breakdownController.request(item.task) },
                            // P18: only the row actually happening right now gets a fill.
                            activeWindow = if (item.isHappeningNow(nowMinute) && item.task.startMin != null) {
                                item.task.startMin!! to (item.task.durationMin ?: 0)
                            } else null
                        )
                        if (breakdownController.taskId == item.task.id) {
                            TaskBreakdownBlock(breakdownController)
                        }
                    }
                    is TodayItem.RoutineItem -> TimelineRow(
                        icon = RowIcon.ROUTINE,
                        title = item.routine.title,
                        subtitle = routineSubtitle(item),
                        subtitleColor = Mist300,
                        bg = rowBg(index),
                        dotColor = dotColor,
                        showLineAbove = !isFirst,
                        showLineBelow = !isLast,
                        done = item.block?.status == "done",
                        highlighted = false,
                        truncateTitle = true,
                        onToggle = { toggleRoutineDone(item, item.block?.status != "done") },
                        onFocus = { openRoutineFocus(item) },
                        onDelete = null,
                        onLongPress = { Toast.makeText(context, "Task breakdown isn't available for routines", Toast.LENGTH_SHORT).show() },
                        // No onBreakdownClick: unlike long-press (a long-standing, low-visibility
                        // shortcut), a menu item that always shows "not available" is dead UI —
                        // don't render the ⋮ affordance at all for routines.
                        activeWindow = if (item.isHappeningNow(nowMinute) && item.startMin != null) {
                            item.startMin!! to (item.durationMinutes ?: 0)
                        } else null
                    )
                }
            }
        }
    }

    focusTask?.let { task ->
        FocusPomodoroDialog(
            task = task,
            resume = focusResume,
            onDismiss = { focusTask = null; focusResume = null },
            onCompleted = {
                focusTask = null
                focusResume = null
                onCompletion("focus")
            }
        )
    }
}

@Composable
private fun SeeAllButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Mist100)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("See all", color = Ink950, style = SecondBrainTypography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

/**
 * A dot-and-line timeline entry: leading icon circle (repeat for routines, bell for one-off
 * tasks), title + subtitle, an optional focus/play button and an optional round toggle. Shared
 * by the compact [TasksPanel] card and the full [AllTasksScreen].
 */
private val TIMELINE_DOT_OFFSET = 24.dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun TimelineRow(
    icon: RowIcon,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    bg: Color,
    dotColor: Color,
    showLineAbove: Boolean,
    showLineBelow: Boolean,
    done: Boolean,
    highlighted: Boolean,
    truncateTitle: Boolean,
    onToggle: (() -> Unit)?,
    onFocus: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onLongPress: (() -> Unit)? = null,
    // P13: a ⋮ "Break it down" menu item, shown only when non-null — long-press (above) reaches
    // the identical flow but is undiscoverable; this is the menu equivalent, not a second flow.
    onBreakdownClick: (() -> Unit)? = null,
    dimmed: Boolean = false,
    // P18: (startMin, durationMin) when this row isHappeningNow — drives a left-to-right fill
    // that visibly drains over the block's duration. null for every other row (most of them).
    activeWindow: Pair<Int, Int>? = null
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(highlighted) { if (highlighted) requester.bringIntoView() }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .bringIntoViewRequester(requester),
        verticalAlignment = Alignment.Top
    ) {
        // The line above and the line below both stop exactly at this row's own edges — with no
        // gap between consecutive rows, the line above of row N+1 picks up exactly where row N's
        // line below ended, so the timeline reads as one continuous stroke through every dot.
        Column(
            modifier = Modifier.width(18.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showLineAbove) {
                Box(Modifier.width(2.dp).height(TIMELINE_DOT_OFFSET).background(Ink600))
            } else {
                Spacer(Modifier.height(TIMELINE_DOT_OFFSET))
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            if (showLineBelow) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.width(2.dp).weight(1f).background(Ink600))
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .then(
                    if (highlighted) Modifier.border(BorderStroke(1.dp, Emerald400), RoundedCornerShape(16.dp))
                    else Modifier
                )
        ) {
            // P18: the row currently happening visibly drains left-to-right over its own
            // duration. Ticks on its own 1s timer via produceState — scoped to just this row,
            // never the panel — so the other N-1 rows (and TasksPanel itself) don't recompose
            // every second for one row's animation.
            if (activeWindow != null) {
                val (startMin, durationMin) = activeWindow
                val fill by produceState(elapsedFraction(startMin, durationMin, currentMinuteOfDay()), startMin, durationMin) {
                    while (true) {
                        value = elapsedFraction(startMin, durationMin, currentMinuteOfDay())
                        delay(1000)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill.coerceIn(0f, 1f))
                        .background(bg.copy(alpha = 0.35f))
                )
            }
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                dimmed -> Ink600.copy(alpha = 0.35f)
                                icon == RowIcon.ROUTINE -> Gold500.copy(alpha = 0.2f)
                                else -> StreakAccent.copy(alpha = 0.18f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) { Text(if (icon == RowIcon.ROUTINE) "🔁" else "🔔", fontSize = 14.sp) }

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onLongPress != null) {
                                Modifier.pointerInput(onLongPress) {
                                    detectTapGestures(onLongPress = {
                                        FeedbackUtil.longPressTick(context)
                                        onLongPress()
                                    })
                                }
                            } else Modifier
                        )
                ) {
                    Text(
                        title,
                        color = if (done || dimmed) Mist400 else Mist100,
                        style = SecondBrainTypography.bodyMedium,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = if (truncateTitle) 1 else Int.MAX_VALUE,
                        overflow = if (truncateTitle) TextOverflow.Ellipsis else TextOverflow.Clip
                    )
                    Text(subtitle, color = subtitleColor, style = SecondBrainTypography.bodySmall)
                }

                if (onFocus != null) {
                    IconButton(onClick = onFocus) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start focus", tint = Violet400)
                    }
                }
                if (onBreakdownClick != null) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More actions", tint = Mist400)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Break it down") },
                                onClick = { menuExpanded = false; onBreakdownClick() }
                            )
                        }
                    }
                }
                if (onToggle != null) {
                    ToggleCircle(done = done, onClick = onToggle)
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Close, contentDescription = "Delete task", tint = Mist400)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleCircle(done: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (done) StreakAccent else Color.Transparent)
            .then(if (!done) Modifier.border(BorderStroke(2.dp, Ink500), CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (done) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Four dots pulsing in a left-to-right wave while a breakdown request is in flight — same shape
 * and ~1.35s cadence as the provided "Loading Animation.svg" reference (rise, brighten, settle,
 * staggered per dot), rebuilt with Compose's animation APIs since that SVG's SMIL `<animate>`
 * tags don't play back on Android (no WebView/browser involved here to interpret them). */
@Composable
internal fun BreakdownLoadingDots(
    modifier: Modifier = Modifier,
    dotColor: Color = StreakAccent,
    dotSize: Dp = 7.dp,
    spacing: Dp = 5.dp
) {
    val transition = rememberInfiniteTransition(label = "breakdownDots")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { index ->
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1350
                        0f at 0
                        1f at 230 using FastOutSlowInEasing
                        0f at 500 using FastOutSlowInEasing
                    },
                    initialStartOffset = StartOffset(index * 130, StartOffsetType.Delay)
                ),
                label = "breakdownDot$index"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = spacing / 2)
                    .size(dotSize)
                    .graphicsLayer {
                        val scale = 0.6f + 0.4f * progress
                        scaleX = scale
                        scaleY = scale
                        translationY = -progress * 6.dp.toPx()
                        alpha = 0.35f + 0.65f * progress
                    }
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
