package com.secondbrain.lock.data

import android.content.Context

/** Which planner routine categories ("sleep", "work", "study", ...) trigger schedule auto-block. */
object SchedulePrefs {
    private const val FILE_NAME = "schedule_prefs"
    private const val KEY_AUTO_BLOCK_CATEGORIES = "auto_block_categories"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getAutoBlockCategories(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_AUTO_BLOCK_CATEGORIES, emptySet()) ?: emptySet()

    fun setAutoBlockCategories(context: Context, categories: Set<String>) {
        prefs(context).edit().putStringSet(KEY_AUTO_BLOCK_CATEGORIES, categories).apply()
    }
}
