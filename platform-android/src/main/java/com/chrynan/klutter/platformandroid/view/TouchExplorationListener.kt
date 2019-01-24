package com.chrynan.klutter.platformandroid.view

import android.view.accessibility.AccessibilityManager

class TouchExplorationListener(
    private val flutterNativeView: FlutterNativeView?,
    private var mAccessibilityFeatureFlags: Int
) : AccessibilityManager.TouchExplorationStateChangeListener {

    override fun onTouchExplorationStateChanged(enabled: Boolean) {
        if (enabled) {
            mTouchExplorationEnabled = true
            ensureAccessibilityEnabled()

            mAccessibilityFeatureFlags = mAccessibilityFeatureFlags or AccessibilityFeature.ACCESSIBLE_NAVIGATION.value

            flutterNativeView?.flutterJNI?.setAccessibilityFeatures(mAccessibilityFeatureFlags)
        } else {
            mTouchExplorationEnabled = false

            if (mAccessibilityNodeProvider != null) {
                mAccessibilityNodeProvider!!.handleTouchExplorationExit()
            }

            mAccessibilityFeatureFlags = mAccessibilityFeatureFlags and
                    AccessibilityFeature.ACCESSIBLE_NAVIGATION.value.inv()

            flutterNativeView?.flutterJNI?.setAccessibilityFeatures(mAccessibilityFeatureFlags)
        }

        resetWillNotDraw()
    }
}