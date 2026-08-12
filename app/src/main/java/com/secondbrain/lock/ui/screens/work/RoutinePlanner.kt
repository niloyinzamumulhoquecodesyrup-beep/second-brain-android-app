package com.secondbrain.lock.ui.screens.work

import android.app.TimePickerDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.CreatePlannerRoutineRequest
import com.secondbrain.lock.network.dto.PlannerRoutine
import com.secondbrain.lock.network.dto.UpdatePlannerRoutineRequest
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.Violet400
import kotlinx.coroutines.launch

/** Matches components/RoutinePlanner.js's STARTER_ROUTINES exactly (title/category/time/days). */
private data class StarterRoutine(val title: String, val startMin: Int, val durationMin: Int, val category: String, val days: List<Int>)

private val ALL_DAYS = listOf(0, 1, 2, 3, 4, 5, 6)
private val WEEKDAYS = listOf(0, 1, 2, 3, 4)

private val STARTER_ROUTINES = listOf(
    StarterRoutine("Sleep at 11 PM", 23 * 60, 480, "sleep", ALL_DAYS),
    StarterRoutine("Morning yoga + freshen up", 7 * 60, 60, "exercise", ALL_DAYS),
    StarterRoutine("Breakfast", 8 * 60, 60, "meals", ALL_DAYS),
    StarterRoutine("Deep work block", 9 * 60, 180, "work", WEEKDAYS),
    StarterRoutine("Lunch", 13 * 60, 60, "meals", ALL_DAYS),
    StarterRoutine("Evening reading", 21 * 60, 60, "leisure", ALL_DAYS)
)

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/** Matches RoutinePlanner.js's CATEGORY_COLORS exactly. */
private val CATEGORY_COLORS = linkedMapOf(
    "sleep" to Color(0xFF8FB8F2),
    "work" to Color(0xFF5EEAD4),
    "study" to Color(0xFFB7A6F7),
    "exercise" to Color(0xFF6EE796),
    "meals" to Color(0xFFF0D9A3),
    "leisure" to Color(0xFFFB7185),
    "other" to Color(0xFF9AA4AE)
)
private val CATEGORIES = CATEGORY_COLORS.keys.toList()

/** Ports RoutinePlanner: first-run free-text prompt, starter chips, day-agnostic list with an
 * inline time/duration editor per routine, and a full add-routine form (title/category/time/
 * duration/days) — mirrors components/RoutinePlanner.js field-for-field. */
@Composable
fun RoutinePlanner() {
    val routines by PlannerRepository.routines.collectAsState()
    val error by PlannerRepository.error.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var routineAnswer by remember { mutableStateOf("") }
    var routineAnswerSent by remember { mutableStateOf(false) }
    var routineAnswerBusy by remember { mutableStateOf(false) }
    var editingRoutineId by remember { mutableStateOf<String?>(null) }

    StreakSurface {
        SbSectionTitle("Routines", color = StreakAccent)
        Spacer(Modifier.height(10.dp))

        if (error != null) {
            Text(error!!, color = Mist300, style = SecondBrainTypography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        if (routines.isEmpty()) {
            SbLabel("First run", color = Emerald400)
            Spacer(Modifier.height(4.dp))
            Text("What do you do on a regular basis?", color = Mist100, style = SecondBrainTypography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            if (routineAnswerSent) {
                Text("Saved. Your next mind cycle will turn this into suggestions.", color = Emerald400, style = SecondBrainTypography.bodySmall)
            } else {
                Text(
                    "Tell your brain in plain words: when you sleep and wake, what your mornings look like, which days you exercise. Or just tap the ready-made ones below.",
                    color = Mist400,
                    style = SecondBrainTypography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = routineAnswer,
                    onValueChange = { routineAnswer = it },
                    placeholder = { Text("e.g. I sleep at 11 and wake at 7, take an hour for breakfast and yoga, gym on Mon/Wed/Fri…", color = Mist400) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StreakAccent.copy(alpha = 0.6f),
                        unfocusedBorderColor = Ink500,
                        focusedTextColor = Mist100,
                        unfocusedTextColor = Mist100,
                        cursorColor = StreakAccent
                    )
                )
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        val text = routineAnswer.trim()
                        if (text.isEmpty()) return@TextButton
                        routineAnswerBusy = true
                        scope.launch {
                            ApiClient.postPlannerPrompt(text)
                            routineAnswerBusy = false
                            routineAnswer = ""
                            routineAnswerSent = true
                        }
                    },
                    enabled = !routineAnswerBusy && routineAnswer.isNotBlank()
                ) { Text("send to your brain", color = StreakAccent) }
            }
            Spacer(Modifier.height(10.dp))
        }

        val starters = STARTER_ROUTINES.filter { s -> routines.none { it.title.equals(s.title, ignoreCase = true) } }
        if (starters.isNotEmpty()) {
            Text("Tap to add", color = Mist400, style = SecondBrainTypography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                starters.forEach { starter ->
                    AssistChip(
                        onClick = {
                            scope.launch {
                                PlannerRepository.createRoutine(
                                    CreatePlannerRoutineRequest(
                                        title = starter.title,
                                        startMin = starter.startMin,
                                        durationMin = starter.durationMin,
                                        category = starter.category,
                                        days = starter.days,
                                        source = "starter"
                                    )
                                )
                                // P18: the sectograph draws from RoutineRepository's separate
                                // Room cache, not this StateFlow — without this it'd show stale
                                // arcs until its own periodic refresh caught up.
                                com.secondbrain.lock.widget.SectographUpdateWorker.requestImmediateUpdate(context)
                            }
                        },
                        label = { Text("+ ${starter.title}") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        routines.forEach { routine ->
            RoutineRow(
                routine = routine,
                editing = editingRoutineId == routine.id,
                onStartEdit = { editingRoutineId = routine.id },
                onCancelEdit = { editingRoutineId = null },
                onSaveTime = { startMin, durationMin ->
                    editingRoutineId = null
                    scope.launch {
                        PlannerRepository.updateRoutine(routine.id, UpdatePlannerRoutineRequest(startMin = startMin, durationMin = durationMin))
                        com.secondbrain.lock.widget.SectographUpdateWorker.requestImmediateUpdate(context)
                    }
                },
                onToggleActive = {
                    scope.launch {
                        PlannerRepository.updateRoutine(routine.id, UpdatePlannerRoutineRequest(active = !routine.active))
                        com.secondbrain.lock.widget.SectographUpdateWorker.requestImmediateUpdate(context)
                    }
                },
                onToggleDay = { day ->
                    val next = if (routine.days.contains(day)) routine.days - day else (routine.days + day).sorted()
                    if (next.isNotEmpty()) {
                        scope.launch {
                            PlannerRepository.updateRoutine(routine.id, UpdatePlannerRoutineRequest(days = next))
                            com.secondbrain.lock.widget.SectographUpdateWorker.requestImmediateUpdate(context)
                        }
                    }
                },
                onDelete = {
                    scope.launch {
                        PlannerRepository.deleteRoutine(routine.id)
                        com.secondbrain.lock.widget.SectographUpdateWorker.requestImmediateUpdate(context)
                    }
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        NewRoutineForm(onCreate = { request ->
            scope.launch {
                PlannerRepository.createRoutine(request)
                com.secondbrain.lock.widget.SectographUpdateWorker.requestImmediateUpdate(context)
            }
        })
    }
}

@Composable
private fun RoutineRow(
    routine: PlannerRoutine,
    editing: Boolean,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveTime: (startMin: Int, durationMin: Int) -> Unit,
    onToggleActive: () -> Unit,
    onToggleDay: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var editStartMin by remember(editing) { mutableStateOf(routine.startMin) }
    var editDuration by remember(editing) { mutableStateOf(routine.durationMin.toString()) }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CATEGORY_COLORS[routine.category] ?: CATEGORY_COLORS.getValue("other"))
            )
            Spacer(Modifier.width(8.dp))
            Text(routine.title, color = Mist100, style = SecondBrainTypography.bodyMedium, modifier = Modifier.weight(1f))
            if (routine.source == "cycle") {
                Text("from your brain", color = Violet400, fontSize = 10.sp, modifier = Modifier.padding(end = 6.dp))
            }
            if (!editing) {
                Text(
                    "${formatTime(routine.startMin)} – ${formatTime(routine.startMin + routine.durationMin)}",
                    color = Mist400,
                    style = SecondBrainTypography.bodySmall,
                    modifier = Modifier.clickable { onStartEdit() }
                )
            }
        }

        if (editing) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        val h = editStartMin / 60
                        val m = editStartMin % 60
                        TimePickerDialog(context, { _, hourOfDay, minute -> editStartMin = hourOfDay * 60 + minute }, h, m, true).show()
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text(formatTime(editStartMin), color = Mist300) }
                OutlinedTextField(
                    value = editDuration,
                    onValueChange = { editDuration = it.filter(Char::isDigit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(64.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = Mist100, focusedTextColor = Mist100)
                )
                Text("min", color = Mist400, style = SecondBrainTypography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val duration = editDuration.toIntOrNull()?.coerceAtLeast(15) ?: routine.durationMin
                    onSaveTime(editStartMin, duration)
                }) { Text("save", color = StreakAccent) }
                TextButton(onClick = onCancelEdit) { Text("cancel", color = Mist400) }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DAY_LETTERS.forEachIndexed { index, letter ->
                val on = routine.days.contains(index)
                Box(
                    modifier = Modifier
                        .size(width = 24.dp, height = 22.dp)
                        .padding(end = 3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (on) Emerald400.copy(alpha = 0.2f) else Ink800)
                        .clickable { onToggleDay(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(letter, color = if (on) Emerald400 else Mist400, fontSize = 10.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleActive, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(if (routine.active) "pause" else "resume", color = Mist300, style = SecondBrainTypography.bodySmall)
            }
            TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("delete", color = Rose400, style = SecondBrainTypography.bodySmall)
            }
        }
    }
}

/** Ports RoutinePlanner.js's NewRoutineForm: collapsed to a "+ new routine" chip, expands into
 * title/category/time/duration/days. */
@Composable
private fun NewRoutineForm(onCreate: (CreatePlannerRoutineRequest) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("other") }
    var startMin by remember { mutableStateOf(9 * 60) }
    var duration by remember { mutableStateOf("60") }
    var days by remember { mutableStateOf(ALL_DAYS) }
    val context = LocalContext.current

    if (!open) {
        AssistChip(onClick = { open = true }, label = { Text("+ new routine") })
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink800)
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("routine name", color = Mist400) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = StreakAccent.copy(alpha = 0.6f),
                unfocusedBorderColor = Ink500,
                focusedTextColor = Mist100,
                unfocusedTextColor = Mist100,
                cursorColor = StreakAccent
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            CATEGORIES.forEach { c ->
                val selected = category == c
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) (CATEGORY_COLORS.getValue(c)).copy(alpha = 0.2f) else Ink800)
                        .border(BorderStroke(1.dp, if (selected) CATEGORY_COLORS.getValue(c) else Ink500), RoundedCornerShape(50))
                        .clickable { category = c }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CATEGORY_COLORS.getValue(c)))
                    Spacer(Modifier.width(6.dp))
                    Text(c, color = if (selected) Mist100 else Mist400, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    val h = startMin / 60
                    val m = startMin % 60
                    TimePickerDialog(context, { _, hourOfDay, minute -> startMin = hourOfDay * 60 + minute }, h, m, true).show()
                },
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) { Text(formatTime(startMin), color = Mist300) }
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it.filter(Char::isDigit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(64.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = Mist100, focusedTextColor = Mist100)
            )
            Text("min", color = Mist400, style = SecondBrainTypography.bodySmall, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DAY_LETTERS.forEachIndexed { index, letter ->
                val on = days.contains(index)
                Box(
                    modifier = Modifier
                        .size(width = 24.dp, height = 22.dp)
                        .padding(end = 3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (on) Emerald400.copy(alpha = 0.2f) else Ink800)
                        .border(BorderStroke(1.dp, Ink500), RoundedCornerShape(4.dp))
                        .clickable { days = if (on) days - index else (days + index).sorted() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(letter, color = if (on) Emerald400 else Mist400, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    if (title.isBlank() || days.isEmpty()) return@TextButton
                    onCreate(
                        CreatePlannerRoutineRequest(
                            title = title.trim(),
                            startMin = startMin,
                            durationMin = duration.toIntOrNull()?.coerceAtLeast(15) ?: 60,
                            category = category,
                            days = days
                        )
                    )
                    open = false
                    title = ""
                },
                enabled = title.isNotBlank() && days.isNotEmpty()
            ) { Text("add", color = StreakAccent) }
            TextButton(onClick = { open = false }) { Text("cancel", color = Mist400) }
        }
    }
}

private fun formatTime(startMin: Int): String {
    val normalized = ((startMin % 1440) + 1440) % 1440
    val h = normalized / 60
    val m = normalized % 60
    return "%02d:%02d".format(h, m)
}
