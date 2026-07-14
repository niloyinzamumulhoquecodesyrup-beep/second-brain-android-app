package com.secondbrain.lock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.secondbrain.lock.service.MonitorService
import com.secondbrain.lock.util.Permissions

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Permissions.allGranted(context)) {
            MonitorService.start(context)
        }
    }
}
