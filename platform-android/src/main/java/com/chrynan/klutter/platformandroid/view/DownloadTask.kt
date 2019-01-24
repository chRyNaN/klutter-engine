package com.chrynan.klutter.platformandroid.view

import android.os.AsyncTask
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.locks.Lock

class DownloadTask(
    private val tag: String,
    private val buildUpdateDownloadURL: () -> String?,
    private val installedPatch: File,
    private val downloadedPatch: File,
    private val installationLock: Lock
) : AsyncTask<String, String, Void>() {

    override fun doInBackground(vararg unused: String): Void? {
        try {
            val unresolvedURL = URL(buildUpdateDownloadURL())

            // Download to transient file to avoid extracting incomplete download.
            val localFile = File(installedPatch.path + ".download")

            val startMillis = Date().time
            Log.i(tag, "Checking for updates at $unresolvedURL")

            val connection = unresolvedURL.openConnection() as HttpURLConnection

            val lastDownloadTime = Math.max(
                downloadedPatch.lastModified(),
                installedPatch.lastModified()
            )

            if (lastDownloadTime != 0L) {
                Log.i(tag, "Active update timestamp $lastDownloadTime")
                connection.ifModifiedSince = lastDownloadTime
            }

            val resolvedURL = connection.getURL()
            Log.i(tag, "Resolved update URL $resolvedURL")

            val responseCode = connection.getResponseCode()
            Log.i(tag, "HTTP response code $responseCode")

            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.i(tag, "Latest update not found on server")
                return null
            }

            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                Log.i(tag, "Already have latest update")
                return null
            }

            connection.inputStream.use { input ->
                Log.i(tag, "Downloading update $unresolvedURL")
                FileOutputStream(localFile).use { output ->
                    var count: Int
                    val data = ByteArray(1024)

                    while (true) {
                        count = input.read(data)

                        if (count == -1) break

                        output.write(data, 0, count)
                    }

                    val totalMillis = Date().time - startMillis

                    Log.i(tag, "Update downloaded in " + totalMillis.toDouble() / 100.0 / 10.0 + "s")
                }
            }

            // Wait renaming the file if extraction is in progress.
            installationLock.lock()

            try {
                val updateFile = downloadedPatch

                // Graduate downloaded file as ready for installation.
                if (updateFile.exists() && !updateFile.delete()) {
                    Log.w(tag, "Could not delete file $updateFile")
                    return null
                }
                if (!localFile.renameTo(updateFile)) {
                    Log.w(tag, "Could not create file $updateFile")
                    return null
                }

                return null

            } finally {
                installationLock.unlock()
            }

        } catch (e: IOException) {
            Log.w(tag, "Could not download update " + e.message)
            return null
        }
    }
}