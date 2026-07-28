package com.secondbrain.lock.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Instant foreground-app detection via window-state-changed events, instead of polling
 * UsageStatsManager every 1.5s. Entirely optional — [MonitorService]'s poller keeps running
 * regardless as a fallback for users who don't grant this. Both paths call the same
 * [ForegroundEvaluator], so granting/revoking this never changes *what* gets blocked, only how
 * fast it's noticed. Reads only which package is in the foreground — never screen content.
 */
class LockAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var evaluator: ForegroundEvaluator

    override fun onServiceConnected() {
        super.onServiceConnected()
        evaluator = ForegroundEvaluator(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val eventPackage = event.packageName?.toString() ?: return
        if (eventPackage == packageName) return // ignore our own overlay/activity windows
        if (!::evaluator.isInitialized) return
        scope.launch { evaluator.onForegroundPackage(eventPackage) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }
}
