package com.secondbrain.lock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.secondbrain.lock.service.AlarmScheduler
import com.secondbrain.lock.service.MonitorService
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
            pending.finish()
        }
    }
}
