package net.arhnabha.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pre = intent.getBooleanExtra("pre", false)
        if (pre) {
            if (intent.getBooleanExtra("speak", false)) {
                // spoken pre-alert → service handles notification + text-to-speech
                val svc = Intent(ctx, AdhanService::class.java).putExtras(intent.extras ?: Bundle())
                try {
                    if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc) else ctx.startService(svc)
                } catch (e: Exception) { }
            } else {
                postPre(ctx, intent.getStringExtra("title") ?: "أرحنا بها")
            }
        } else {
            val svc = Intent(ctx, AdhanService::class.java).putExtras(intent.extras ?: Bundle())
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc) else ctx.startService(svc)
            } catch (e: Exception) { }
        }
        // Re-arm next occurrences and refresh widgets.
        Scheduler.scheduleAll(ctx)
        Widgets.updateAll(ctx)
    }

    @Suppress("DEPRECATION")
    private fun postPre(ctx: Context, title: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("pre") == null) {
            val ch = NotificationChannel("pre", "تنبيه قبل الأذان", NotificationManager.IMPORTANCE_HIGH)
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
        val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java), flags)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "pre") else Notification.Builder(ctx)
        b.setContentTitle(title).setContentText("أرحنا بها")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open).setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
        nm.notify(2, b.build())
    }
}
