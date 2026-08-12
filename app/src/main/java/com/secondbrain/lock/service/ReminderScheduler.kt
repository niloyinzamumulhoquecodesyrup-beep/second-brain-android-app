package com.secondbrain.lock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.dto.Task
import com.secondbrain.lock.receiver.ReminderAlarmReceiver
import com.secondbrain.lock.util.Permissions
import java.time.LocalDate
import java.util.Calendar

/**
 * Local-alarm tier for task-start reminders. Reminders are NOT alarm clocks — unlike
 * [AlarmScheduler]'s wake alarm, these use [AlarmManager.setExactAndAllowWhileIdle] (or the
 * inexact fallback below) rather than [AlarmManager.setAlarmClock], so 15 task reminders a day
 * don't each show up in the system's status-bar alarm icon and alarm list.
 *
 * [Permissions.hasExactAlarm] is checked live, at scheduling time, for every single reminder —
 * never cached — because it's user-revocable at any moment:
 *   - granted -> [AlarmManager.setExactAndAllowWhileIdle], fires on the minute.
 *   - denied  -> [AlarmManager.setAndAllowWhileIdle], fires within a window instead of exactly.
 *     This is a deliberate graceful degradation, not a failure: combined with the FCM backstop
 *     (P5) it keeps reminders working without the permission, just less precisely.
 *
 * Holds its own [appContext] (init'd from [com.secondbrain.lock.LockApp], same pattern as
 * [SyncQueue]) rather than taking a Context parameter on every call — [rescheduleAll] is called
 * from [TasksRepository], a plain singleton `object` with no Android Context of its own, and
 * threading one through the whole repository just for this would break that pattern everywhere
 * else in it.
 */
object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    private const val PREFS_NAME = "reminder_scheduler"
    private const val KEY_SCHEDULED_IDS = "scheduled_task_ids"

    /** Hard cap — there is no value in scheduling something days out that a later refresh will
     * re-derive anyway, and AlarmManager has practical per-app limits on pending alarms. */
    private const val MAX_SCHEDULED = 20

    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TITLE = "title"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Cancels every reminder this object scheduled last time (tracked in prefs, since
     * AlarmManager has no "list pending alarms" API to reconstruct that from), then schedules one
     * for each of today's next [MAX_SCHEDULED] not-done, timed tasks. */
    fun rescheduleAll() {
        val am = appContext.getSystemService(AlarmManager::class.java) ?: return

        previouslyScheduledIds().forEach { id ->
            runCatching { am.cancel(reminderPendingIntent(id)) }
        }

        val today = LocalDate.now().toString()
        val upcoming = TasksRepository.tasks.value
            .asSequence()
            .filter { !it.done && it.startMin != null && it.dueDate?.take(10) == today }
            .sortedBy { it.startMin }
            .take(MAX_SCHEDULED)
            .toList()

        upcoming.forEach { task -> scheduleOne(am, task) }
        saveScheduledIds(upcoming.map { it.id }.toSet())
    }

    private fun scheduleOne(am: AlarmManager, task: Task) {
        val startMin = task.startMin ?: return
        val triggerAt = todayAtMinuteMillis(startMin)
        if (triggerAt <= System.currentTimeMillis()) return

        val pendingIntent = reminderPendingIntent(task.id, task.title)
        if (Permissions.hasExactAlarm(appContext)) {
            runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent) }
                .onFailure { Log.e(TAG, "Couldn't schedule exact reminder for ${task.id}", it) }
        } else {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent) }
                .onFailure { Log.e(TAG, "Couldn't schedule inexact reminder for ${task.id}", it) }
        }
    }

    /** Extras are irrelevant to [PendingIntent] cancel-matching (only component + request code
     * matter), so a cancel-only call can omit [title]. */
    private fun reminderPendingIntent(id: String, title: String? = null): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, id)
            putExtra(EXTRA_TASK_ID, id)
            if (title != null) putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            appContext, id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun todayAtMinuteMillis(minuteOfDay: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun prefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun previouslyScheduledIds(): Set<String> =
        prefs().getStringSet(KEY_SCHEDULED_IDS, emptySet()).orEmpty()

    private fun saveScheduledIds(ids: Set<String>) {
        prefs().edit().putStringSet(KEY_SCHEDULED_IDS, ids).apply()
    }
}
