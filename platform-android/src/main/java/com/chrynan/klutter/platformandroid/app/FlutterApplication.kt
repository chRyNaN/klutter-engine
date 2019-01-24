package com.chrynan.klutter.platformandroid.app

import android.app.Activity
import android.app.Application

/**
 * Flutter implementation of [android.app.Application], managing
 * application-level global initializations.
 */
class FlutterApplication : Application() {

    var currentActivity: Activity? = null

    @CallSuper
    fun onCreate() {
        super.onCreate()

        FlutterMain.startInitialization(this)
    }
}