package com.secondbrain.lock.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.secondbrain.lock.data.UsageStatsHelper
import com.secondbrain.lock.service.LockAccessibilityService

object Permissions {

    fun hasOverlay(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** Optional — [com.secondbrain.lock.service.MonitorService]'s poller works without this. */
    fun hasAccessibility(context: Context): Boolean {
        val expected = "${context.packageName}/${LockAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun accessibilityIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun hasUsageAccess(context: Context): Boolean =
        UsageStatsHelper.hasUsageAccess(context)

    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Mindcord room's mic/camera — checked independently since a call is usable audio-only. */
    fun hasMicrophone(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasCamera(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** User-toggleable "special app access" on API 31+ (Settings -> Apps -> Special app access ->
     * Alarms & reminders), auto-granted below that. Revocable at any time — the OS kills this
     * process when the user flips it off, so callers must check live at scheduling time rather
     * than caching the result. Deliberately NOT folded into [allGranted]: Shield's onboarding
     * gates its Continue button on that function, and Shield's app-blocking works fine without
     * exact alarms — this is wired into its own optional onboarding step instead. */
    fun hasExactAlarm(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun exactAlarmIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))

    fun allGranted(context: Context): Boolean =
        hasOverlay(context) && hasUsageAccess(context) && hasNotifications(context)

    fun usageAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun overlayIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}
