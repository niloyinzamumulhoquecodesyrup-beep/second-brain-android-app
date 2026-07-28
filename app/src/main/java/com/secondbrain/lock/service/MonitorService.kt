package com.secondbrain.lock.service

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.secondbrain.lock.LockApp
import com.secondbrain.lock.MainActivity
import com.secondbrain.lock.data.RoutineRepository
import com.secondbrain.lock.data.UsageStatsHelper
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.util.Permissions
import com.secondbrain.lock.widget.SectographUpdateWorker
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the app-limit enforcement and cross-device focus sync alive.
 * Foreground-package detection itself is a *fallback* poll here — [LockAccessibilityService],
 * when granted, detects window changes instantly and calls the same [ForegroundEvaluator]
 * directly. Both paths are safe to run at once; the evaluator is idempotent per package.
 */
class MonitorService : LifecycleService() {

    private lateinit var evaluator: ForegroundEvaluator
    private lateinit var routineRepository: RoutineRepository
    private var pollJob: Job? = null
    private var focusPollJob: Job? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            restartFocusPoll()
            lifecycleScope.launch {
            routineRepository.refresh()
            SectographUpdateWorker.requestImmediateUpdate(applicationContext)
        }
        }
    }

    override fun onCreate() {
        super.onCreate()
        evaluator = ForegroundEvaluator(applicationContext)
        routineRepository = RoutineRepository(applicationContext)
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // Also check once at service startup, so an already-active session (e.g. started on the
        // web before the phone was even unlocked) is picked up without waiting for a screen event.
        restartFocusPoll()
        lifecycleScope.launch {
            routineRepository.refresh()
            SectographUpdateWorker.requestImmediateUpdate(applicationContext)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (pollJob?.isActive != true) {
            pollJob = lifecycleScope.launch { pollLoop() }
        }
        return START_STICKY
    }

    /** Restarts the cross-device focus/Pomodoro poll loop from scratch — called on screen-on. */
    private fun restartFocusPoll() {
        focusPollJob?.cancel()
        focusPollJob = lifecycleScope.launch { focusPollLoop() }
    }

    private suspend fun focusPollLoop() {
        while (coroutineContext.isActive) {
            val state = ApiClient.getFocusState().getOrNull()
            val endsAt = state?.endsAtMillis
            if (state == null || !state.active || endsAt == null || endsAt <= System.currentTimeMillis()) {
                FocusSessionState.active = false
                Overlays.focus.hide()
                return
            }
            FocusSessionState.active = true
            // The pill's own 1s tick hides itself if it reaches zero between polls, at which
            // point this device also reports the completion — safe now that session_id makes
            // POST /api/activity/focus idempotent server-side, so it can't double-count a
            // completion the web client (or another device) already logged.
            Overlays.focus.show(endsAt) {
                FocusSessionState.active = false
                val minutes = state.startedAtMillis
                    ?.let { started -> ((endsAt - started) / 60_000L).toInt().coerceAtLeast(1) }
                    ?: 25
                lifecycleScope.launch {
                    ApiClient.postFocusActivity(state.mode ?: "focus", minutes, state.taskId, state.sessionId)
                }
            }
            delay(FOCUS_POLL_INTERVAL_MS)
        }
    }

    private suspend fun pollLoop() {
        var lookbackStart = System.currentTimeMillis() - POLL_INTERVAL_MS
        while (coroutineContext.isActive) {
            if (Permissions.hasUsageAccess(applicationContext)) {
                runCatching {
                    val foreground = UsageStatsHelper.currentForegroundPackage(applicationContext, lookbackStart)
                    evaluator.onForegroundPackage(foreground)
                }
            }
            lookbackStart = System.currentTimeMillis() - LOOKBACK_WINDOW_MS
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, LockApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Slay Task is active")
            .setContentText("Watching your daily app limits.")
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        focusPollJob?.cancel()
        Overlays.lock.hide()
        Overlays.cooldown.hide()
        Overlays.focus.hide()
        runCatching { unregisterReceiver(screenStateReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 1500L
        private const val LOOKBACK_WINDOW_MS = 5_000L
        private const val FOCUS_POLL_INTERVAL_MS = 15_000L

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            context.startForegroundService(intent)
        }
    }
}
