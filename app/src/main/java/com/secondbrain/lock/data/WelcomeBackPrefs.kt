package com.secondbrain.lock.data

import android.content.Context

/** Local-only "when was this app last opened" tracking for [com.secondbrain.lock.ui.screens.work.WelcomeBackSheet].
 * Not sensitive — plain prefs, same as SleepPrefs' snooze/skip epoch-day tracking. */
object WelcomeBackPrefs {
    private const val FILE_NAME = "welcome_back_prefs"
    private const val KEY_LAST_OPENED_EPOCH_DAY = "last_opened_epoch_day"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Null the very first time the app is ever opened — that's onboarding's job, not a "welcome
     * back" lapse, so callers should treat a null result as "never show the sheet" rather than as
     * an infinite gap. */
    fun getLastOpenedEpochDay(context: Context): Long? {
        val value = prefs(context).getLong(KEY_LAST_OPENED_EPOCH_DAY, -1L)
        return if (value < 0) null else value
    }

    fun setLastOpenedEpochDay(context: Context, epochDay: Long) {
        prefs(context).edit().putLong(KEY_LAST_OPENED_EPOCH_DAY, epochDay).apply()
    }
}
