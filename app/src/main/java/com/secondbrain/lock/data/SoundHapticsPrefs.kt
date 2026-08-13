package com.secondbrain.lock.data

import android.content.Context

/** User toggles for tap/gesture feedback (e.g. the PARA card-stack's spin ticks). Both default on. */
object SoundHapticsPrefs {
    private const val FILE_NAME = "sound_haptics_prefs"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    private const val KEY_SOUNDS_ENABLED = "sounds_enabled"
    private const val KEY_FOCUS_AUDIO_PRESET = "focus_audio_preset"

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

    /** null = off (P19's focus-session background noise). Persists across sessions so the
     * speaker toggle remembers the last preset picked, in either the focus screen or Settings. */
    fun getFocusAudioPreset(context: Context): FocusAudioPreset? =
        prefs(context).getString(KEY_FOCUS_AUDIO_PRESET, null)?.let {
            runCatching { FocusAudioPreset.valueOf(it) }.getOrNull()
        }

    fun setFocusAudioPreset(context: Context, preset: FocusAudioPreset?) {
        prefs(context).edit().putString(KEY_FOCUS_AUDIO_PRESET, preset?.name).apply()
    }
}
