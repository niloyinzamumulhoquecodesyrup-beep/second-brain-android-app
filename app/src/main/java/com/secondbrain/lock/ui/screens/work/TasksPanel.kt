package com.secondbrain.lock.ui.screens.work

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.FocusState
import com.secondbrain.lock.data.RoutineRepository
import com.secondbrain.lock.data.repo.MindQueueRepository
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.CreatePlannerBlockRequest
import com.secondbrain.lock.network.dto.MindQueueItem
import com.secondbrain.lock.network.dto.PlannerBlock
import com.secondbrain.lock.network.dto.PlannerRoutine
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.network.dto.UpdatePlannerBlockRequest
import com.secondbrain.lock.network.dto.taskSuggestionTitle
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Red400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.Violet400
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Mirrors TodayCards.js's deterministic per-item icon/color pick (a hash of the item's id). */
private val CARD_ICONS = listOf("📝", "⭐", "🎯", "🔔", "📌", "✨")
private val CARD_COLORS = listOf(0xFFF87171, 0xFFFB923C, 0xFFFBBF24, 0xFFEAB308, 0xFF84CC16, 0xFF14B8A6).map { Color(it) }

private fun <T> pick(list: List<T>, key: String): T {
    var sum = 0
    for (c in key) sum += c.code
    return list[sum % list.size]
}

private val DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)

private fun formatDueDate(raw: String): String =
    runCatching { LocalDate.parse(raw.take(10)).format(DUE_DATE_FORMAT) }.getOrDefault(raw)

/** Opens the platform date picker anchored on [initial], reporting the picked day. */
private fun pickDate(context: android.content.Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth
    ).show()
}

/**
 * Ports TasksPanel + TodayCards as one flat, tappable-to-focus task list, plus TasksPanel.js's
 * "Your brain suggests" chips (from /api/mind/queue), its This week/This month due-date
 * groupings, a due-date picker on add, a collapsible "distilled notes" tasks group, overdue
 * styling, and a reminder-driven highlight/glow + scroll-into-view (mirrors pages/work.js's
 * highlightKey). Also mirrors TodayCards.js's cross-device focus poll: tapping a task's focus
 * button while a session for that same task is already running elsewhere jumps straight to the
 * running clock instead of the picker. Simplified vs the web: no drag-to-reorder timeline — that
 * needs a canvas timeline, deferred to a later pass.
 */
@Composable
fun TasksPanel(onCompletion: (String) -> Unit, highlightTaskId: String? = null, onHighlightConsumed: () -> Unit = {}) {
    val tasks by TasksRepository.tasks.collectAsState()
    val error by TasksRepository.error.collectAsState()
    val queueItems by MindQueueRepository.items.collectAsState()
    val routines by PlannerRepository.routines.collectAsState()
    val plannerToday by PlannerRepository.today.collectAsState()
    var newTitle by remember { mutableStateOf("") }
    var newDueDate by remember { mutableStateOf<LocalDate?>(null) }
    var focusTask by remember { mutableStateOf<Task?>(null) }
    var focusResume by remember { mutableStateOf<FocusState?>(null) }
    var remoteFocus by remember { mutableStateOf<FocusState?>(null) }
    var glowTaskId by remember { mutableStateOf<String?>(null) }
    var showDistillTasks by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun openFocus(task: Task) {
        focusTask = task
        focusResume = remoteFocus?.takeIf { it.active && it.taskId == task.id }
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

    // A reminder's "Open" (NudgesStrip -> WorkScreen) lands here as [highlightTaskId] -- open the
    // distilled-notes group if that's where the task lives, glow its row briefly, then hand
    // control back to WorkScreen so a recomposition doesn't replay it.
    LaunchedEffect(highlightTaskId) {
        val id = highlightTaskId ?: return@LaunchedEffect
        if (tasks.any { it.id == id && it.noteId != null }) showDistillTasks = true
        glowTaskId = id
        onHighlightConsumed()
        delay(2500)
        glowTaskId = null
    }

    val suggestions = remember(queueItems) {
        queueItems.mapNotNull { item -> item.taskSuggestionTitle()?.let { title -> item to title } }
    }

    // Matches lib/plannerDay.js's dayEntries(): a routine active today (weekday match) shows as
    // a "virtual" occurrence unless a concrete planner_blocks row already materializes it for
    // today (that materialized row "wins" so completing/skipping today never edits the routine).
    val todayRoutineItems = remember(routines, plannerToday) {
        val todayStr = LocalDate.now().toString()
        val weekday = RoutineRepository.currentDayOfWeekIndex()
        // Matches lib/plannerDay.js's dayEntries() exactly: real = blocks whose status isn't
        // "dismissed" (that single condition doubles as both "covers the routine, don't show a
        // virtual placeholder" and "show this materialized card"). A "skipped" block still
        // covers *and* still shows (TodayCards.js only hides on "dismissed"), matching
        // components/TodayCards.js's skipRoutine(): a still-virtual occurrence is skipped by
        // POSTing status "skipped" (POST only accepts active/done/skipped), while skipping an
        // already-materialized block PATCHes it to "dismissed" — which, quirks included, is
        // exactly what un-covers it again so a fresh virtual placeholder regenerates next load.
        val real = plannerToday.blocks.filter {
            it.planDate.take(10) == todayStr && it.routineId != null && it.status != "dismissed"
        }
        val coveredRoutineIds = real.mapNotNull { it.routineId }.toSet()
        val materialized = real.mapNotNull { block ->
            val routine = routines.firstOrNull { it.id == block.routineId } ?: return@mapNotNull null
            TodayItem.RoutineItem(routine, block, virtual = false)
        }
        val virtual = routines
            .filter { it.active && it.days.contains(weekday) && it.id !in coveredRoutineIds }
            .map { routine -> TodayItem.RoutineItem(routine, block = null, virtual = true) }
        materialized + virtual
    }

    SbCard(topBorderColor = Violet400) {
        SbLabel("Tasks", color = Violet400)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                placeholder = { Text("Add a task", color = Mist400) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Violet400.copy(alpha = 0.6f),
                    unfocusedBorderColor = Ink500,
                    focusedTextColor = Mist100,
                    unfocusedTextColor = Mist100,
                    cursorColor = Violet400
                )
            )
            TextButton(
                onClick = { pickDate(context, newDueDate ?: LocalDate.now()) { newDueDate = it } },
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text(newDueDate?.let { formatDueDate(it.toString()) } ?: "Due date", color = Mist400, style = SecondBrainTypography.bodySmall)
            }
            IconButton(onClick = {
                val title = newTitle.trim()
                if (title.isNotBlank()) {
                    newTitle = ""
                    val due = newDueDate
                    newDueDate = null
                    scope.launch { TasksRepository.create(title, dueDate = due?.toString()) }
                }
            }) { Icon(Icons.Filled.Add, contentDescription = "Add task", tint = Violet400) }
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = Rose400, style = SecondBrainTypography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        val open = tasks.filter { !it.done }
        val done = tasks.filter { it.done }
        // Matches TasksPanel.js's distillTasks: every task spun off from a distilled note in
        // Organize, shown as its own collapsible group regardless of where its due date buckets
        // it (undated ones especially would otherwise be invisible).
        val distillTasks = remember(tasks) { tasks.filter { it.noteId != null } }

        val today = remember { LocalDate.now() }
        val weekEnd = remember { today.plusDays(6) }
        fun dueDateOf(t: Task): LocalDate? = t.dueDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        // Matches TasksPanel.js's weekTasks/monthTasks split: undated/overdue/due-today stays in
        // the flat "today" list; everything later buckets into exactly one of the two groups
        // below (so nothing with a due date silently disappears past the month window).
        val todayTasks = open.filter { val d = dueDateOf(it); d == null || d <= today }
        val weekTasks = open.filter { val d = dueDateOf(it); d != null && d > today && d <= weekEnd }
        val monthTasks = open.filter { val d = dueDateOf(it); d != null && d > weekEnd }

        if (distillTasks.isNotEmpty()) {
            TextButton(onClick = { showDistillTasks = !showDistillTasks }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(
                    "${if (showDistillTasks) "Hide" else "Show"} tasks from distilled notes (${distillTasks.size})",
                    color = Emerald400,
                    style = SecondBrainTypography.bodySmall
                )
            }
            if (showDistillTasks) {
                Spacer(Modifier.height(4.dp))
                distillTasks.forEach { task ->
                    TaskRow(
                        task = task,
                        onToggle = {
                            val willBeDone = !task.done
                            scope.launch { TasksRepository.setDone(task.id, willBeDone) }
                            if (willBeDone) onCompletion("task")
                        },
                        onFocus = if (!task.done) { { openFocus(task) } } else null,
                        onDelete = { scope.launch { TasksRepository.delete(task.id) } },
                        strikethrough = task.done,
                        highlighted = glowTaskId == task.id,
                        onSchedule = { date -> scope.launch { TasksRepository.setDueDate(task.id, date.toString()) } }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (tasks.isEmpty() && error == null && todayRoutineItems.isEmpty()) {
            Text("No tasks yet — add one above.", color = Mist400, style = SecondBrainTypography.bodySmall)
        }

        // Merged, time-sorted list (routines carry a concrete start_min; undated tasks sort last)
        // — matches TodayCards.js combining routine occurrences and tasks into one "Today" list.
        val combinedToday = remember(todayTasks, todayRoutineItems) {
            (todayTasks.map { TodayItem.TaskItem(it) } + todayRoutineItems)
                .sortedWith(compareBy({ it.startMin ?: Int.MAX_VALUE }, { it.title }))
        }

        combinedToday.forEach { item ->
            when (item) {
                is TodayItem.TaskItem -> TaskRow(
                    task = item.task,
                    onToggle = {
                        scope.launch {
                            TasksRepository.setDone(item.task.id, true)
                            onCompletion("task")
                        }
                    },
                    onFocus = { openFocus(item.task) },
                    onDelete = { scope.launch { TasksRepository.delete(item.task.id) } },
                    highlighted = glowTaskId == item.task.id
                )
                is TodayItem.RoutineItem -> RoutineTodayRow(
                    item = item,
                    onSkipToday = {
                        scope.launch {
                            val block = item.block
                            if (block != null) {
                                // Matches TodayCards.js's skipRoutine(): an existing block is
                                // PATCHed to "dismissed", not "skipped".
                                PlannerRepository.updateBlock(block.id, UpdatePlannerBlockRequest(status = "dismissed"))
                            } else {
                                // POST /api/planner only accepts active/done/skipped (not
                                // "dismissed"), so a still-virtual occurrence being skipped for
                                // the first time is created straight into "skipped" — matching
                                // TodayCards.js's skipRoutine() virtual branch exactly.
                                PlannerRepository.createBlock(
                                    CreatePlannerBlockRequest(
                                        title = item.routine.title,
                                        planDate = LocalDate.now().toString(),
                                        startMin = item.routine.startMin,
                                        durationMin = item.routine.durationMin,
                                        category = item.routine.category,
                                        routineId = item.routine.id,
                                        status = "skipped"
                                    )
                                )
                            }
                        }
                    }
                )
            }
        }

        if (done.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SbLabel("Done", color = Mist400)
            Spacer(Modifier.height(4.dp))
            done.take(5).forEach { task ->
                TaskRow(
                    task = task,
                    onToggle = { scope.launch { TasksRepository.setDone(task.id, false) } },
                    onFocus = null,
                    onDelete = { scope.launch { TasksRepository.delete(task.id) } },
                    strikethrough = true
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            BrainSuggestsSection(suggestions, scope)
        }

        if (weekTasks.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SbLabel("This week (${weekTasks.size})", color = Violet400)
            Spacer(Modifier.height(4.dp))
            weekTasks.forEach { task ->
                TaskRow(
                    task = task,
                    onToggle = { scope.launch { TasksRepository.setDone(task.id, true); onCompletion("task") } },
                    onFocus = { openFocus(task) },
                    onDelete = { scope.launch { TasksRepository.delete(task.id) } },
                    highlighted = glowTaskId == task.id
                )
            }
        }

        if (monthTasks.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SbLabel("This month (${monthTasks.size})", color = Violet400)
            Spacer(Modifier.height(4.dp))
            monthTasks.forEach { task ->
                TaskRow(
                    task = task,
                    onToggle = { scope.launch { TasksRepository.setDone(task.id, true); onCompletion("task") } },
                    onFocus = { openFocus(task) },
                    onDelete = { scope.launch { TasksRepository.delete(task.id) } },
                    highlighted = glowTaskId == task.id
                )
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

/** A row in the merged "Today" list — either a real task or a routine occurrence (materialized
 * or virtual-for-today), sorted together by [startMin] the same way the web app does. */
private sealed class TodayItem(val startMin: Int?, val title: String) {
    class TaskItem(val task: Task) : TodayItem(task.startMin, task.title)
    class RoutineItem(val routine: PlannerRoutine, val block: PlannerBlock?, val virtual: Boolean) :
        TodayItem(block?.startMin ?: routine.startMin, routine.title)
}

private fun formatMinuteOfDay(minute: Int): String {
    val h = (minute / 60) % 24
    val m = minute % 60
    return "%02d:%02d".format(h, m)
}

@Composable
private fun RoutineTodayRow(item: TodayItem.RoutineItem, onSkipToday: () -> Unit) {
    val startMin = item.startMin ?: item.routine.startMin
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Gold500.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) { Text("🔁", fontSize = 13.sp) }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(item.routine.title, color = Mist100, style = SecondBrainTypography.bodyMedium)
            Text(
                "${formatMinuteOfDay(startMin)} – ${formatMinuteOfDay(startMin + item.routine.durationMin)}",
                color = Mist300,
                style = SecondBrainTypography.bodySmall
            )
        }
        TextButton(onClick = onSkipToday) { Text("Skip today", color = Mist400, style = SecondBrainTypography.bodySmall) }
    }
}

@Composable
private fun BrainSuggestsSection(suggestions: List<Pair<MindQueueItem, String>>, scope: kotlinx.coroutines.CoroutineScope) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Violet400.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, Violet400.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            SbLabel("Your brain suggests", color = Violet400)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                suggestions.forEach { (item, title) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Ink950)
                            .border(BorderStroke(1.dp, Violet400.copy(alpha = 0.4f)), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(title, color = Mist100, style = SecondBrainTypography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "✓",
                            color = Emerald400,
                            style = SecondBrainTypography.bodyMedium,
                            modifier = Modifier.clickable { scope.launch { MindQueueRepository.accept(item, title) } }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "✕",
                            color = Mist400,
                            style = SecondBrainTypography.bodyMedium,
                            modifier = Modifier.clickable { scope.launch { MindQueueRepository.dismiss(item) } }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onFocus: (() -> Unit)?,
    onDelete: () -> Unit,
    strikethrough: Boolean = false,
    highlighted: Boolean = false,
    // Only the distilled-notes group offers this on web (TasksPanel.js's TaskGroup doesn't pass
    // onSchedule to This week/This month rows) — undated notes-spun tasks are the ones that
    // actually need a day picked for them.
    onSchedule: ((LocalDate) -> Unit)? = null
) {
    val context = LocalContext.current
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(highlighted) { if (highlighted) requester.bringIntoView() }

    val dueRaw = task.dueDate
    val dueDateLocal = dueRaw?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val overdue = dueDateLocal != null && dueDateLocal < LocalDate.now() && !task.done

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .let {
                if (highlighted) {
                    it.clip(RoundedCornerShape(8.dp))
                        .background(Emerald400.copy(alpha = 0.1f))
                        .border(BorderStroke(1.dp, Emerald400), RoundedCornerShape(8.dp))
                } else it
            }
            .padding(vertical = 4.dp, horizontal = if (highlighted) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(pick(CARD_COLORS, task.id).copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) { Text(pick(CARD_ICONS, task.id), fontSize = 13.sp) }
        Spacer(Modifier.width(6.dp))
        Checkbox(
            checked = task.done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Emerald400)
        )
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                color = if (strikethrough) Mist400 else Mist100,
                style = SecondBrainTypography.bodyMedium,
                textDecoration = if (strikethrough) TextDecoration.LineThrough else null
            )
            Text(
                when {
                    dueDateLocal == null -> "No due date"
                    overdue -> "overdue: ${formatDueDate(dueRaw)}"
                    else -> formatDueDate(dueRaw)
                },
                color = if (overdue) Red400 else Mist300,
                style = SecondBrainTypography.bodySmall
            )
        }
        if (onSchedule != null && !task.done) {
            if (dueDateLocal != LocalDate.now()) {
                TextButton(onClick = { onSchedule(LocalDate.now()) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Today", color = Emerald400, style = SecondBrainTypography.bodySmall)
                }
            }
            IconButton(onClick = { pickDate(context, dueDateLocal ?: LocalDate.now(), onSchedule) }) {
                Icon(Icons.Filled.DateRange, contentDescription = "Schedule", tint = Mist400)
            }
        }
        if (onFocus != null) {
            IconButton(onClick = onFocus) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Start focus", tint = Violet400)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Delete task", tint = Mist400)
        }
    }
}
