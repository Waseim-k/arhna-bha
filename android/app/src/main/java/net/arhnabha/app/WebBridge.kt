package net.arhnabha.app

import android.app.Activity
import android.content.Intent
import android.webkit.JavascriptInterface

/** Exposed to the WebView as window.AndroidBridge */
class WebBridge(private val act: Activity) {

    @JavascriptInterface
    fun stopAdhan() {
        try { act.startService(Intent(act, AdhanService::class.java).setAction("STOP")) } catch (e: Exception) { }
    }

    @JavascriptInterface
    fun saveConfig(json: String) {
        Scheduler.saveConfig(act, json)
        Scheduler.scheduleAll(act)
    }

    @JavascriptInterface
    fun enableBackground() {
        act.runOnUiThread { (act as? MainActivity)?.requestBgPermissions() }
        Scheduler.scheduleAll(act)
    }

    @JavascriptInterface
    fun disableBackground() {
        Scheduler.cancelAll(act)
    }
}
