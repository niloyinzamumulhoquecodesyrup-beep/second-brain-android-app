package com.secondbrain.lock.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.secondbrain.lock.data.RoutineRepository
import com.secondbrain.lock.data.SleepPrefs
import com.secondbrain.lock.util.Permissions
import java.util.Calendar

/**
 * Schedules the sleep-window wake alarm via [AlarmManager.setAlarmClock] — the one AlarmManager
 * API that's supposed to survive Doze without needing the SCHEDULE_EXACT_ALARM permission, since
 * it's meant exactly for user-visible alarms (shows the status-bar alarm-clock icon, same as
 * the stock Clock app). In practice this isn't reliable: on API 31+ SCHEDULE_EXACT_ALARM is
 * user-toggleable special app access, revocable at any time, and at least one OEM (Oplus/ColorOS's
 * AlarmManagerService) enforces the check even for setAlarmClock with a SecurityException that
 * isn't documented anywhere in stock Android. [scheduleWakeAlarm] below short-circuits when
 * [Permissions.hasExactAlarm] is false and records the outcome via
 * [SleepPrefs.setAlarmSchedulingFailed] either way, so SettingsScreen can surface a fix-it row
 * instead of the alarm just silently never firing.
 */
object AlarmScheduler {

    suspend fun reschedule(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = alarmPendingIntent(appContext)

        if (!SleepPrefs.isEnabled(appContext)) {
            runCatching { am.cancel(pendingIntent) }
                .onFailure { Log.e("AlarmScheduler", "Couldn't cancel wake alarm", it) }
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
        scheduleWakeAlarm(appContext, am, triggerAt, showIntent, pendingIntent)
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
        scheduleWakeAlarm(appContext, am, triggerAt, showIntent, alarmPendingIntent(appContext))
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
        scheduleWakeAlarm(appContext, am, triggerAt, showIntent, alarmPendingIntent(appContext))
    }

    /** Shared by all three scheduling call sites: short-circuits before ever touching AlarmManager
     * if the exact-alarm permission is (now) missing — [Permissions.hasExactAlarm] is revocable at
     * any moment, so this is checked live rather than cached — and otherwise records whether the
     * call actually succeeded, instead of the previous bare `runCatching` that discarded failures
     * (including the ColorOS SecurityException noted above) with no trace anywhere. */
    private fun scheduleWakeAlarm(
        context: Context,
        am: AlarmManager,
        triggerAt: Long,
        showIntent: PendingIntent,
        pendingIntent: PendingIntent
    ) {
        if (!Permissions.hasExactAlarm(context)) {
            SleepPrefs.setAlarmSchedulingFailed(context, true)
            return
        }
        runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pendingIntent) }
            .onSuccess { SleepPrefs.setAlarmSchedulingFailed(context, false) }
            .onFailure { e ->
                Log.e("AlarmScheduler", "Couldn't schedule wake alarm", e)
                SleepPrefs.setAlarmSchedulingFailed(context, true)
            }
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
