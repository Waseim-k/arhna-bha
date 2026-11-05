package net.arhnabha.app

import java.util.Calendar

/**
 * Native port of the web app's prayer-time engine:
 * fixed monthly tables (Bang-matched) + periodic Catmull-Rom interpolation.
 * Returns minutes-after-midnight for each prayer on a given date.
 */
object PrayerTimes {

    val PKEYS = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
    private val MONTH_STARTS = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    // 3 cities × 12 months (15th) × 6 times "HH:MM"
    private val TBL: Map<String, Array<Array<String>>> = mapOf(
        "duhok" to arrayOf(
            arrayOf("05:45","07:19","12:25","15:03","17:19","18:40"),
            arrayOf("05:27","06:58","12:30","15:30","17:54","19:16"),
            arrayOf("04:53","06:22","12:25","15:47","18:21","19:44"),
            arrayOf("04:04","05:38","12:16","15:54","18:47","20:12"),
            arrayOf("03:24","05:07","12:12","15:59","19:12","20:38"),
            arrayOf("03:07","04:57","12:17","16:05","19:33","21:00"),
            arrayOf("03:22","05:09","12:22","16:10","19:31","20:58"),
            arrayOf("03:54","05:31","12:20","16:03","19:05","20:30"),
            arrayOf("04:24","05:54","12:12","15:41","18:21","19:45"),
            arrayOf("04:49","06:17","12:02","15:10","17:39","19:02"),
            arrayOf("05:14","06:46","12:01","14:45","17:04","18:25"),
            arrayOf("05:38","07:12","12:11","14:43","16:57","18:17")
        ),
        "erbil" to arrayOf(
            arrayOf("05:58","07:18","12:22","14:55","17:18","18:33"),
            arrayOf("05:29","06:55","12:26","15:22","17:50","19:05"),
            arrayOf("04:50","06:19","12:23","15:39","18:15","19:30"),
            arrayOf("04:03","05:39","12:14","15:48","18:41","19:56"),
            arrayOf("03:29","05:06","12:09","15:58","19:07","20:22"),
            arrayOf("03:16","05:00","12:16","16:05","19:27","20:42"),
            arrayOf("03:29","05:10","12:22","16:09","19:26","20:41"),
            arrayOf("03:52","05:31","12:19","15:58","18:58","20:13"),
            arrayOf("04:22","05:54","12:10","15:34","18:16","19:31"),
            arrayOf("04:50","06:16","11:58","15:04","17:33","18:48"),
            arrayOf("05:22","06:45","11:58","14:38","17:01","18:16"),
            arrayOf("05:55","07:14","12:10","14:37","16:57","18:12")
        ),
        "sulaymaniyah" to arrayOf(
            arrayOf("05:43","07:07","12:14","14:50","17:16","18:35"),
            arrayOf("05:23","06:45","12:18","15:16","17:47","19:04"),
            arrayOf("04:46","06:07","12:13","15:32","18:14","19:29"),
            arrayOf("04:03","05:24","12:04","15:39","18:39","19:54"),
            arrayOf("03:28","04:52","12:01","15:43","19:04","20:22"),
            arrayOf("03:16","04:42","12:05","15:48","19:23","20:43"),
            arrayOf("03:29","04:55","12:10","15:54","19:21","20:40"),
            arrayOf("03:56","05:18","12:09","15:47","18:55","20:12"),
            arrayOf("04:21","05:42","12:00","15:26","18:13","19:29"),
            arrayOf("04:45","06:06","11:50","14:56","17:31","18:45"),
            arrayOf("05:13","06:35","11:49","14:32","17:00","18:17"),
            arrayOf("05:35","07:01","12:00","14:30","16:55","18:15")
        )
    )

    private fun toMin(s: String): Double {
        val p = s.split(":")
        return p[0].toInt() * 60.0 + p[1].toInt()
    }

    /** Day of year matching the web's doy(): Jan 1 -> 0. */
    private fun doy(cal: Calendar): Int = cal.get(Calendar.DAY_OF_YEAR) - 1

    private fun anchors(city: String, pi: Int): Pair<IntArray, DoubleArray> {
        val t = IntArray(12); val v = DoubleArray(12)
        val city2 = if (TBL.containsKey(city)) city else "duhok"
        for (m in 0 until 12) { t[m] = MONTH_STARTS[m] + 14; v[m] = toMin(TBL[city2]!![m][pi]) }
        return Pair(t, v)
    }

    // Periodic Catmull-Rom (identical math to the web interp()).
    private fun interp(ts: IntArray, vs: DoubleArray, day: Int): Double {
        val n = ts.size; val Y = 365
        fun idx(k: Int) = ((k % n) + n) % n
        val dref = if (day >= ts[0]) day else day + Y
        var i = 0
        for (k in 0 until n) {
            val a = ts[k]; val b = if (k + 1 < n) ts[k + 1] else ts[0] + Y
            if (dref >= a && dref < b) { i = k; break }
            if (k == n - 1) i = n - 1
        }
        val p0 = vs[idx(i - 1)]; val p1 = vs[idx(i)]; val p2 = vs[idx(i + 1)]; val p3 = vs[idx(i + 2)]
        var t0 = ts[idx(i)]; var t1 = ts[idx(i + 1)]
        if (t1 <= t0) t1 += Y
        val dd = if (dref < t0) dref + Y else dref
        var u = (dd - t0).toDouble() / (t1 - t0)
        u = u.coerceIn(0.0, 1.0)
        val u2 = u * u; val u3 = u2 * u
        return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3)
    }

    /** minutes-after-midnight per prayer key, with per-prayer offsets applied. */
    fun timesFor(city: String, cal: Calendar, offsets: Map<String, Int>): Map<String, Int> {
        val day = doy(cal)
        val out = HashMap<String, Int>()
        PKEYS.forEachIndexed { pi, k ->
            val (ts, vs) = anchors(city, pi)
            val m = interp(ts, vs, day) + (offsets[k] ?: 0)
            out[k] = Math.round(m).toInt()
        }
        return out
    }
}
