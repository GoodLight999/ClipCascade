package com.clipcascade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.facebook.react.HeadlessJsTaskService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val headlessTaskIntent = Intent(context, HeadlessTaskService::class.java).apply {
                putExtra("event", "BOOT_COMPLETED")
            }
            context.startService(headlessTaskIntent)
            HeadlessJsTaskService.acquireWakeLockNow(context)
        }
    }
}
