package com.secondbrain.lock.ui.screens.work

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.ui.components.BreakdownRow
import com.secondbrain.lock.ui.components.BreakdownSuggestions
import com.secondbrain.lock.ui.nav.pickTime
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Owns one open "break this task down" flow: request -> loading/error -> suggestion rows (each
 * with a time slot computed by stacking sequentially after the parent task's own scheduled end,
 * or after "now" for an undated/untimed parent) -> add/discard/recurse. Built as a single
 * controller, shared by TasksPanel's compact card and AllTasksScreen's Today tab (P13's "surface
 * it everywhere" requirement), rather than duplicating this state per screen. Only one task's
 * breakdown is ever open at a time, same as the long-press flow this replaces/extends. */
internal class TaskBreakdownController(
    private val scope: CoroutineScope,
    private val context: Context
) {
    var taskId by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var rows by mutableStateOf<List<BreakdownRow>>(emptyList()); private set
    var remainingToday by mutableStateOf<Int?>(null); private set
    var furtherLoadingId by mutableStateOf<String?>(null); private set

    private var parentTask: Task? = null
    private var baseMin: Int = 0

    fun request(task: Task) {
        if (loading) return
        taskId = task.id
        parentTask = task
        rows = emptyList()
        if (remainingToday == 0) {
            error = "0 task breakdowns left today"
            return
        }
        error = null
        loading = true
        baseMin = task.startMin?.let { it + (task.durationMin ?: 0) } ?: currentMinuteOfDay()
        scope.launch {
            val result = TasksRepository.breakdown(task.title)
            loading = false
            result.onSuccess { resp ->
                rows = resp.subtasks.map { BreakdownRow(subtask = it) }
                remainingToday = resp.remainingToday
            }.onFailure {
                error = it.message ?: "Breakdown failed"
            }
        }
    }

    fun dismiss() {
        taskId = null
        rows = emptyList()
        error = null
    }

    /** Seeds an already-open breakdown flow directly with rows fetched elsewhere (P14's "Make
     * these separate tasks instead" escape hatch redirects FocusReadyScreen's own AI suggestions
     * here) — avoids calling the breakdown endpoint a second time for the identical rows, which
     * would burn additional quota and could return a different result than what's on screen. */
    fun seed(task: Task, seededRows: List<BreakdownRow>, seededRemainingToday: Int?) {
        taskId = task.id
        parentTask = task
        rows = seededRows
        remainingToday = seededRemainingToday
        error = null
        loading = false
        baseMin = task.startMin?.let { it + (task.durationMin ?: 0) } ?: currentMinuteOfDay()
    }

    /** Cumulative start minute for [row], computed live from the current row order — recursion
     * (splicing a subtask's own further breakdown in at its position) naturally gets correct
     * stacking for free since this always walks the live list, never a cached value. */
    private fun windowFor(row: BreakdownRow): Int? {
        var cursor = baseMin
        rows.forEach { r ->
            val start = cursor
            cursor += r.subtask.estimatedMinutes
            if (r.id == row.id) return start
        }
        return null
    }

    fun subtitleFor(row: BreakdownRow): String {
        val start = windowFor(row)
        return if (start != null && start in 0 until 1440) {
            "${formatMinuteOfDay(start)} – ${formatMinuteOfDay(start + row.subtask.estimatedMinutes)}"
        } else {
            "No room left today — pick another day"
        }
    }

    fun fitsToday(row: BreakdownRow): Boolean = (windowFor(row) ?: -1) in 0 until 1440

    // Mirrors TasksPanel's original single-suggestion add: create the subtask as a real task at
    // its computed slot, then push every other still-open today task at or after that slot back
    // by the same duration so the rest of the day shifts to make room.
    private suspend fun performAdd(row: BreakdownRow, startMin: Int): Boolean {
        val parent = parentTask ?: return false
        return TasksRepository.create(title = row.subtask.topic, dueDate = parent.dueDate).onSuccess { created ->
            TasksRepository.reschedule(created.id, startMin, row.subtask.estimatedMinutes)
            TasksRepository.tasks.value
                .filter { it.id != created.id && !it.done && isTodayTask(it, LocalDate.now()) && (it.startMin ?: -1) >= startMin }
                .forEach { t ->
                    val shiftedStart = (t.startMin ?: 0) + row.subtask.estimatedMinutes
                    // Same start_min < 1440 constraint as the suggestion itself — leave a task
                    // that would get pushed past midnight where it is rather than send an invalid
                    // value (better a stale time than a failed request).
                    if (shiftedStart < 1440) {
                        TasksRepository.reschedule(t.id, shiftedStart, t.durationMin ?: 0)
                    }
                }
            rows = rows.filterNot { it.id == row.id }
            if (rows.isEmpty()) taskId = null
        }.isSuccess
    }

    fun add(row: BreakdownRow) {
        val start = windowFor(row)
        if (start == null || start !in 0 until 1440) {
            Toast.makeText(context, "That falls after midnight — no room left today", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            if (!performAdd(row, start)) {
                Toast.makeText(context, "Couldn't add subtask", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun addAll() {
        // Freeze every row's slot against the CURRENT stacking before any of them are added —
        // adding sequentially would otherwise recompute a later row's slot against a shrinking
        // list and stack it too early, on top of time an earlier add already reserved.
        val windows = rows.associateWith { windowFor(it) }
        scope.launch {
            rows.toList().forEach { row ->
                val start = windows[row]
                if (start != null && start in 0 until 1440) performAdd(row, start)
            }
        }
    }

    private fun addOnDate(row: BreakdownRow, date: LocalDate, time: LocalTime) {
        scope.launch {
            TasksRepository.create(title = row.subtask.topic, dueDate = date.toString()).onSuccess { created ->
                TasksRepository.reschedule(created.id, time.hour * 60 + time.minute, row.subtask.estimatedMinutes)
                rows = rows.filterNot { it.id == row.id }
                if (rows.isEmpty()) taskId = null
            }.onFailure {
                Toast.makeText(context, it.message ?: "Couldn't add subtask", Toast.LENGTH_LONG).show()
            }
        }
    }

    // A suggestion that doesn't fit today gets scheduled onto a day/time the user picks instead —
    // same calendar/clock dialogs as the nav bar's "+" quick-add, with today blocked since that's
    // exactly the day this suggestion didn't fit into.
    fun scheduleForLater(row: BreakdownRow) {
        val tomorrow = LocalDate.now().plusDays(1)
        pickDate(context, initial = tomorrow, minDate = tomorrow) { date ->
            pickTime(context, initial = LocalTime.of(9, 0)) { time -> addOnDate(row, date, time) }
        }
    }

    fun discard(row: BreakdownRow) {
        rows = rows.filterNot { it.id == row.id }
        if (rows.isEmpty()) taskId = null
    }

    fun discardAll() = dismiss()

    fun breakdownFurther(row: BreakdownRow) {
        if (furtherLoadingId != null) return
        if (remainingToday == 0) {
            Toast.makeText(context, "0 task breakdowns left today", Toast.LENGTH_SHORT).show()
            return
        }
        furtherLoadingId = row.id
        scope.launch {
            val result = TasksRepository.breakdown(row.subtask.topic)
            furtherLoadingId = null
            result.onSuccess { resp ->
                val index = rows.indexOfFirst { it.id == row.id }
                if (index == -1) return@onSuccess
                val expanded = resp.subtasks.map { BreakdownRow(subtask = it) }
                rows = rows.toMutableList().apply {
                    removeAt(index)
                    addAll(index, expanded)
                }
                remainingToday = resp.remainingToday
            }.onFailure {
                Toast.makeText(context, it.message ?: "Couldn't break that down further", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
internal fun rememberTaskBreakdownController(): TaskBreakdownController {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember { TaskBreakdownController(scope, context) }
}

/** The temporary block shown under a task right after its breakdown is requested: a loading line
 * while in flight, the server's error message verbatim on failure (matches the API's "show this
 * verbatim" guidance for the 429 daily-limit case), or the shared [BreakdownSuggestions] list. */
@Composable
internal fun TaskBreakdownBlock(controller: TaskBreakdownController) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 26.dp, bottom = 8.dp)) {
        when {
            controller.loading -> BreakdownLoadingDots(modifier = Modifier.padding(vertical = 4.dp))
            controller.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(controller.error!!, color = Rose400, style = SecondBrainTypography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    "✕",
                    color = Mist400,
                    style = SecondBrainTypography.bodySmall,
                    modifier = Modifier.clickable(onClick = controller::dismiss).padding(start = 8.dp)
                )
            }
            controller.rows.isNotEmpty() -> BreakdownSuggestions(
                rows = controller.rows,
                remainingToday = controller.remainingToday,
                onAdd = controller::add,
                onDiscard = controller::discard,
                onAddAll = controller::addAll,
                onDiscardAll = controller::discardAll,
                onBreakdownFurther = controller::breakdownFurther,
                subtitleFor = controller::subtitleFor,
                addEnabledFor = controller::fitsToday,
                altAction = { row -> ScheduleLaterAction(onClick = { controller.scheduleForLater(row) }) },
                breakdownFurtherLoadingId = controller.furtherLoadingId
            )
        }
    }
}

@Composable
private fun ScheduleLaterAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarToday, contentDescription = "Pick a day", tint = StreakAccent, modifier = Modifier.padding(end = 2.dp))
            Icon(Icons.Filled.AccessTime, contentDescription = "Pick a time", tint = StreakAccent)
        }
    }
}
