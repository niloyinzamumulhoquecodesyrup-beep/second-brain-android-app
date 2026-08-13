package com.secondbrain.lock.data

import android.content.Context
import java.time.LocalDate

/** Local-only counter behind [com.secondbrain.lock.ui.screens.work.ThreeThingsRing] (P22) — fills
 * on ANY completion (task, routine, focus session), resets at local midnight. Deliberately not
 * server-synced: this is a same-day, same-device "did I do three things today" signal, not a
 * stat worth persisting or reconciling across devices. */
object ThreeThingsPrefs {
    private const val FILE_NAME = "three_things_prefs"
    private const val KEY_COUNT = "count"
    private const val KEY_EPOCH_DAY = "epoch_day"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Capped at 3 — the cap is the whole point (P22: not configurable, ever). */
    fun getCompletedToday(context: Context): Int {
        val p = prefs(context)
        val storedDay = p.getLong(KEY_EPOCH_DAY, -1)
        val today = LocalDate.now().toEpochDay()
        if (storedDay != today) return 0
        return p.getInt(KEY_COUNT, 0).coerceIn(0, 3)
    }

    /** Returns the new count so the caller can tell whether this completion was the 3rd
     * (triggers the pulse celebration) without a redundant second read. */
    fun increment(context: Context): Int {
        val today = LocalDate.now().toEpochDay()
        val current = getCompletedToday(context)
        val next = (current + 1).coerceAtMost(3)
        prefs(context).edit().putLong(KEY_EPOCH_DAY, today).putInt(KEY_COUNT, next).apply()
        return next
    }
}
