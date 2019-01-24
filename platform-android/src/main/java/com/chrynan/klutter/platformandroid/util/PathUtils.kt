package com.chrynan.klutter.platformandroid.util

import android.content.Context

object PathUtils {

    fun getFilesDir(applicationContext: Context) = applicationContext.filesDir.path

    fun getDataDirectory(applicationContext: Context) =
        applicationContext.getDir("flutter", Context.MODE_PRIVATE).path

    fun getCacheDirectory(applicationContext: Context) = applicationContext.cacheDir.path
}

val ApplicationContext.getFilesDir: String
    get() = applicationContext.applicationContext.filesDir.path

val ApplicationContext.getDataDirectory: String
    get() = applicationContext.applicationContext.getDir("flutter", Context.MODE_PRIVATE).path

val ApplicationContext.getCacheDirectory: String
    get() = applicationContext.applicationContext.cacheDir.path