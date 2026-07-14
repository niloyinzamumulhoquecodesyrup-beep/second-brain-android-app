package com.secondbrain.lock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class LockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    MONITOR_CHANNEL_ID,
                    "Lock monitoring",
                    NotificationManager.IMPORTANCE_MIN
                ).apply { description = "Keeps app limits enforced in the background" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    WARNING_CHANNEL_ID,
                    "Usage warnings",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Alerts when you're close to a daily limit" }
            )
        }
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "monitor_service"
        const val WARNING_CHANNEL_ID = "usage_warning"
    }
}
