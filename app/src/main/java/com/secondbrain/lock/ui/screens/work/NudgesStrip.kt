package com.secondbrain.lock.ui.screens.work

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.RemindersRepository
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SecondBrainTypography
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

    SbCard(topBorderColor = Gold500) {
        Row {
            Text("🔔", style = SecondBrainTypography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isSuggestion) "Worth locking in?" else "When you're ready",
                    color = Gold500,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(current.message, color = Mist100, style = SecondBrainTypography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Row {
                    if (isSuggestion) {
                        Button(
                            onClick = { scope.launch { RemindersRepository.act(current.id, "accept") } },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Ink950)
                        ) { Text("Yes, add it") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { scope.launch { RemindersRepository.act(current.id, "done") } },
                            colors = ButtonDefaults.buttonColors(containerColor = Ink950, contentColor = Mist300)
                        ) { Text("Not now") }
                    } else {
                        Button(
                            onClick = { scope.launch { RemindersRepository.act(current.id, "done") } },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Ink950)
                        ) { Text("✓ Done") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { scope.launch { RemindersRepository.act(current.id, "snooze", 10) } },
                            colors = ButtonDefaults.buttonColors(containerColor = Ink950, contentColor = Gold500)
                        ) { Text("Snooze 10m") }
                        if (current.kind == "task" && current.taskId != null) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onOpenTask(current.taskId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Ink950, contentColor = Mist300)
                            ) { Text("Open") }
                        }
                        current.learnMoreUrl?.let { url ->
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                                colors = ButtonDefaults.buttonColors(containerColor = Ink950, contentColor = Mist300)
                            ) { Text("Learn more →") }
                        }
                    }
                }
                if (moreWaiting > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("+$moreWaiting more waiting", color = Mist400, style = SecondBrainTypography.bodySmall)
                }
            }
            IconButton(onClick = { dismissed.add(current.id) }) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Mist400)
            }
        }
    }
}
