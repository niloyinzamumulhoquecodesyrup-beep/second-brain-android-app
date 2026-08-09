package com.secondbrain.lock.data

import android.content.Context

/** Once-per-day gate for [com.secondbrain.lock.ui.screens.work.MorningBriefSection] — same shape
 * as [SleepPrefs]'s snooze/skip flags. Keyed off the device's local epoch day, not the server's
 * UTC "today", which is fine since the section is only reachable inside [SleepPrefs]'s 60-minute
 * post-wake window anyway. */
object MorningBriefPrefs {
    private const val FILE_NAME = "morning_brief_prefs"
    private const val KEY_LAST_PLAYED_EPOCH_DAY = "last_played_epoch_day"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun hasPlayedToday(context: Context): Boolean =
        prefs(context).getLong(KEY_LAST_PLAYED_EPOCH_DAY, -1) == UsageStatsHelper.todayEpochDay()

    fun markPlayedToday(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_PLAYED_EPOCH_DAY, UsageStatsHelper.todayEpochDay()).apply()
    }
}
