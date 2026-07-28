package com.secondbrain.lock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import coil.Coil
import coil.ImageLoader
import com.secondbrain.lock.data.SecurePrefs
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.service.Overlays
import com.secondbrain.lock.ui.theme.SbThemeState
import com.secondbrain.lock.ui.theme.ThemeMode
import com.secondbrain.lock.widget.SectographUpdateWorker

class LockApp : Application() {
    override fun onCreate() {
        super.onCreate()

        ApiClient.init(this)
        Overlays.init(this)
        // Reuses ApiClient's OkHttp client (bearer-token interceptor included) so any AsyncImage
        // pointed at an authenticated endpoint like /api/auth/avatar just works.
        Coil.setImageLoader(ImageLoader.Builder(this).okHttpClient(ApiClient.client).build())
        SbThemeState.mode = ThemeMode.fromStorageKey(SecurePrefs.getTheme(this))
        SectographUpdateWorker.schedulePeriodic(this)

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
                ).apply {
                    description = "Alerts when you're close to a daily limit"
                    setSound(
                        Uri.parse("android.resource://$packageName/raw/notification"),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    ALARM_CHANNEL_ID,
                    "Wake alarm",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Your morning wake alarm"
                    setBypassDnd(true)
                    setSound(
                        Uri.parse("android.resource://$packageName/raw/alarm_wake"),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            )
        }
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "monitor_service"
        const val WARNING_CHANNEL_ID = "usage_warning"
        const val ALARM_CHANNEL_ID = "wake_alarm"
    }
}
