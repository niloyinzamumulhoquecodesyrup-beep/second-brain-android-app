package com.secondbrain.lock.data

import android.content.Context

/** Tracks how many times [com.secondbrain.lock.ui.nav.FastCaptureSheet] has been opened, purely
 * to show the "hold the button to speak" discoverability hint (P6a step 6, completed here in P12
 * once long-press-to-speak actually does something) for the first two openings only, then never
 * again. Not sensitive — plain prefs. */
object CaptureHintPrefs {
    private const val FILE_NAME = "capture_hint_prefs"
    private const val KEY_OPEN_COUNT = "open_count"
    private const val HINT_MAX_SHOWS = 2

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Call once per sheet open. Returns whether the voice hint should show THIS time. */
    fun recordOpenAndShouldShowVoiceHint(context: Context): Boolean {
        val p = prefs(context)
        val count = p.getInt(KEY_OPEN_COUNT, 0)
        p.edit().putInt(KEY_OPEN_COUNT, count + 1).apply()
        return count < HINT_MAX_SHOWS
    }
}
