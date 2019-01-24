package com.chrynan.klutter.platformandroid.plugin.common

/**
 * Thrown to indicate that a Flutter method invocation failed on the Flutter side.
 */
class FlutterException internal constructor(
    val code: String,
    message: String,
    val details: Any
) : RuntimeException(message)