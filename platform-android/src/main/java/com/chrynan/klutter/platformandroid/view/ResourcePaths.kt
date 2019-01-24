package com.chrynan.klutter.platformandroid.view

import android.content.Context
import java.io.File
import java.io.IOException

internal object ResourcePaths {

    // The filename prefix used by Chromium temporary file APIs.
    const val TEMPORARY_RESOURCE_PREFIX = ".org.chromium.Chromium."

    // Return a temporary file that will be cleaned up by the ResourceCleaner.
    @Throws(IOException::class)
    fun createTempFile(context: Context, suffix: String) =
        File.createTempFile(
            TEMPORARY_RESOURCE_PREFIX, "_$suffix",
            context.cacheDir
        )
}