package net.arhnabha.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import java.util.Calendar

// --- Provider classes (each is a selectable widget shape) ---
class WidgetCompact : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, Widgets.buildCompact(ctx, false))
    }
}
class WidgetFull : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, Widgets.buildFull(ctx, false))
    }
}
class WidgetFullDark : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, Widgets.buildFull(ctx, true))
    }
}

object Widgets {
    private val ATHAN = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
    private val ROW = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
    private val DEF_NAMES = mapOf(
        "fajr" to "الفجر", "sunrise" to "الشروق", "dhuhr" to "الظهر",
        "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء"
    )

    /** Refresh every instance of all three widget shapes. */
    fun updateAll(ctx: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            refresh(ctx, mgr, WidgetCompact::class.java)
            refresh(ctx, mgr, WidgetFull::class.java)
            refresh(ctx, mgr, WidgetFullDark::class.java)
        } catch (e: Exception) { }
    }

    private fun refresh(ctx: Context, mgr: AppWidgetManager, cls: Class<*>) {
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls)) ?: return
        if (ids.isEmpty()) return
        val dark = cls == WidgetFullDark::class.java
        val views = if (cls == WidgetCompact::class.java) buildCompact(ctx, dark) else buildFull(ctx, dark)
        for (id in ids) mgr.updateAppWidget(id, views)
    }

    private class Data(
        val nextKey: String, val nextName: String, val nextMillis: Long,
        val nextLbl: String, val times: Map<String, Int>, val names: Map<String, String>, val clock24: Boolean
    )

    private fun compute(ctx: Context): Data {
        val cfg = Scheduler.config(ctx)
        val city = cfg?.optString("city", "duhok") ?: "duhok"
        val clock24 = cfg?.optBoolean("clock24", false) ?: false
        val nextLbl = cfg?.optString("nextLbl", "الصلاة القادمة بعد") ?: "الصلاة القادمة بعد"
        val offsets = HashMap<String, Int>()
        cfg?.optJSONObject("offsets")?.let { oj -> oj.keys().forEach { k -> offsets[k] = oj.optInt(k, 0) } }
        val names = HashMap<String, String>()
        val nj = cfg?.optJSONObject("names")
        for (k in ROW) names[k] = nj?.optString(k, DEF_NAMES[k]) ?: DEF_NAMES[k]!!

        val today = Calendar.getInstance()
        val times = PrayerTimes.timesFor(city, today, offsets)
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var nextKey = ""; var nextMillis = 0L
        for (k in ATHAN) { val m = times[k] ?: 0; if (m > nowMin) { nextKey = k; nextMillis = millisAt(today, m); break } }
        if (nextKey == "") {
            val tm = Calendar.getInstance(); tm.add(Calendar.DAY_OF_YEAR, 1)
            val tt = PrayerTimes.timesFor(city, tm, offsets)
            nextKey = "fajr"; nextMillis = millisAt(tm, tt["fajr"] ?: 0)
        }
        return Data(nextKey, names[nextKey] ?: nextKey, nextMillis, nextLbl, times, names, clock24)
    }

    private fun millisAt(day: Calendar, minutes: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.YEAR, day.get(Calendar.YEAR))
        c.set(Calendar.DAY_OF_YEAR, day.get(Calendar.DAY_OF_YEAR))
        c.set(Calendar.HOUR_OF_DAY, minutes / 60); c.set(Calendar.MINUTE, minutes % 60)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun fmt(min: Int, clock24: Boolean): String {
        val m = ((min % 1440) + 1440) % 1440
        val h = m / 60; val mm = m % 60
        if (clock24) return String.format("%02d:%02d", h, mm)
        val hh = ((h + 11) % 12) + 1
        val ap = if (h < 12) "ص" else "م"
        return String.format("%02d:%02d %s", hh, mm, ap)
    }

    private fun openPI(ctx: Context): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(ctx, 9, Intent(ctx, MainActivity::class.java), flags)
    }

    fun buildCompact(ctx: Context, dark: Boolean): RemoteViews {
        val d = compute(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_compact)
        val cLbl = if (dark) 0xFFB9A6C9.toInt() else 0xFF6B5B73.toInt()
        val cBig = if (dark) 0xFFF3B9C6.toInt() else 0xFFC25D78.toInt()
        val cName = if (dark) 0xFFFDEEF2.toInt() else 0xFF4A4458.toInt()
        v.setInt(R.id.wRoot, "setBackgroundResource", if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
        v.setTextViewText(R.id.wNextLbl, d.nextLbl)
        v.setTextColor(R.id.wNextLbl, cLbl)
        v.setTextColor(R.id.wChrono, cBig)
        v.setTextViewText(R.id.wNextName, d.nextName)
        v.setTextColor(R.id.wNextName, cName)
        val base = SystemClock.elapsedRealtime() + (d.nextMillis - System.currentTimeMillis())
        v.setChronometerCountDown(R.id.wChrono, true)
        v.setChronometer(R.id.wChrono, base, null, true)
        v.setOnClickPendingIntent(R.id.wRoot, openPI(ctx))
        return v
    }

    fun buildFull(ctx: Context, dark: Boolean): RemoteViews {
        val d = compute(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_full)
        val cLbl = if (dark) 0xFFB9A6C9.toInt() else 0xFF6B5B73.toInt()
        val cBig = if (dark) 0xFFF3B9C6.toInt() else 0xFFC25D78.toInt()
        val cName = if (dark) 0xFFFDEEF2.toInt() else 0xFF4A4458.toInt()
        val cTimeName = if (dark) 0xFFB9A6C9.toInt() else 0xFF6B5B73.toInt()
        val cTime = if (dark) 0xFFFDEEF2.toInt() else 0xFF4A4458.toInt()
        v.setInt(R.id.wRoot, "setBackgroundResource", if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light)
        v.setTextViewText(R.id.wNextLbl, d.nextLbl); v.setTextColor(R.id.wNextLbl, cLbl)
        v.setTextColor(R.id.wChrono, cBig)
        v.setTextViewText(R.id.wNextName, d.nextName); v.setTextColor(R.id.wNextName, cName)
        val base = SystemClock.elapsedRealtime() + (d.nextMillis - System.currentTimeMillis())
        v.setChronometerCountDown(R.id.wChrono, true)
        v.setChronometer(R.id.wChrono, base, null, true)

        val nIds = intArrayOf(R.id.wN0, R.id.wN1, R.id.wN2, R.id.wN3, R.id.wN4, R.id.wN5)
        val tIds = intArrayOf(R.id.wT0, R.id.wT1, R.id.wT2, R.id.wT3, R.id.wT4, R.id.wT5)
        for (i in ROW.indices) {
            val k = ROW[i]
            val isNext = (k == d.nextKey)
            v.setTextViewText(nIds[i], d.names[k] ?: k)
            v.setTextColor(nIds[i], if (isNext) cBig else cTimeName)
            v.setTextViewText(tIds[i], fmt(d.times[k] ?: 0, d.clock24))
            v.setTextColor(tIds[i], if (isNext) cBig else cTime)
        }
        v.setOnClickPendingIntent(R.id.wRoot, openPI(ctx))
        return v
    }
}
