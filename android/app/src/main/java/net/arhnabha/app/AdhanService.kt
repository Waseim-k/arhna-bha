package net.arhnabha.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AdhanService : Service() {
    private var mp: MediaPlayer? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopEverything(); return START_NOT_STICKY }

        val b = intent?.extras
        val title = b?.getString("title") ?: "أرحنا بها"
        val pre = b?.getBoolean("pre", false) ?: false
        val sound = b?.getString("sound") ?: ""
        val duaOn = b?.getBoolean("duaOn", false) ?: false
        val dua = b?.getString("dua") ?: ""

        ensureChannel()
        val notif = buildNotif(title, pre)
        try {
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(1, notif)
        } catch (e: Exception) { startForeground(1, notif) }

        if (!pre && sound.isNotEmpty()) {
            playAsset(sound) {
                if (duaOn && dua.isNotEmpty()) playAsset(dua) { stopEverything() } else stopEverything()
            }
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ stopEverything() }, 2500)
        }
        return START_NOT_STICKY
    }

    private fun playAsset(path: String, onDone: () -> Unit) {
        try {
            val afd = assets.openFd("www/$path")
            val m = MediaPlayer()
            m.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            m.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            m.setOnCompletionListener { it.release(); if (mp === it) mp = null; onDone() }
            m.setOnErrorListener { _, _, _ -> onDone(); true }
            m.prepare(); m.start(); mp = m
        } catch (e: Exception) { onDone() }
    }

    private fun stopEverything() {
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        try { stopForeground(true) } catch (e: Exception) { }
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel("adhan") == null) {
                val ch = NotificationChannel("adhan", "الأذان", NotificationManager.IMPORTANCE_HIGH)
                ch.setSound(null, null) // we play the adhan audio ourselves
                ch.enableVibration(true)
                nm.createNotificationChannel(ch)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotif(title: String, pre: Boolean): Notification {
        val openFlags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), openFlags)
        val stopIntent = Intent(this, AdhanService::class.java).setAction("STOP")
        val stop = PendingIntent.getService(this, 1, stopIntent, openFlags)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "adhan") else Notification.Builder(this)
        b.setContentTitle(title).setContentText("أرحنا بها")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open).setAutoCancel(true).setOngoing(!pre)
        if (!pre) b.addAction(Notification.Action.Builder(0, "إيقاف", stop).build())
        return b.build()
    }
}
