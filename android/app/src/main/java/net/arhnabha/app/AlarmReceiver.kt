package net.arhnabha.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val svc = Intent(ctx, AdhanService::class.java).putExtras(intent.extras ?: Bundle())
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc) else ctx.startService(svc)
        } catch (e: Exception) { }
        // Reschedule the next occurrences (next day), so the cycle keeps going.
        Scheduler.scheduleAll(ctx)
    }
}
