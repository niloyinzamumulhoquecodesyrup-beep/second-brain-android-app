package com.secondbrain.lock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.secondbrain.lock.service.ReminderNotifier
import com.secondbrain.lock.service.ReminderScheduler

/** Fires when [ReminderScheduler]'s per-task alarm goes off. Everything it needs (title, ids)
 * travels in the Intent extras, so — unlike an action receiver that has to mutate task state —
 * this never touches [com.secondbrain.lock.data.repo.TasksRepository] and doesn't need to hydrate
 * it from a cold process first. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Task reminder"
        ReminderNotifier.show(
            context = context,
            reminderId = reminderId,
            taskId = taskId,
            title = title,
            body = "Scheduled for right now.",
            snoozeCount = 0
        )
    }
}
