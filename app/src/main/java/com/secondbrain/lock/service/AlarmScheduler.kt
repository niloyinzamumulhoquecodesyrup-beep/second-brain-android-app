package com.secondbrain.lock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.secondbrain.lock.data.RoutineRepository
import com.secondbrain.lock.data.SleepPrefs
import java.util.Calendar

/**
 * Schedules the sleep-window wake alarm via [AlarmManager.setAlarmClock] — the one AlarmManager
 * API that reliably survives Doze without needing the SCHEDULE_EXACT_ALARM permission, since
 * it's meant exactly for user-visible alarms (shows the status-bar alarm-clock icon, same as
 * the stock Clock app).
 */
object AlarmScheduler {

    suspend fun reschedule(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = alarmPendingIntent(appContext)

        if (!SleepPrefs.isEnabled(appContext)) {
            am.cancel(pendingIntent)
            return
        }

        // WakeAlarmReceiver calls this on every firing to line up tomorrow's alarm, but that
        // races the user's in-flight "Snooze 10 min"/"for later" tap, which schedules its own
        // one-off trigger on this exact same AlarmManager slot — whichever call lands last wins.
        // If a one-off alarm is still ahead of us, it hasn't fired yet, so back off instead of
        // stomping it with tomorrow's (much later) trigger.
        if (SleepPrefs.oneOffAlarmAt(appContext) > System.currentTimeMillis()) return

        val wakeMinute = resolveWakeMinuteOfDay(appContext)
        val triggerAt = nextOccurrenceMillis(wakeMinute)
        val showIntent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, com.secondbrain.lock.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pendingIntent)
    }

    /** One-off snooze — doesn't touch the recurring daily schedule. */
    fun scheduleSnooze(context: Context, minutesFromNow: Int) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + minutesFromNow * 60_000L
        SleepPrefs.setOneOffAlarmAt(appContext, triggerAt)
        val showIntent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, com.secondbrain.lock.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), alarmPendingIntent(appContext))
    }

    /** One-off "wake me at this time instead" from the wake flow's own time picker — also
     * doesn't touch the recurring daily schedule, same as [scheduleSnooze]. */
    fun scheduleAt(context: Context, hourOfDay: Int, minute: Int) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextOccurrenceMillis(hourOfDay * 60 + minute)
        SleepPrefs.setOneOffAlarmAt(appContext, triggerAt)
        val showIntent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, com.secondbrain.lock.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), alarmPendingIntent(appContext))
    }

    private fun alarmPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE,
            Intent(context, WakeAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun nextOccurrenceMillis(minuteOfDay: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    private suspend fun resolveWakeMinuteOfDay(context: Context): Int {
        if (!SleepPrefs.getUseRoutineSleepWindow(context)) return SleepPrefs.getWakeMinuteOfDay(context)
        val sleepRoutine = RoutineRepository(context).getCached().firstOrNull { it.category == "sleep" }
        return if (sleepRoutine != null) {
            (sleepRoutine.startMin + sleepRoutine.durationMin) % 1440
        } else {
            SleepPrefs.getWakeMinuteOfDay(context)
        }
    }

    private const val ALARM_REQUEST_CODE = 4200
}
