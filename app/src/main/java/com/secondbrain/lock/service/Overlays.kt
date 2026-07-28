package com.secondbrain.lock.service

import android.content.Context

/**
 * App-wide singleton overlay instances. Both [MonitorService]'s polling fallback and
 * [LockAccessibilityService]'s instant events need to drive the *same* lock/cooldown/focus
 * overlays, so these live here instead of being owned by whichever component happened to
 * detect the foreground change first.
 */
object Overlays {
    lateinit var lock: LockOverlayManager
        private set
    lateinit var cooldown: CooldownOverlayManager
        private set
    lateinit var focus: FocusOverlayManager
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        lock = LockOverlayManager(appContext)
        cooldown = CooldownOverlayManager(appContext)
        focus = FocusOverlayManager(appContext)
        initialized = true
    }
}

/** Whether a cross-device focus/Pomodoro session is currently active, per the last poll. */
object FocusSessionState {
    @Volatile var active: Boolean = false
}
