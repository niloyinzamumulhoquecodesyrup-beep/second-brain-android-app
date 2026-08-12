package com.secondbrain.lock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.secondbrain.lock.data.AppDatabase
import com.secondbrain.lock.service.ReminderNotifier
import com.secondbrain.lock.service.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when [ReminderScheduler]'s per-task alarm goes off — both for the original schedule and
 * for a snooze re-fire ([ReminderScheduler.scheduleOneOff] targets this same receiver). Everything
 * it needs (title, ids) travels in the Intent extras, so — unlike an action receiver that has to
 * mutate task state — this never touches [com.secondbrain.lock.data.repo.TasksRepository] and
 * doesn't need to hydrate it from a cold process first.
 *
 * P4: it DOES now need [snoozeCount][com.secondbrain.lock.data.ReminderState.snoozeCount] from
 * Room to drive [ReminderNotifier]'s escalation ladder — a snooze re-fire must show the count
 * [ReminderActionReceiver] already incremented, not always start over at 0 — so this needs
 * `goAsync()` the same way [ReminderActionReceiver] does. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return@launch
                val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID)
                val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Task reminder"

                val snoozeCount = AppDatabase.get(context).reminderStateDao().get(reminderId)?.snoozeCount ?: 0
                ReminderNotifier.show(
                    context = context,
                    reminderId = reminderId,
                    taskId = taskId,
                    title = title,
                    body = "Scheduled for right now.",
                    snoozeCount = snoozeCount
                )
            } finally {
                pending.finish()
            }
        }
    }
}
