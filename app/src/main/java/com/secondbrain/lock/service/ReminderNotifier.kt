package com.secondbrain.lock.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.secondbrain.lock.LockApp
import com.secondbrain.lock.MainActivity
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.receiver.ReminderActionReceiver
import com.secondbrain.lock.ui.nudge.NudgeTakeoverActivity
import com.secondbrain.lock.ui.screens.work.currentMinuteOfDay
import com.secondbrain.lock.ui.screens.work.isCurrentNow
import com.secondbrain.lock.ui.theme.StreakAccent
import androidx.compose.ui.graphics.toArgb

/**
 * The single place a reminder notification is ever constructed — the local alarm tier
 * ([ReminderScheduler]) and, later, the FCM tier both call this, so there's exactly one
 * construction path and no drift between them. [show]'s notification id is a stable hash of
 * [reminderId], never a timestamp, so a reminder arriving via both tiers replaces rather than
 * duplicates.
 *
 * P4's escalation ladder, driven entirely by [snoozeCount] (device-local, from [ReminderState]):
 *   0-1  -> a standard, dismissible heads-up
 *   2    -> ongoing (can't be swiped away) and colorized in [StreakAccent]
 *   3+   -> the same, plus a full-screen takeover via [NudgeTakeoverActivity]
 * Levels 2+ are suppressed — falling back to the standard level 0/1 presentation — whenever
 * [isEscalationBlocked] can't rule out quiet hours or an active focus session. This only ever
 * gates the *escalation*, never the reminder itself: a plain heads-up still always fires,
 * including fully offline.
 */
object ReminderNotifier {
    suspend fun show(
        context: Context,
        reminderId: String,
        taskId: String?,
        title: String,
        body: String,
        snoozeCount: Int
    ) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val requestedLevel = when {
            snoozeCount >= 3 -> 3
            snoozeCount == 2 -> 2
            else -> 0
        }
        val level = if (requestedLevel >= 2 && isEscalationBlocked()) 0 else requestedLevel

        val contentIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // PERSIST_CHANNEL_ID is IMPORTANCE_HIGH same as TASK_CHANNEL_ID, but user-visibly labeled
        // "Persistent reminders" — LockApp declared it specifically for this level>=2 tier (see its
        // KDoc there), so an ongoing/colorized notification shouldn't quietly ride the plain
        // "Task reminders" channel a user might have muted or customized differently.
        val channelId = if (level >= 2) LockApp.PERSIST_CHANNEL_ID else LockApp.TASK_CHANNEL_ID
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .addAction(actionButton(context, reminderId, taskId, title, ReminderActionReceiver.ACTION_DONE, "Done"))
            .addAction(actionButton(context, reminderId, taskId, title, ReminderActionReceiver.ACTION_SNOOZE, "10 min"))
            .addAction(actionButton(context, reminderId, taskId, title, ReminderActionReceiver.ACTION_BREAK, "Too big"))

        if (level >= 2) {
            builder.setOngoing(true)
                .setAutoCancel(false)
                .setColorized(true)
                .setColor(StreakAccent.toArgb())
        } else {
            builder.setAutoCancel(true)
        }

        if (level >= 3) {
            val takeoverIntent = PendingIntent.getActivity(
                context,
                "$reminderId:takeover".hashCode(),
                NudgeTakeoverActivity.intent(context, reminderId, taskId, title),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setFullScreenIntent(takeoverIntent, true)
        }

        context.getSystemService(NotificationManager::class.java)?.notify(reminderId.hashCode(), builder.build())
    }

    /** Fails CLOSED: if either check can't be reached (offline, timeout, server error), this
     * returns true and escalation is suppressed. A full-screen takeover firing during quiet hours
     * or an active focus session is the exact harm this gate exists to prevent — staying at a
     * plain heads-up a little longer, on the rare occasion connectivity is the reason we can't
     * verify, is a far smaller cost than getting that wrong. Note `/api/activity/focus-state`
     * (named in the original spec for this check) is write-only with no `GET` — see
     * docs/api-reference.md — so this uses `/api/focus/state`, the one endpoint that actually
     * answers "is a focus session active" from the client's side. */
    private suspend fun isEscalationBlocked(): Boolean {
        val prefs = runCatching { ApiClient.getNotificationPrefs() }.getOrNull()?.getOrNull()
            ?: return true
        if (isWithinQuietHours(prefs.quietStartMin, prefs.quietEndMin)) return true

        val focus = runCatching { ApiClient.getFocusState() }.getOrNull()?.getOrNull()
            ?: return true
        return focus.active
    }

    /** Reuses [isCurrentNow]'s overnight-wrap handling (already verified for routine windows)
     * rather than re-deriving the same wraparound math for quiet hours. */
    private fun isWithinQuietHours(quietStartMin: Int?, quietEndMin: Int?): Boolean {
        if (quietStartMin == null || quietEndMin == null) return false
        val duration = if (quietEndMin >= quietStartMin) quietEndMin - quietStartMin
        else (quietEndMin + 1440) - quietStartMin
        return isCurrentNow(quietStartMin, duration, currentMinuteOfDay())
    }

    private fun actionButton(
        context: Context,
        reminderId: String,
        taskId: String?,
        title: String,
        action: String,
        label: String
    ): NotificationCompat.Action {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId ?: reminderId)
            putExtra(ReminderScheduler.EXTRA_TITLE, title)
        }
        // Request code mixes the action into the hash so Done/Snooze/Break on the SAME reminder
        // get distinct PendingIntents instead of one clobbering another's extras via FLAG_UPDATE_CURRENT.
        val pendingIntent = PendingIntent.getBroadcast(
            context, "$reminderId:$action".hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(0, label, pendingIntent).build()
    }
}
