package com.secondbrain.lock.ui.screens.work

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.RemindersRepository
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.launch

/**
 * Ports NudgesStrip.js exactly: one due reminder at a time above the task list, same
 * "Worth locking in?"/"When you're ready" eyebrow copy, same action verbs — declining a
 * routine suggestion really does call action=done (not dismiss), matching the web's code
 * literally even though that reads a little oddly. A task reminder's "Open" (lib/reminders.js's
 * reminderOpenTarget) calls [onOpenTask] with its task_id so WorkScreen can scroll/glow the
 * matching TasksPanel row; routine/block reminders' "Open" (which only ever points back at this
 * same /work screen on web) stays a no-op. interest_event's external "Learn more" link opens
 * the browser.
 *
 * Visually matches the Tasks Section redesign: the same tinted [StreakSurface] card as the
 * streak summary (no top accent stripe), a StreakAccent-red eyebrow label, a bell icon in a
 * tinted circle chip (same treatment as a TimelineRow's icon), and StreakAccent as the one
 * primary-action color instead of the old Emerald/Gold mix. The action row scrolls horizontally
 * since a task reminder can have up to 4 buttons (Done/Snooze/Open/Learn more) that don't all
 * fit — letting one get squeezed to near-zero width instead wraps its label vertically and
 * blows out the whole row's height.
 */
@Composable
fun NudgesStrip(onOpenTask: (String) -> Unit = {}) {
    val reminders by RemindersRepository.reminders.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dismissed = remember { mutableStateListOf<String>() }
    val due = reminders.filter { it.due }
    val current = due.firstOrNull { it.id !in dismissed } ?: return
    val moreWaiting = due.size - 1
    val isSuggestion = current.kind == "routine_suggestion"

    StreakSurface {
        // The dismiss "X" overlays the top-right corner instead of sitting in the main Row —
        // giving it its own row slot squeezed the button row's available width down to almost
        // exactly what "Done"/"Snooze" need, so the "Snooze" pill's rounded right edge was
        // getting clipped by the column boundary.
        Box {
            Row(modifier = Modifier.padding(end = 32.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(StreakAccent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) { Text("🔔", fontSize = 16.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    SbSectionTitle(
                        if (isSuggestion) "Worth locking in?" else "When you're ready",
                        color = StreakAccent,
                        uppercase = false
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(current.message, color = Mist100, style = SecondBrainTypography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        if (isSuggestion) {
                            Button(
                                onClick = { scope.launch { RemindersRepository.act(current.id, "accept") } },
                                colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
                            ) { Text("Yes, add it") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { scope.launch { RemindersRepository.act(current.id, "done") } },
                                colors = ButtonDefaults.buttonColors(containerColor = Ink800, contentColor = Mist300)
                            ) { Text("Not now") }
                        } else {
                            Button(
                                onClick = { scope.launch { RemindersRepository.act(current.id, "done") } },
                                colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
                            ) { Text("✓ Done") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { scope.launch { RemindersRepository.act(current.id, "snooze", 10) } },
                                colors = ButtonDefaults.buttonColors(containerColor = Ink800, contentColor = Mist300)
                            ) { Text("Snooze 10m") }
                            if (current.kind == "task" && current.taskId != null) {
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { onOpenTask(current.taskId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Ink800, contentColor = Mist300)
                                ) { Text("Open") }
                            }
                            current.learnMoreUrl?.let { url ->
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Ink800, contentColor = Mist300)
                                ) { Text("Learn more →") }
                            }
                        }
                    }
                    if (moreWaiting > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("+$moreWaiting more waiting", color = Mist400, style = SecondBrainTypography.bodySmall)
                    }
                }
            }
            IconButton(
                onClick = { dismissed.add(current.id) },
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Mist400)
            }
        }
    }
}
