package com.secondbrain.lock.data

import android.content.Context

/** User toggles for tap/gesture feedback (e.g. the PARA card-stack's spin ticks). Both default on. */
object SoundHapticsPrefs {
    private const val FILE_NAME = "sound_haptics_prefs"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    private const val KEY_SOUNDS_ENABLED = "sounds_enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isHapticsEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_HAPTICS_ENABLED, true)

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
    }

    fun isSoundsEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_SOUNDS_ENABLED, true)

    fun setSoundsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOUNDS_ENABLED, enabled).apply()
    }
}
