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
import com.secondbrain.lock.receiver.ReminderActionReceiver

/**
 * The single place a reminder notification is ever constructed — the local alarm tier
 * ([ReminderScheduler]) and, later, the FCM tier both call this, so there's exactly one
 * construction path and no drift between them. [show]'s notification id is a stable hash of
 * [reminderId], never a timestamp, so a reminder arriving via both tiers replaces rather than
 * duplicates. P4 will extend this further for the snooze-count escalation ladder.
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
            .addAction(actionButton(context, reminderId, taskId, title, ReminderActionReceiver.ACTION_DONE, "Done"))
            .addAction(actionButton(context, reminderId, taskId, title, ReminderActionReceiver.ACTION_SNOOZE, "10 min"))
            .addAction(actionButton(context, reminderId, taskId, title, ReminderActionReceiver.ACTION_BREAK, "Too big"))
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(reminderId.hashCode(), notification)
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
