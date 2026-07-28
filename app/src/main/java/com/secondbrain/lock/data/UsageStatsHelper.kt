package com.secondbrain.lock.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.ZoneId
import java.util.Calendar

object UsageStatsHelper {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Milliseconds until the next local midnight — when all limits reset. */
    fun millisUntilReset(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis - System.currentTimeMillis()
    }

    fun todayEpochDay(): Long = java.time.LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    /** Total foreground time (ms) for [packageName] since local midnight, per the system's own tracking. */
    fun todaysUsageMillis(context: Context, packageName: String): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = startOfTodayMillis()
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        return stats?.filter { it.packageName == packageName }?.sumOf { it.totalTimeInForeground } ?: 0L
    }

    /**
     * Counts how many times [packageName] has been brought to the foreground from a *different*
     * app (or from nothing) since local midnight — i.e. distinct "opens," not every resume from
     * screen-on/off blips within the same session. Only worth calling on an actual foreground
     * transition (it walks the full day's event log), not on every poll tick.
     */
    fun todaysOpenCount(context: Context, packageName: String): Int {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(startOfTodayMillis(), System.currentTimeMillis())
        var lastForeground: String? = null
        var opens = 0
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // ACTIVITY_RESUMED/PAUSED share the same underlying values as the deprecated
            // MOVE_TO_FOREGROUND/BACKGROUND, so matching one covers both.
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (event.packageName != lastForeground) {
                        if (event.packageName == packageName) opens++
                        lastForeground = event.packageName
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (event.packageName == lastForeground) lastForeground = null
                }
            }
        }
        return opens
    }

    /**
     * Walks the raw event log since [sinceMillis] to find the package currently in the
     * foreground. More responsive than [android.app.usage.UsageStats] aggregates, which can
     * lag by up to a minute.
     */
    fun currentForegroundPackage(context: Context, sinceMillis: Long): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(sinceMillis, System.currentTimeMillis())
        var lastForeground: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                lastForeground = event.packageName
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
            ) {
                if (event.packageName == lastForeground) lastForeground = null
            }
        }
        return lastForeground
    }
}
