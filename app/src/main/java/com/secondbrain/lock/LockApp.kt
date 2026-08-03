package com.secondbrain.lock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import coil.Coil
import coil.ImageLoader
import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.data.SecurePrefs
import com.secondbrain.lock.data.SyncQueue
import com.secondbrain.lock.data.repo.MindQueueRepository
import com.secondbrain.lock.data.repo.MindRepository
import com.secondbrain.lock.data.repo.MindverseRepository
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.data.repo.ProfileRepository
import com.secondbrain.lock.data.repo.RemindersRepository
import com.secondbrain.lock.data.repo.StatsRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.service.Overlays
import com.secondbrain.lock.ui.theme.SbThemeState
import com.secondbrain.lock.ui.theme.ThemeMode
import com.secondbrain.lock.widget.SectographUpdateWorker
import kotlinx.coroutines.runBlocking

class LockApp : Application() {
    override fun onCreate() {
        super.onCreate()

        ApiClient.init(this)
        Overlays.init(this)
        LocalCache.init(this)
        SyncQueue.init(this)
        // Local-first: every repository's last-saved data is loaded into memory before the first
        // screen composes, so the UI shows it immediately (even if stale) instead of a blank
        // state while each screen's own network refresh() is still in flight. These are just a
        // handful of local SQLite reads, so blocking here briefly (like the theme read below) is
        // cheap relative to the rest of process startup.
        runBlocking {
            MindRepository.restore()
            MindQueueRepository.restore()
            MindverseRepository.restore()
            NotesRepository.restore()
            PlannerRepository.restore()
            ProfileRepository.restore()
            RemindersRepository.restore()
            StatsRepository.restore()
            TasksRepository.restore()
        }
        // Runs immediately if a network is already up, or waits for one via WorkManager's
        // NetworkType.CONNECTED constraint — replays anything SyncQueue queued last session while
        // offline (task creates/edits, focus-activity logs) that never made it to the server.
        SyncQueue.scheduleFlush()
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
