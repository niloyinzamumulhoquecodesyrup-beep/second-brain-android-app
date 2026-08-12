package com.secondbrain.lock.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.secondbrain.lock.LockApp
import com.secondbrain.lock.data.AppDatabase
import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.data.ReminderState
import com.secondbrain.lock.data.repo.RemindersRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.service.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the Done / Snooze / Too big action buttons on a task-reminder notification.
 *
 * THE CRITICAL CONSTRAINT: [TasksRepository] is a Kotlin `object` hydrated from disk in
 * [com.secondbrain.lock.LockApp.onCreate] via `runBlocking`. A broadcast fired at this receiver
 * from a cold process (app force-stopped, notification survives via AlarmManager/system) gets a
 * completely empty [TasksRepository] unless it's re-hydrated first — calling
 * [TasksRepository.setDone] against that empty state would silently no-op. [LocalCache.init] and
 * [TasksRepository.restore] below are exactly that re-hydration, done from disk (never network) so
 * it works with zero connectivity too.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LocalCache.init(context) // idempotent
                TasksRepository.restore() // hydrate from DISK, not network

                val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return@launch
                val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: reminderId
                val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Task reminder"

                when (intent.action) {
                    ACTION_DONE -> handleDone(context, reminderId, taskId)
                    ACTION_SNOOZE -> handleSnooze(context, reminderId, taskId, title)
                    ACTION_BREAK -> handleBreak(context, reminderId, taskId, title)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** [TasksRepository.setDone] already routes through its shared `updateTask` helper, which
     * falls back to [com.secondbrain.lock.data.SyncQueue] on a connectivity failure — so this
     * works offline for free. Adding a second offline path here would just duplicate one that
     * already works and risk them drifting apart. */
    private suspend fun handleDone(context: Context, reminderId: String, taskId: String) {
        TasksRepository.setDone(taskId, true)
        cancelNotification(context, reminderId)
    }

    private suspend fun handleSnooze(context: Context, reminderId: String, taskId: String, title: String) {
        // Most reminders here are derived purely client-side from a task's start time (P2) and
        // have no corresponding backend Reminder row yet — that's P23's "auto-create reminder rows
        // for scheduled tasks" backend work. Until it ships, this PATCH commonly 404s; that's
        // expected, not a bug, so it's best-effort and never blocks the local snooze below.
        runCatching { RemindersRepository.act(reminderId, "snooze", SNOOZE_MINUTES) }

        val dao = AppDatabase.get(context).reminderStateDao()
        val nextCount = (dao.get(reminderId)?.snoozeCount ?: 0) + 1
        dao.upsert(ReminderState(reminderId, nextCount, System.currentTimeMillis()))

        cancelNotification(context, reminderId)
        ReminderScheduler.scheduleOneOff(reminderId, title, minutesFromNow = SNOOZE_MINUTES)
    }

    private suspend fun handleBreak(context: Context, reminderId: String, taskId: String, title: String) {
        val result = TasksRepository.breakdown(title)
        val subtasks = result.getOrNull()?.subtasks.orEmpty()
        if (subtasks.isEmpty()) {
            postFallback(context, reminderId, taskId, title)
            return
        }

        val style = NotificationCompat.InboxStyle()
        subtasks.forEach { style.addLine("${it.topic} · ${it.estimatedMinutes} min") }
        val notification = NotificationCompat.Builder(context, LockApp.TASK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("${subtasks.size} steps")
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(reminderId.hashCode(), notification)

        // "Schedule the first subtask" — a short, real follow-up reminder for just the first step,
        // not the whole list, so the next thing the user sees is one small actionable prompt.
        subtasks.first().let {
            ReminderScheduler.scheduleOneOff(reminderId, "${title} — ${it.topic}", minutesFromNow = 1)
        }
    }

    private suspend fun postFallback(context: Context, reminderId: String, taskId: String, title: String) {
        com.secondbrain.lock.service.ReminderNotifier.show(
            context = context,
            reminderId = reminderId,
            taskId = taskId,
            title = title,
            body = "Break it down in the app",
            snoozeCount = 0
        )
    }

    private fun cancelNotification(context: Context, reminderId: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel(reminderId.hashCode())
    }

    companion object {
        const val ACTION_DONE = "com.secondbrain.lock.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.secondbrain.lock.action.REMINDER_SNOOZE"
        const val ACTION_BREAK = "com.secondbrain.lock.action.REMINDER_BREAK"
        private const val SNOOZE_MINUTES = 10
    }
}
