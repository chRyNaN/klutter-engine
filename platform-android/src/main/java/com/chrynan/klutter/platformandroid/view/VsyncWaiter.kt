package com.chrynan.klutter.platformandroid.view

import android.view.Choreographer

object VsyncWaiter {

    // This estimate will be updated by FlutterView when it is attached to a Display.
    var refreshPeriodNanos = 1_000_000_000L / 60L

    // This should also be updated by FlutterView when it is attached to a Display.
    // The initial value of 0.0 indicates unkonwn refresh rate.
    var refreshRateFPS = 0.0f

    fun asyncWaitForVsync(cookie: Long) {
        Choreographer.getInstance().postFrameCallback { frameTimeNanos ->
            nativeOnVsync(
                frameTimeNanos,
                frameTimeNanos + refreshPeriodNanos,
                cookie
            )
        }
    }

    private external fun nativeOnVsync(frameTimeNanos: Long, frameTargetTimeNanos: Long, cookie: Long)
}