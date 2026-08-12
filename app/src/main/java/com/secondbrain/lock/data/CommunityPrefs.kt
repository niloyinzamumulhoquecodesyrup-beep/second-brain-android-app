package com.secondbrain.lock.data

import android.content.Context

/** Whether the Mindverse tab (cross-account live chat/video, no moderation/reporting/blocking
 * yet) is reachable at all — defaults OFF. Not sensitive — plain prefs, same as SleepPrefs. */
object CommunityPrefs {
    private const val FILE_NAME = "community_prefs"
    private const val KEY_ENABLED = "community_enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
