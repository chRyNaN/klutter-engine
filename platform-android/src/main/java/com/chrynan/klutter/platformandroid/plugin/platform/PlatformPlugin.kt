package com.chrynan.klutter.platformandroid.plugin.platform

import android.content.ClipData
import org.json.JSONObject
import android.os.Build
import netscape.javascript.JSObject.getWindow
import org.json.JSONArray
import android.app.ActivityManager.TaskDescription
import android.content.pm.ActivityInfo
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import org.junit.runner.Request.method
import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import org.json.JSONException

/**
 * Android implementation of the platform plugin.
 */
class PlatformPlugin(private val mActivity: Activity) : MethodCallHandler,
    ActivityLifecycleListener {

    companion object {

        val DEFAULT_SYSTEM_UI = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        private val kTextPlainFormat = "text/plain"
    }

    private var mCurrentTheme: JSONObject? = null

    private var mEnabledOverlays: Int = 0

    init {
        mEnabledOverlays =
                DEFAULT_SYSTEM_UI
    }

    fun onMethodCall(call: MethodCall, result: Result) {
        val method = call.method
        val arguments = call.arguments
        try {
            if (method == "SystemSound.play") {
                playSystemSound(arguments as String)
                result.success(null)
            } else if (method == "HapticFeedback.vibrate") {
                vibrateHapticFeedback(arguments as String)
                result.success(null)
            } else if (method == "SystemChrome.setPreferredOrientations") {
                setSystemChromePreferredOrientations(arguments as JSONArray)
                result.success(null)
            } else if (method == "SystemChrome.setApplicationSwitcherDescription") {
                setSystemChromeApplicationSwitcherDescription(arguments as JSONObject)
                result.success(null)
            } else if (method == "SystemChrome.setEnabledSystemUIOverlays") {
                setSystemChromeEnabledSystemUIOverlays(arguments as JSONArray)
                result.success(null)
            } else if (method == "SystemChrome.restoreSystemUIOverlays") {
                restoreSystemChromeSystemUIOverlays()
                result.success(null)
            } else if (method == "SystemChrome.setSystemUIOverlayStyle") {
                setSystemChromeSystemUIOverlayStyle(arguments as JSONObject)
                result.success(null)
            } else if (method == "SystemNavigator.pop") {
                popSystemNavigator()
                result.success(null)
            } else if (method == "Clipboard.getData") {
                result.success(getClipboardData(arguments as String))
            } else if (method == "Clipboard.setData") {
                setClipboardData(arguments as JSONObject)
                result.success(null)
            } else {
                result.notImplemented()
            }
        } catch (e: JSONException) {
            result.error("error", "JSON error: " + e.getMessage(), null)
        }

    }

    private fun playSystemSound(soundType: String) {
        if (soundType == "SystemSoundType.click") {
            val view = mActivity.window.decorView
            view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    private fun vibrateHapticFeedback(feedbackType: String?) {
        val view = mActivity.window.decorView
        if (feedbackType == null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } else if (feedbackType == "HapticFeedbackType.lightImpact") {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (feedbackType == "HapticFeedbackType.mediumImpact") {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } else if (feedbackType == "HapticFeedbackType.heavyImpact") {
            // HapticFeedbackConstants.CONTEXT_CLICK from API level 23.
            view.performHapticFeedback(6)
        } else if (feedbackType == "HapticFeedbackType.selectionClick") {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    @Throws(JSONException::class)
    private fun setSystemChromePreferredOrientations(orientations: JSONArray) {
        var requestedOrientation = 0x00
        var firstRequestedOrientation = 0x00
        var index = 0
        while (index < orientations.length()) {
            if (orientations.getString(index) == "DeviceOrientation.portraitUp") {
                requestedOrientation = requestedOrientation or 0x01
            } else if (orientations.getString(index) == "DeviceOrientation.landscapeLeft") {
                requestedOrientation = requestedOrientation or 0x02
            } else if (orientations.getString(index) == "DeviceOrientation.portraitDown") {
                requestedOrientation = requestedOrientation or 0x04
            } else if (orientations.getString(index) == "DeviceOrientation.landscapeRight") {
                requestedOrientation = requestedOrientation or 0x08
            }
            if (firstRequestedOrientation == 0x00) {
                firstRequestedOrientation = requestedOrientation
            }
            index += 1
        }
        when (requestedOrientation) {
            0x00 -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            0x01 -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            0x02 -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            0x04 -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            0x05 -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            0x08 -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            0x0a -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            0x0b -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            0x0f -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            0x03 // portraitUp and landscapeLeft
                , 0x06 // portraitDown and landscapeLeft
                , 0x07 // portraitUp, portraitDown, and landscapeLeft
                , 0x09 // portraitUp and landscapeRight
                , 0x0c // portraitDown and landscapeRight
                , 0x0d // portraitUp, portraitDown, and landscapeRight
                , 0x0e // portraitDown, landscapeLeft, and landscapeRight
            ->
                // Android can't describe these cases, so just default to whatever the first
                // specified value was.
                when (firstRequestedOrientation) {
                    0x01 -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    0x02 -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    0x04 -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    0x08 -> mActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
        }
    }

    @Throws(JSONException::class)
    private fun setSystemChromeApplicationSwitcherDescription(description: JSONObject) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        var color = description.getInt("primaryColor")
        if (color != 0) { // 0 means color isn't set, use system default
            color = color or -0x1000000 // color must be opaque if set
        }

        val label = description.getString("label")

        val taskDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            TaskDescription(label, 0, color)
        else
            TaskDescription(label, null, color)

        mActivity.setTaskDescription(taskDescription)
    }

    @Throws(JSONException::class)
    private fun setSystemChromeEnabledSystemUIOverlays(overlays: JSONArray) {
        var enabledOverlays = (DEFAULT_SYSTEM_UI
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

        if (overlays.length() == 0) {
            enabledOverlays = enabledOverlays or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        for (i in 0 until overlays.length()) {
            val overlay = overlays.getString(i)
            if (overlay == "SystemUiOverlay.top") {
                enabledOverlays = enabledOverlays and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
            } else if (overlay == "SystemUiOverlay.bottom") {
                enabledOverlays = enabledOverlays and View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION.inv()
                enabledOverlays = enabledOverlays and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
            }
        }

        mEnabledOverlays = enabledOverlays
        updateSystemUiOverlays()
    }

    private fun updateSystemUiOverlays() {
        mActivity.window.decorView.systemUiVisibility = mEnabledOverlays
        if (mCurrentTheme != null) {
            setSystemChromeSystemUIOverlayStyle(mCurrentTheme)
        }
    }

    private fun restoreSystemChromeSystemUIOverlays() {
        updateSystemUiOverlays()
    }

    private fun setSystemChromeSystemUIOverlayStyle(message: JSONObject) {
        val window = mActivity.window
        val view = window.decorView
        var flags = view.systemUiVisibility
        try {
            // You can change the navigation bar color (including translucent colors)
            // in Android, but you can't change the color of the navigation buttons until Android O.
            // LIGHT vs DARK effectively isn't supported until then.
            // Build.VERSION_CODES.O
            if (Build.VERSION.SDK_INT >= 26) {
                if (!message.isNull("systemNavigationBarIconBrightness")) {
                    val systemNavigationBarIconBrightness = message.getString("systemNavigationBarIconBrightness")
                    when (systemNavigationBarIconBrightness) {
                        "Brightness.dark" ->
                            //View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                            flags = flags or 0x10
                        "Brightness.light" -> flags = flags and 0x10.inv()
                    }
                }
                if (!message.isNull("systemNavigationBarColor")) {
                    window.navigationBarColor = message.getInt("systemNavigationBarColor")
                }
            }
            // Build.VERSION_CODES.M
            if (Build.VERSION.SDK_INT >= 23) {
                if (!message.isNull("statusBarIconBrightness")) {
                    val statusBarIconBrightness = message.getString("statusBarIconBrightness")
                    when (statusBarIconBrightness) {
                        "Brightness.dark" ->
                            // View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            flags = flags or 0x2000
                        "Brightness.light" -> flags = flags and 0x2000.inv()
                    }
                }
                if (!message.isNull("statusBarColor")) {
                    window.statusBarColor = message.getInt("statusBarColor")
                }
            }
            if (!message.isNull("systemNavigationBarDividerColor")) {
                // Not availible until Android P.
                // window.setNavigationBarDividerColor(systemNavigationBarDividerColor);
            }
            view.systemUiVisibility = flags
            mCurrentTheme = message
        } catch (err: JSONException) {
            Log.i("PlatformPlugin", err.toString())
        }

    }

    private fun popSystemNavigator() {
        mActivity.finish()
    }

    @Throws(JSONException::class)
    private fun getClipboardData(format: String?): JSONObject? {
        val clipboard = mActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.getPrimaryClip() ?: return null

        if (format == null || format == kTextPlainFormat) {
            val result = JSONObject()
            result.put("text", clip.getItemAt(0).coerceToText(mActivity))
            return result
        }

        return null
    }

    @Throws(JSONException::class)
    private fun setClipboardData(data: JSONObject) {
        val clipboard = mActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("text label?", data.getString("text"))
        clipboard.setPrimaryClip(clip)
    }

    fun onPostResume() {
        updateSystemUiOverlays()
    }
}