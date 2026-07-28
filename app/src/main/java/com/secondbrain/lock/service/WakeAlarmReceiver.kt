package com.secondbrain.lock.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.secondbrain.lock.LockApp
import com.secondbrain.lock.ui.wake.WakeFlowActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when [AlarmScheduler]'s daily alarm goes off. Posts a full-screen-intent notification
 * (the documented way to reliably launch a full-screen UI from the background even over a
 * locked screen) and also tries a direct launch as a belt-and-suspenders fallback.
 */
class WakeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launchIntent = Intent(context, WakeFlowActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, LockApp.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time to wake up")
            .setContentText("Tap to open your wake alarm.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(launchPendingIntent, true)
            .setContentIntent(launchPendingIntent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(WAKE_NOTIFICATION_ID, notification)
        runCatching { context.startActivity(launchIntent) }

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { AlarmScheduler.reschedule(context) }
            pending.finish()
        }
    }

    companion object {
        private const val WAKE_NOTIFICATION_ID = 2001
    }
}
