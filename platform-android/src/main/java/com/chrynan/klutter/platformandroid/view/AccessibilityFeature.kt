package com.chrynan.klutter.platformandroid.view

/// Must match the enum defined in window.dart.
enum class AccessibilityFeature(internal val value: Int) {

    ACCESSIBLE_NAVIGATION(1 shl 0),
    INVERT_COLORS(1 shl 1), // NOT SUPPORTED
    DISABLE_ANIMATIONS(1 shl 2)
}