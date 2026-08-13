package com.secondbrain.lock.data

import android.content.Context

/** Local-only storage for P20's welcome-flow answers that POST /api/onboarding/complete doesn't
 * accept yet (backend work is P23, out of scope here — the request body only takes
 * displayName/age/persona today). Read by screens that personalize based on these answers. */
object OnboardingPrefs {
    private const val FILE_NAME = "onboarding_prefs"
    private const val KEY_PAIN_POINTS = "pain_points"
    private const val KEY_TEMPLATE = "template"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getPainPoints(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PAIN_POINTS, emptySet()).orEmpty()

    fun setPainPoints(context: Context, points: Set<String>) {
        prefs(context).edit().putStringSet(KEY_PAIN_POINTS, points).apply()
    }

    fun getTemplate(context: Context): String? = prefs(context).getString(KEY_TEMPLATE, null)

    fun setTemplate(context: Context, template: String?) {
        prefs(context).edit().putString(KEY_TEMPLATE, template).apply()
    }

    // Pain-point keys — screen 2's answers, each meant to change something concrete elsewhere in
    // the app (P20). Only PAIN_CANT_START is actually wired to a behavior change so far (see
    // FocusPomodoro's READY screen); the rest are stored for now but not yet consumed anywhere —
    // wiring every one of them touches several unrelated screens (notifications, TimeBar/
    // Sectograph defaults, Sort Pass prompts, Library copy) and was scoped down given the size of
    // this prompt pack. Flagged here rather than silently claimed as fully wired.
    const val PAIN_FORGETS = "forgets"
    const val PAIN_CANT_START = "cant_start"
    const val PAIN_LOSES_TIME = "loses_time"
    const val PAIN_NEVER_FINISHES = "never_finishes"
}
