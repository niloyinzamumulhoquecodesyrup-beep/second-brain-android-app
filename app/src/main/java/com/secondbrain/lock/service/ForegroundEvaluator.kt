package com.secondbrain.lock.service

import android.app.NotificationManager
import android.content.Context
import com.secondbrain.lock.LockApp
import com.secondbrain.lock.data.AppLimit
import com.secondbrain.lock.data.AppLimitRepository
import com.secondbrain.lock.data.RoutineRepository
import com.secondbrain.lock.data.SchedulePrefs
import com.secondbrain.lock.data.SleepPrefs
import com.secondbrain.lock.data.UsageStatsHelper
import androidx.core.app.NotificationCompat

/**
 * Single source of truth for "should the app currently in the foreground be locked, and why."
 * Called both by [MonitorService]'s polling fallback and by [LockAccessibilityService]'s
 * instant window-change events, so the lock decision is identical regardless of which detection
 * path noticed the change — and so only one of them needs to actually be running for locking to
 * work correctly.
 */
class ForegroundEvaluator(context: Context) {
    private val appContext = context.applicationContext
    private val limitRepository = AppLimitRepository(appContext)
    private val routineRepository = RoutineRepository(appContext)

    private var lastForeground: String? = null

    suspend fun onForegroundPackage(foreground: String?) {
        if (Overlays.lock.isShowing && Overlays.lock.lockedPackage != foreground) {
            Overlays.lock.hide()
        }
        if (Overlays.cooldown.isShowing && Overlays.cooldown.shownForPackage != foreground) {
            Overlays.cooldown.hide()
        }

        if (foreground == null) {
            lastForeground = null
            return
        }
        val enteredNow = foreground != lastForeground
        lastForeground = foreground

        // Applies to every app, not just ones with a configured limit — unlike the checks below,
        // which only ever run for a package that already has an AppLimit row.
        if (foreground != appContext.packageName && SleepPrefs.isMorningRoutineActive(appContext)) {
            triggerLock(
                foreground, labelFor(foreground),
                label = "MORNING ROUTINE",
                subtitle = "Finish freshening up, yoga, and breakfast first — " +
                    "${SleepPrefs.morningRoutineRemainingMinutes(appContext)} min left.",
                showMidnightCountdown = false
            )
            return
        }

        val limit = limitRepository.getLimit(foreground)
        if (limit == null || !limit.enabled) return

        // Schedule and focus blocks apply regardless of the minute/open-count budgets below.
        if (limit.blockDuringSchedule) {
            val category = activeScheduleCategory()
            if (category != null) {
                triggerLock(
                    foreground, limit.appName,
                    label = "SCHEDULED BLOCK",
                    subtitle = "This app is blocked during your $category time.",
                    showMidnightCountdown = false
                )
                return
            }
        }
        if (limit.blockDuringFocus && FocusSessionState.active) {
            triggerLock(
                foreground, limit.appName,
                label = "FOCUS SESSION",
                subtitle = "Blocked while your focus session is running.",
                showMidnightCountdown = false
            )
            return
        }

        val usedMillis = limitRepository.todaysUsageMillis(foreground)
        val limitMillis = limit.dailyLimitMinutes * 60_000L
        if (usedMillis >= limitMillis) {
            triggerLock(foreground, limit.appName)
            return
        }

        if (limit.openCountLimit != null && enteredNow) {
            val opens = limitRepository.todaysOpenCount(foreground)
            if (opens > limit.openCountLimit) {
                triggerLock(
                    foreground, limit.appName,
                    label = "OPEN LIMIT REACHED",
                    subtitle = "You've opened this app ${opens}x today — today's limit is ${limit.openCountLimit}."
                )
                return
            }
        }

        if (Overlays.lock.lockedPackage == foreground) Overlays.lock.hide()

        maybeWarn(limit, usedMillis, limitMillis)
    }

    /** Cooldown plays once, then hands off to the real lock — never grants extra time. */
    private fun triggerLock(
        packageName: String,
        appName: String,
        label: String = "DAILY LIMIT REACHED",
        subtitle: String = "You've used your time for today. This app unlocks automatically at midnight.",
        showMidnightCountdown: Boolean = true
    ) {
        if (Overlays.lock.lockedPackage == packageName) return
        if (Overlays.cooldown.isShowing && Overlays.cooldown.shownForPackage == packageName) return
        Overlays.cooldown.show(packageName, appName) {
            Overlays.lock.show(packageName, appName, label, subtitle, showMidnightCountdown)
        }
    }

    private fun labelFor(packageName: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private suspend fun activeScheduleCategory(): String? {
        val autoBlockCategories = SchedulePrefs.getAutoBlockCategories(appContext)
        if (autoBlockCategories.isEmpty()) return null
        val day = RoutineRepository.currentDayOfWeekIndex()
        val minute = RoutineRepository.currentMinuteOfDay()
        return routineRepository.getCached()
            .firstOrNull { it.category in autoBlockCategories && it.coversNow(day, minute) }
            ?.category
    }

    private suspend fun maybeWarn(limit: AppLimit, usedMillis: Long, limitMillis: Long) {
        val today = UsageStatsHelper.todayEpochDay()
        if (limit.lastWarnedEpochDay == today) return
        if (usedMillis < (limitMillis * 0.9).toLong()) return

        limitRepository.markWarned(limit)
        val remainingMinutes = ((limitMillis - usedMillis) / 60_000L).coerceAtLeast(0)
        val nm = appContext.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(appContext, LockApp.WARNING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${limit.appName}: almost at today's limit")
            .setContentText("About $remainingMinutes min left before it locks for the day.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(limit.packageName.hashCode(), notification)
    }
}
