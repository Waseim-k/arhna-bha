package net.arhnabha.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import org.json.JSONObject
import java.util.Calendar

object Scheduler {
    const val PREFS = "arhna_bg"
    private val PRAYERS = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")

    fun config(ctx: Context): JSONObject? {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("config", null) ?: return null
        return try { JSONObject(s) } catch (e: Exception) { null }
    }

    fun saveConfig(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("config", json).apply()
    }

    private fun pi(ctx: Context, id: Int, extras: Bundle): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).putExtras(extras)
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) f = f or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, id, i, f)
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var id = 1000
        for (p in PRAYERS) { am.cancel(pi(ctx, id, Bundle())); am.cancel(pi(ctx, id + 1, Bundle())); id += 10 }
    }

    fun scheduleAll(ctx: Context) {
        val cfg = config(ctx) ?: return
        if (!cfg.optBoolean("enabled", false)) { cancelAll(ctx); return }
        val city = cfg.optString("city", "duhok")
        val pre = cfg.optInt("pre", 0)
        val offsets = HashMap<String, Int>()
        cfg.optJSONObject("offsets")?.let { oj -> oj.keys().forEach { k -> offsets[k] = oj.optInt(k, 0) } }
        val adhanTpl = cfg.optString("adhanTemplate", "{p}")
        val soonTpl = cfg.optString("soonTemplate", "{p}")
        val duaOn = cfg.optBoolean("duaOn", false)
        val dua = cfg.optString("dua", "")
        val prayers = cfg.optJSONObject("prayers") ?: JSONObject()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()
        var id = 1000
        for (p in PRAYERS) {
            val pc = prayers.optJSONObject(p)
            val on = pc?.optBoolean("on", false) ?: false
            if (!on) { am.cancel(pi(ctx, id, Bundle())); am.cancel(pi(ctx, id + 1, Bundle())); id += 10; continue }
            val name = pc?.optString("name", p) ?: p
            val sound = pc?.optString("sound", "") ?: ""
            val cal = nextTime(city, offsets, p, now, 0)
            val ex = Bundle().apply {
                putString("key", p); putString("name", name); putString("sound", sound)
                putBoolean("pre", false); putString("title", adhanTpl.replace("{p}", name))
                putBoolean("duaOn", duaOn); putString("dua", dua)
            }
            setExact(am, cal.timeInMillis, pi(ctx, id, ex))
            if (pre > 0) {
                val calp = nextTime(city, offsets, p, now, pre)
                val exp = Bundle().apply {
                    putString("key", p); putString("name", name); putString("sound", "")
                    putBoolean("pre", true); putString("title", soonTpl.replace("{p}", name))
                    putBoolean("duaOn", false); putString("dua", "")
                }
                setExact(am, calp.timeInMillis, pi(ctx, id + 1, exp))
            } else am.cancel(pi(ctx, id + 1, Bundle()))
            id += 10
        }
    }

    private fun calFor(day: Calendar, minutes: Int): Calendar {
        var m = minutes; if (m < 0) m = 0
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, day.get(Calendar.YEAR))
        cal.set(Calendar.DAY_OF_YEAR, day.get(Calendar.DAY_OF_YEAR))
        cal.set(Calendar.HOUR_OF_DAY, m / 60)
        cal.set(Calendar.MINUTE, m % 60)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun nextTime(city: String, offsets: Map<String, Int>, key: String, now: Calendar, subMin: Int): Calendar {
        for (addDay in 0..2) {
            val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, addDay)
            val times = PrayerTimes.timesFor(city, c, offsets)
            val cal = calFor(c, (times[key] ?: 0) - subMin)
            if (cal.timeInMillis > now.timeInMillis + 2000) return cal
        }
        val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, 1)
        val times = PrayerTimes.timesFor(city, c, offsets)
        return calFor(c, (times[key] ?: 0) - subMin)
    }

    private fun setExact(am: AlarmManager, time: Long, p: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p)
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p)
            } else am.setExact(AlarmManager.RTC_WAKEUP, time, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p)
        }
    }
}
