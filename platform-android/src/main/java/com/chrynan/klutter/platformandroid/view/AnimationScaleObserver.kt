package com.chrynan.klutter.platformandroid.view

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler

// Listens to the global TRANSITION_ANIMATION_SCALE property and notifies us so
// that we can disable animations in Flutter.
class AnimationScaleObserver(
    handler: Handler,
    private val flutterNativeView: FlutterNativeView?,
    private val context: Context,
    private var mAccessibilityFeatureFlags: Int
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean) = this.onChange(selfChange, null)

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        val value = Settings.Global.getString(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE
        )

        mAccessibilityFeatureFlags = if (value != null && value == "0") {
            mAccessibilityFeatureFlags or AccessibilityFeature.DISABLE_ANIMATIONS.value
        } else {
            mAccessibilityFeatureFlags and AccessibilityFeature.DISABLE_ANIMATIONS.value.inv()
        }

        flutterNativeView!!.flutterJNI.setAccessibilityFeatures(mAccessibilityFeatureFlags)
    }
}