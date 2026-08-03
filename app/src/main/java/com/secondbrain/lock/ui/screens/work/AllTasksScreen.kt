package com.secondbrain.lock.ui.screens.work

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.MindQueueRepository
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.data.repo.StatsRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.dto.CreatePlannerBlockRequest
import com.secondbrain.lock.network.dto.MindQueueItem
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.network.dto.UpdatePlannerBlockRequest
import com.secondbrain.lock.network.dto.taskSuggestionTitle
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Red400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.StreakSilver
import com.secondbrain.lock.ui.theme.Violet400
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class TasksTab(val label: String) { TODAY("Today"), WEEK("This week"), MONTH("This month"), MORE("More tasks") }

/**
 * The "See all" destination from the Tasks Section design: same dot-and-line timeline as the
 * compact card, but full untruncated titles, a Today/This week/This month breakdown, and the
 * completed-tasks ("Done") list — none of which belong on the compact card per the design.
 */
@Composable
fun AllTasksScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val tasks by TasksRepository.tasks.collectAsState()
    val queueItems by MindQueueRepository.items.collectAsState()
    val routines by PlannerRepository.routines.collectAsState()
    val plannerToday by PlannerRepository.today.collectAsState()
    var focusTask by remember { mutableStateOf<Task?>(null) }
    var tab by remember { mutableStateOf(TasksTab.TODAY) }
    var nowMinute by remember { mutableStateOf(currentMinuteOfDay()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMinute = currentMinuteOfDay()
        }
    }

    val today = remember { LocalDate.now() }
    val weekEnd = remember(today) { today.plusDays(6) }
    val open = remember(tasks) { tasks.filter { !it.done } }
    val done = remember(tasks) { tasks.filter { it.done } }
    val todayTasks = remember(open, today) { open.filter { isTodayTask(it, today) } }
    val weekTasks = remember(open, today, weekEnd) { open.filter { val d = dueDateOf(it); d != null && d > today && d <= weekEnd } }
    val monthTasks = remember(open, weekEnd) { open.filter { val d = dueDateOf(it); d != null && d > weekEnd } }
    val draftTasks = remember(open, today) { open.filter { isDraftTask(it, today) } }
    val overdueTasks = remember(open, today) { open.filter { isOverdueTask(it, today) } }
    val combinedToday = remember(todayTasks, routines, plannerToday, nowMinute) { buildTodayItems(todayTasks, routines, plannerToday, nowMinute) }
    val suggestions = remember(queueItems) {
        queueItems.mapNotNull { item -> item.taskSuggestionTitle()?.let { title -> item to title } }
    }

    // Stats still bump from this screen (mirrors WorkScreen's handleCompletion), but the
    // celebration overlay is a WorkScreen-only flourish — this is a secondary/detail screen.
    fun toggleDone(task: Task, value: Boolean) {
        scope.launch {
            TasksRepository.setDone(task.id, value).onSuccess {
                if (value) StatsRepository.bumpTaskDone()
            }
        }
    }
    fun deleteTask(task: Task) {
        scope.launch { TasksRepository.delete(task.id) }
    }
    fun openRoutineFocus(item: TodayItem.RoutineItem) {
        focusTask = Task(id = "", title = item.routine.title)
    }
    fun toggleRoutineDone(item: TodayItem.RoutineItem, value: Boolean) {
        scope.launch {
            val block = item.block
            if (block != null) {
                PlannerRepository.updateBlock(block.id, UpdatePlannerBlockRequest(status = if (value) "done" else "active"))
            } else if (value) {
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
            if (value) StatsRepository.bumpTaskDone()
        }
    }

    Box(Modifier.fullAuraBackground().fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp
                )
        ) {
            AllTasksHeader(onBack)
            Spacer(Modifier.height(18.dp))
            TabsRow(selected = tab, onSelect = { tab = it })
            Spacer(Modifier.height(16.dp))

            SbCard {
                when (tab) {
                    TasksTab.TODAY -> {
                        if (suggestions.isNotEmpty()) {
                            BrainSuggestsSection(suggestions, scope)
                            Spacer(Modifier.height(16.dp))
                        }
                        TodayTimelineList(
                            items = combinedToday,
                            nowMinute = nowMinute,
                            onToggleTask = { toggleDone(it, true) },
                            onFocusTask = { focusTask = it },
                            onDeleteTask = ::deleteTask,
                            onToggleRoutine = ::toggleRoutineDone,
                            onFocusRoutine = ::openRoutineFocus
                        )
                    }
                    TasksTab.WEEK -> TaskTimelineList(weekTasks, nowMinute = nowMinute, onToggle = { toggleDone(it, true) }, onFocus = { focusTask = it }, onDelete = ::deleteTask)
                    TasksTab.MONTH -> TaskTimelineList(monthTasks, nowMinute = nowMinute, onToggle = { toggleDone(it, true) }, onFocus = { focusTask = it }, onDelete = ::deleteTask)
                    TasksTab.MORE -> {
                        SbLabel("Overdue", color = Red400)
                        Spacer(Modifier.height(10.dp))
                        TaskTimelineList(overdueTasks, nowMinute = nowMinute, onToggle = { toggleDone(it, true) }, onFocus = { focusTask = it }, onDelete = ::deleteTask)
                        Spacer(Modifier.height(20.dp))
                        SbLabel("Drafts", color = Mist400)
                        Spacer(Modifier.height(10.dp))
                        TaskTimelineList(draftTasks, nowMinute = nowMinute, onToggle = { toggleDone(it, true) }, onFocus = { focusTask = it }, onDelete = ::deleteTask)
                    }
                }
            }

            if (done.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SbCard {
                    SbLabel("Done", color = Mist400)
                    Spacer(Modifier.height(10.dp))
                    done.forEachIndexed { index, task ->
                        TimelineRow(
                            icon = RowIcon.TASK,
                            title = task.title,
                            subtitle = if (task.pendingSync) "Syncing…" else "Completed",
                            subtitleColor = Mist400,
                            bg = rowBg(index),
                            dotColor = StreakSilver,
                            showLineAbove = index != 0,
                            showLineBelow = index != done.lastIndex,
                            done = true,
                            highlighted = false,
                            truncateTitle = false,
                            onToggle = { toggleDone(task, false) },
                            onFocus = null,
                            onDelete = { deleteTask(task) }
                        )
                    }
                }
            }
        }
    }

    focusTask?.let { task ->
        FocusPomodoroDialog(
            task = task,
            resume = null,
            onDismiss = { focusTask = null },
            onCompleted = { focusTask = null; StatsRepository.bumpFocusSession() }
        )
    }
}

@Composable
private fun AllTasksHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Ink900)
                .border(BorderStroke(1.dp, Ink600), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = Mist100, fontSize = 16.sp) }
        Spacer(Modifier.width(14.dp))
        Text("All Tasks", color = Mist100, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TabsRow(selected: TasksTab, onSelect: (TasksTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Ink900)
            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(50))
            .padding(6.dp)
    ) {
        TasksTab.entries.forEach { t ->
            val isSelected = t == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Mist100 else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t.label,
                    color = if (isSelected) Ink950 else Mist300,
                    style = SecondBrainTypography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TodayTimelineList(
    items: List<TodayItem>,
    nowMinute: Int,
    onToggleTask: (Task) -> Unit,
    onFocusTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onToggleRoutine: (TodayItem.RoutineItem, Boolean) -> Unit,
    onFocusRoutine: (TodayItem.RoutineItem) -> Unit
) {
    if (items.isEmpty()) {
        Text("Nothing here.", color = Mist400, style = SecondBrainTypography.bodySmall)
        return
    }
    val nextUp = remember(items, nowMinute) { nextUpIndex(items, nowMinute) }
    items.forEachIndexed { index, item ->
        val isFirst = index == 0
        val isLast = index == items.lastIndex
        val dotColor = if (item.isHappeningNow(nowMinute) || index == nextUp) StreakAccent else StreakSilver
        when (item) {
            is TodayItem.TaskItem -> {
                val info = taskSubtitleInfo(item.task)
                TimelineRow(
                    icon = RowIcon.TASK,
                    title = item.task.title,
                    subtitle = if (item.task.pendingSync) "Syncing… · ${info.text}" else info.text,
                    subtitleColor = if (info.overdue) Red400 else Mist300,
                    bg = rowBg(index),
                    dotColor = dotColor,
                    showLineAbove = !isFirst,
                    showLineBelow = !isLast,
                    done = false,
                    highlighted = false,
                    truncateTitle = false,
                    onToggle = { onToggleTask(item.task) },
                    onFocus = { onFocusTask(item.task) },
                    onDelete = { onDeleteTask(item.task) }
                )
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
                truncateTitle = false,
                onToggle = { onToggleRoutine(item, item.block?.status != "done") },
                onFocus = { onFocusRoutine(item) },
                onDelete = null
            )
        }
    }
}

@Composable
private fun TaskTimelineList(
    tasks: List<Task>,
    nowMinute: Int,
    onToggle: (Task) -> Unit,
    onFocus: (Task) -> Unit,
    onDelete: (Task) -> Unit
) {
    if (tasks.isEmpty()) {
        Text("Nothing here.", color = Mist400, style = SecondBrainTypography.bodySmall)
        return
    }
    tasks.forEachIndexed { index, task ->
        val info = taskSubtitleInfo(task)
        TimelineRow(
            icon = RowIcon.TASK,
            title = task.title,
            subtitle = if (task.pendingSync) "Syncing… · ${info.text}" else info.text,
            subtitleColor = if (info.overdue) Red400 else Mist300,
            bg = rowBg(index),
            dotColor = if (isCurrentNow(task.startMin, task.durationMin, nowMinute)) StreakAccent else StreakSilver,
            showLineAbove = index != 0,
            showLineBelow = index != tasks.lastIndex,
            done = false,
            highlighted = false,
            truncateTitle = false,
            onToggle = { onToggle(task) },
            onFocus = { onFocus(task) },
            onDelete = { onDelete(task) }
        )
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
