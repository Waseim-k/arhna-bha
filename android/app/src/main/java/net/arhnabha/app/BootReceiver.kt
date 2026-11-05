package net.arhnabha.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Re-arm all alarms after reboot / app update.
        Scheduler.scheduleAll(ctx)
    }
}
