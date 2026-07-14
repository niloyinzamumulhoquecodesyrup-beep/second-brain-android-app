package com.secondbrain.lock.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.secondbrain.lock.LockApp
import com.secondbrain.lock.MainActivity
import com.secondbrain.lock.R
import com.secondbrain.lock.data.AppLimit
import com.secondbrain.lock.data.AppLimitRepository
import com.secondbrain.lock.data.UsageStatsHelper
import com.secondbrain.lock.util.Permissions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitorService : LifecycleService() {

    private lateinit var overlayManager: LockOverlayManager
    private lateinit var repository: AppLimitRepository
    private var pollJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        overlayManager = LockOverlayManager(applicationContext)
        repository = AppLimitRepository(applicationContext)
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (pollJob?.isActive != true) {
            pollJob = lifecycleScope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        var lookbackStart = System.currentTimeMillis() - POLL_INTERVAL_MS
        while (isActive) {
            if (Permissions.hasUsageAccess(applicationContext)) {
                runCatching { tick(lookbackStart) }
            }
            lookbackStart = System.currentTimeMillis() - LOOKBACK_WINDOW_MS
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun tick(sinceMillis: Long) {
        val foreground = UsageStatsHelper.currentForegroundPackage(applicationContext, sinceMillis)

        if (overlayManager.isShowing && overlayManager.lockedPackage != foreground) {
            overlayManager.hide()
        }

        if (foreground == null) return
        val limit = repository.getLimit(foreground) ?: return
        if (!limit.enabled) return

        val usedMillis = repository.todaysUsageMillis(foreground)
        val limitMillis = limit.dailyLimitMinutes * 60_000L

        if (usedMillis >= limitMillis) {
            overlayManager.show(foreground, limit.appName)
            return
        }

        if (overlayManager.lockedPackage == foreground) {
            overlayManager.hide()
        }

        maybeWarn(limit, usedMillis, limitMillis)
    }

    private suspend fun maybeWarn(limit: AppLimit, usedMillis: Long, limitMillis: Long) {
        val today = UsageStatsHelper.todayEpochDay()
        if (limit.lastWarnedEpochDay == today) return
        if (usedMillis < (limitMillis * 0.9).toLong()) return

        repository.markWarned(limit)
        val remainingMinutes = ((limitMillis - usedMillis) / 60_000L).coerceAtLeast(0)
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(applicationContext, LockApp.WARNING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${limit.appName}: almost at today's limit")
            .setContentText("About $remainingMinutes min left before it locks for the day.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(limit.packageName.hashCode(), notification)
    }

    private fun buildForegroundNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, LockApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Second Brain Lock is active")
            .setContentText("Watching your daily app limits.")
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        overlayManager.hide()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 1500L
        private const val LOOKBACK_WINDOW_MS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            context.startForegroundService(intent)
        }
    }
}
