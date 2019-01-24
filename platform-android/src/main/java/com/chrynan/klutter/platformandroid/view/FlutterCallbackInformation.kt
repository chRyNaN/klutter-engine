package com.chrynan.klutter.platformandroid.view

/**
 * A class representing information for a callback registered using
 * `PluginUtilities` from `dart:ui`.
 */
class FlutterCallbackInformation private constructor(
    val callbackName: String,
    val callbackClassName: String,
    val callbackLibraryPath: String
) {

    companion object {

        /**
         * Get callback information for a given handle.
         * @param handle the handle for the callback, generated by
         * `PluginUtilities.getCallbackHandle` in `dart:ui`.
         * @return an instance of FlutterCallbackInformation for the provided handle.
         */
        fun lookupCallbackInformation(handle: Long): FlutterCallbackInformation = nativeLookupCallbackInformation(handle)

        private external fun nativeLookupCallbackInformation(handle: Long): FlutterCallbackInformation
    }
}