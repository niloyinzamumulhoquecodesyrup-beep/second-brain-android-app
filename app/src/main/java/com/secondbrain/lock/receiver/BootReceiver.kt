package com.secondbrain.lock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.secondbrain.lock.service.AlarmScheduler
import com.secondbrain.lock.service.MonitorService
import com.secondbrain.lock.service.ReminderScheduler
import com.secondbrain.lock.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Permissions.allGranted(context)) {
            MonitorService.start(context)
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { AlarmScheduler.reschedule(context) }
            // LockApp.onCreate() (which runs before any receiver in this process) already called
            // ReminderScheduler.init() and an initial rescheduleAll() — this call is for the case
            // the day rolled over while the device was off, so "today" now means something
            // different than it did when the process last restored its task list.
            runCatching { ReminderScheduler.rescheduleAll() }
            pending.finish()
        }
    }
}
