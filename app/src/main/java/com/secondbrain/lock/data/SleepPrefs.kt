package com.secondbrain.lock.data

import android.content.Context

/** Local settings for the sleep-window wake alarm + wellbeing loop. Not sensitive — plain prefs. */
object SleepPrefs {
    private const val FILE_NAME = "sleep_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WAKE_MINUTE_OF_DAY = "wake_minute_of_day"
    private const val KEY_USE_ROUTINE = "use_routine_sleep_window"
    private const val KEY_LAST_SNOOZE_EPOCH_DAY = "last_snooze_epoch_day"
    private const val KEY_LAST_SKIP_EPOCH_DAY = "last_skip_epoch_day"
    private const val KEY_JOURNAL_REMINDER_PENDING = "journal_reminder_pending"
    private const val KEY_MORNING_ROUTINE_ENDS_AT = "morning_routine_ends_at"
    private const val KEY_ONE_OFF_ALARM_AT = "one_off_alarm_at"
    private const val KEY_ALARM_SCHEDULING_FAILED = "alarm_scheduling_failed"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Minutes since midnight (0-1439) the alarm fires at, when not deriving from a planner routine. */
    fun getWakeMinuteOfDay(context: Context): Int = prefs(context).getInt(KEY_WAKE_MINUTE_OF_DAY, 7 * 60)

    fun setWakeMinuteOfDay(context: Context, minuteOfDay: Int) {
        prefs(context).edit().putInt(KEY_WAKE_MINUTE_OF_DAY, minuteOfDay).apply()
    }

    fun getUseRoutineSleepWindow(context: Context): Boolean = prefs(context).getBoolean(KEY_USE_ROUTINE, false)

    fun setUseRoutineSleepWindow(context: Context, use: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_ROUTINE, use).apply()
    }

    /** The whole flow (ring -> wellbeing block) is snoozable/skippable once per day. */
    fun canSnoozeToday(context: Context): Boolean =
        prefs(context).getLong(KEY_LAST_SNOOZE_EPOCH_DAY, -1) != UsageStatsHelper.todayEpochDay()

    fun consumeSnooze(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_SNOOZE_EPOCH_DAY, UsageStatsHelper.todayEpochDay()).apply()
    }

    fun canSkipToday(context: Context): Boolean =
        prefs(context).getLong(KEY_LAST_SKIP_EPOCH_DAY, -1) != UsageStatsHelper.todayEpochDay()

    fun consumeSkip(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_SKIP_EPOCH_DAY, UsageStatsHelper.todayEpochDay()).apply()
    }

    /** Set when the user picks "Remind me in the web app" on the post-wake journal nudge. */
    fun isJournalReminderPending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_JOURNAL_REMINDER_PENDING, false)

    fun setJournalReminderPending(context: Context, pending: Boolean) {
        prefs(context).edit().putBoolean(KEY_JOURNAL_REMINDER_PENDING, pending).apply()
    }

    /** Starts the post-wake "block every other app" window (default 1 hour) — read by
     * ForegroundEvaluator on every foreground-app change, same as the schedule/focus blocks. */
    fun startMorningRoutine(context: Context, durationMinutes: Int = 60) {
        prefs(context).edit()
            .putLong(KEY_MORNING_ROUTINE_ENDS_AT, System.currentTimeMillis() + durationMinutes * 60_000L)
            .apply()
    }

    /** Ends the block immediately — "I've done those", "Skip for today", and "Show this to me
     * later" (which re-blocks on the next wake/snooze) all clear it before moving on. */
    fun endMorningRoutine(context: Context) {
        prefs(context).edit().putLong(KEY_MORNING_ROUTINE_ENDS_AT, 0L).apply()
    }

    fun isMorningRoutineActive(context: Context): Boolean =
        prefs(context).getLong(KEY_MORNING_ROUTINE_ENDS_AT, 0L) > System.currentTimeMillis()

    /** Trigger time of a one-off snooze/"for later" alarm, so [AlarmScheduler.reschedule] can
     * tell one is still pending and avoid clobbering it with tomorrow's recurring trigger. */
    fun setOneOffAlarmAt(context: Context, epochMillis: Long) {
        prefs(context).edit().putLong(KEY_ONE_OFF_ALARM_AT, epochMillis).apply()
    }

    fun oneOffAlarmAt(context: Context): Long = prefs(context).getLong(KEY_ONE_OFF_ALARM_AT, 0L)

    /** Set by [com.secondbrain.lock.service.AlarmScheduler] whenever a wake-alarm AlarmManager
     * call actually failed (missing exact-alarm permission, or an OEM SecurityException) — read by
     * SettingsScreen to show a "can't schedule alarms" warning instead of silently doing nothing. */
    fun setAlarmSchedulingFailed(context: Context, failed: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALARM_SCHEDULING_FAILED, failed).apply()
    }

    fun isAlarmSchedulingFailed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALARM_SCHEDULING_FAILED, false)

    fun morningRoutineRemainingMinutes(context: Context): Int {
        val remaining = prefs(context).getLong(KEY_MORNING_ROUTINE_ENDS_AT, 0L) - System.currentTimeMillis()
        return (remaining / 60_000L).coerceAtLeast(0L).toInt()
    }
}
