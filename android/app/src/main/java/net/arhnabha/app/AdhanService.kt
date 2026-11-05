package net.arhnabha.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class AdhanService : Service() {
    private var mp: MediaPlayer? = null
    private var sensorMgr: SensorManager? = null
    private var flipListener: SensorEventListener? = null
    private var flipArmAt = 0L
    private var curPre = false
    private var tts: TextToSpeech? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopEverything(); return START_NOT_STICKY }

        val b = intent?.extras
        val title = b?.getString("title") ?: "أرحنا بها"
        val pre = b?.getBoolean("pre", false) ?: false
        val sound = b?.getString("sound") ?: ""
        val duaOn = b?.getBoolean("duaOn", false) ?: false
        val dua = b?.getString("dua") ?: ""
        val showScreen = b?.getBoolean("showScreen", false) ?: false
        val name = b?.getString("name") ?: ""
        val speak = b?.getBoolean("speak", false) ?: false
        curPre = pre

        ensureChannel()
        val notif = buildNotif(title, pre, showScreen, name)
        val fgId = if (pre) 2 else 1
        try {
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(fgId, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(fgId, notif)
        } catch (e: Exception) { startForeground(fgId, notif) }

        val flip = b?.getBoolean("flipStop", false) ?: false
        if (!pre && sound.isNotEmpty()) {
            if (flip) registerFlip()
            playAsset(sound) {
                if (duaOn && dua.isNotEmpty()) playAsset(dua) { stopEverything() } else stopEverything()
            }
        } else if (pre && speak) {
            speakText(title)
            Handler(Looper.getMainLooper()).postDelayed({ stopEverything() }, 12000) // safety
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ stopEverything() }, 2500)
        }
        return START_NOT_STICKY
    }

    private fun speakText(text: String) {
        try {
            val engine = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try { tts?.language = Locale("ar") } catch (e: Exception) { }
                    try {
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(id: String?) {}
                            override fun onDone(id: String?) { stopEverything() }
                            @Deprecated("Deprecated in Java")
                            override fun onError(id: String?) { stopEverything() }
                        })
                    } catch (e: Exception) { }
                    try { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "pre") } catch (e: Exception) { stopEverything() }
                } else stopEverything()
            }
            tts = engine
        } catch (e: Exception) { }
    }

    private fun registerFlip() {
        try {
            val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            flipArmAt = SystemClock.elapsedRealtime() + 1200 // arm after 1.2s to avoid instant stop
            val lis = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    if (e.values.size < 3) return
                    // face-down: z strongly negative (phone screen down)
                    if (SystemClock.elapsedRealtime() > flipArmAt && e.values[2] < -7.5f) stopEverything()
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm.registerListener(lis, acc, SensorManager.SENSOR_DELAY_UI)
            sensorMgr = sm; flipListener = lis
        } catch (e: Exception) { }
    }

    private fun unregisterFlip() {
        try { flipListener?.let { sensorMgr?.unregisterListener(it) } } catch (e: Exception) { }
        flipListener = null; sensorMgr = null
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
        unregisterFlip()
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
        tts = null
        try {
            if (Build.VERSION.SDK_INT >= 24)
                stopForeground(if (curPre) Service.STOP_FOREGROUND_DETACH else Service.STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(!curPre)
        } catch (e: Exception) { }
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
    private fun buildNotif(title: String, pre: Boolean, showScreen: Boolean, name: String): Notification {
        val openFlags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val openIntent = Intent(this, MainActivity::class.java)
        val open = PendingIntent.getActivity(this, 0, openIntent, openFlags)
        val stopIntent = Intent(this, AdhanService::class.java).setAction("STOP")
        val stop = PendingIntent.getService(this, 1, stopIntent, openFlags)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "adhan") else Notification.Builder(this)
        b.setContentTitle(title).setContentText("أرحنا بها")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open).setAutoCancel(true).setOngoing(!pre)
        if (!pre) {
            b.addAction(Notification.Action.Builder(0, "إيقاف", stop).build())
            if (showScreen) {
                val fs = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("showAdhan", true).putExtra("prayerName", name)
                val fsFlags = openFlags or PendingIntent.FLAG_UPDATE_CURRENT
                val fsPI = PendingIntent.getActivity(this, 5, fs, fsFlags)
                b.setFullScreenIntent(fsPI, true)
            }
        }
        return b.build()
    }
}
