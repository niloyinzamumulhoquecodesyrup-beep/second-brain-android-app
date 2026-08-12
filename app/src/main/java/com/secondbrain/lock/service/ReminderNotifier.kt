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

/**
 * The single place a reminder notification is ever constructed — the local alarm tier
 * ([ReminderScheduler]) and, later, the FCM tier both call this, so there's exactly one
 * construction path and no drift between them. [show]'s notification id is a stable hash of
 * [reminderId], never a timestamp, so a reminder arriving via both tiers replaces rather than
 * duplicates. This builds a plain heads-up notification for now — P3 adds action buttons, P4 adds
 * the snooze-count escalation ladder, both by extending this same function.
 */
object ReminderNotifier {
    fun show(
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

        val contentIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, LockApp.TASK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(reminderId.hashCode(), notification)
    }
}
