package com.secondbrain.lock.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import android.app.TimePickerDialog
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.ui.screens.work.formatMinuteOfDay
import com.secondbrain.lock.ui.screens.work.pickDate
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Opens the platform time picker anchored on [initial], reporting the picked time-of-day. Shared
 * with TasksPanel's "schedule a breakdown suggestion for a later day" flow. */
internal fun pickTime(context: android.content.Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour, initial.minute, true
    ).show()
}

/** The nav bar's "+" button — pick between a quick task and the full capture flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddChooserSheet(onDismiss: () -> Unit, onPickTask: () -> Unit, onPickCapture: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink900) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            SbLabel("Quick add")
            Spacer(Modifier.height(14.dp))
            ChooserRow(icon = "✅", title = "Create a task", subtitle = "Straight onto today's list", onClick = onPickTask)
            Spacer(Modifier.height(10.dp))
            ChooserRow(icon = "📝", title = "Capture", subtitle = "Save a note, link, or idea", onClick = onPickCapture)
        }
    }
}

@Composable
private fun ChooserRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink800)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(StreakAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 18.sp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Mist100, style = SecondBrainTypography.bodyMedium)
            Text(subtitle, color = Mist300, style = SecondBrainTypography.bodySmall)
        }
    }
}

private val QUICK_DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** The "Create a task" branch — same fields TasksPanel's inline add-row used to offer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddTaskSheet(onDismiss: () -> Unit, onCreated: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var startTime by remember { mutableStateOf<LocalTime?>(null) }
    var durationMin by remember { mutableStateOf(30) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink900) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SbLabel("New task")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Mist400)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("What do you need to do?", color = Mist400) },
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
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickDate(context, dueDate ?: LocalDate.now()) { dueDate = it } }) {
                    Text(
                        dueDate?.let { it.format(QUICK_DUE_DATE_FORMAT) } ?: "Due date (optional)",
                        color = Mist400,
                        style = SecondBrainTypography.bodySmall
                    )
                }
                TextButton(onClick = { pickTime(context, startTime ?: LocalTime.of(9, 0)) { startTime = it } }) {
                    Text(
                        startTime?.let { formatMinuteOfDay(it.hour * 60 + it.minute) } ?: "Time (optional)",
                        color = Mist400,
                        style = SecondBrainTypography.bodySmall
                    )
                }
            }
            if (startTime != null) {
                Spacer(Modifier.height(6.dp))
                Row {
                    listOf(15, 30, 45, 60).forEach { m ->
                        FilterChip(
                            selected = durationMin == m,
                            onClick = { durationMin = m },
                            label = { Text("${m}m") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = StreakAccent.copy(alpha = 0.3f))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val t = title.trim()
                    if (t.isNotBlank()) {
                        saving = true
                        scope.launch {
                            // The create endpoint only takes title/due date/note — a chosen time
                            // is applied with a follow-up PUT, same as TasksPanel's reschedule().
                            TasksRepository.create(t, dueDate = dueDate?.toString()).onSuccess { created ->
                                startTime?.let { time ->
                                    TasksRepository.reschedule(created.id, time.hour * 60 + time.minute, durationMin)
                                }
                            }
                            saving = false
                            onCreated()
                        }
                    }
                },
                enabled = title.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
            ) { Text(if (saving) "Adding…" else "Add task") }
        }
    }
}
