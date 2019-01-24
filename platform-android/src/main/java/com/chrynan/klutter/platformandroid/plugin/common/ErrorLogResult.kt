package com.chrynan.klutter.platformandroid.plugin.common

import android.util.Log
import android.util.Log.WARN
import org.jetbrains.annotations.Nullable

/**
 * An implementation of [MethodChannel.Result] that writes error results
 * to the Android log.
 */
class ErrorLogResult @JvmOverloads constructor(
    private val tag: String,
    private val level: Int = Log.WARN
) : MethodChannel.Result {

    fun success(result: Any?) {}

    fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
        val details = if (errorDetails != null) " details: $errorDetails" else ""
        Log.println(level, tag, errorMessage + details)
    }

    fun notImplemented() {
        Log.println(level, tag, "method not implemented")
    }
}