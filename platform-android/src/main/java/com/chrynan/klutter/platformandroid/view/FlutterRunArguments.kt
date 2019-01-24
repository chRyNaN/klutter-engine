package com.chrynan.klutter.platformandroid.view

/**
 * A class containing arguments for entering a FlutterNativeView's isolate for
 * the first time.
 */
data class FlutterRunArguments(
    var bundlePaths: List<String> = emptyList(),
    var bundlePath: String? = null,
    var entrypoint: String? = null,
    var libraryPath: String? = null,
    var defaultPath: String? = null
)