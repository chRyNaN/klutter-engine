package com.chrynan.klutter.platformandroid.view

import android.content.Context
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.CRC32
import java.util.zip.ZipFile

class ResourceUpdater(private val context: Context) {

    companion object {

        private const val TAG = "ResourceUpdater"

        private const val BUFFER_SIZE = 16 * 1024
    }

    /// Lock that prevents replacement of the install file by the downloader
    /// while this file is being extracted, since these can happen in parallel.
    val installationLock: Lock = ReentrantLock()

    // Patch file that's fully installed and is ready to serve assets.
    // This file represents the final stage in the installation process.
    val installedPatch: File
        get() = File("${context.filesDir}/patch.zip")

    // Patch file that's finished downloading and is ready to be installed.
    // This is a separate file in order to prevent serving assets from patch
    // that failed installing for any reason, such as mismatched APK version.
    val downloadedPatch: File
        get() = File("${installedPatch.path}.install")

    internal val downloadMode: DownloadMode
        get() {
            val metaData: Bundle?
            try {
                metaData = context.packageManager.getApplicationInfo(
                    context.packageName, PackageManager.GET_META_DATA
                ).metaData

            } catch (e: PackageManager.NameNotFoundException) {
                throw RuntimeException(e)
            }

            if (metaData == null) {
                return DownloadMode.ON_RESTART
            }

            val patchDownloadMode = metaData.getString("PatchDownloadMode") ?: return DownloadMode.ON_RESTART

            return try {
                DownloadMode.valueOf(patchDownloadMode)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid PatchDownloadMode $patchDownloadMode")
                DownloadMode.ON_RESTART
            }
        }

    internal val installMode: InstallMode
        get() {
            val metaData: Bundle?
            try {
                metaData = context.packageManager.getApplicationInfo(
                    context.packageName, PackageManager.GET_META_DATA
                ).metaData

            } catch (e: PackageManager.NameNotFoundException) {
                throw RuntimeException(e)
            }

            if (metaData == null) {
                return InstallMode.ON_NEXT_RESTART
            }

            val patchInstallMode = metaData.getString("PatchInstallMode") ?: return InstallMode.ON_NEXT_RESTART

            return try {
                InstallMode.valueOf(patchInstallMode)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid PatchInstallMode $patchInstallMode")
                InstallMode.ON_NEXT_RESTART
            }
        }

    private var downloadTask: DownloadTask? = null

    private val apkVersion: String?
        get() =
            try {
                val packageManager = context.packageManager
                val packageInfo = packageManager.getPackageInfo(context.packageName, 0)

                if (packageInfo == null) null else "${ResourceExtractor.getVersionCode(packageInfo)}"
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

    /// Returns manifest JSON from ZIP file, or null if not found.
    fun readManifest(updateFile: File): JSONObject? {
        if (!updateFile.exists()) {
            return null
        }

        try {
            val zipFile = ZipFile(updateFile)
            val entry = zipFile.getEntry("manifest.json")
            if (entry == null) {
                Log.w(TAG, "Invalid update file: $updateFile")
                return null
            }

            // Read and parse the entire JSON file as single operation.
            val scanner = Scanner(zipFile.getInputStream(entry))
            return JSONObject(scanner.useDelimiter("\\A").next())

        } catch (e: IOException) {
            Log.w(TAG, "Invalid update file: $e")
            return null
        } catch (e: JSONException) {
            Log.w(TAG, "Invalid update file: $e")
            return null
        }
    }

    /// Returns true if the patch file was indeed built for this APK.
    fun validateManifest(manifest: JSONObject?): Boolean {
        if (manifest == null) {
            return false
        }

        val buildNumber = manifest.optString("buildNumber", null)
        if (buildNumber == null) {
            Log.w(TAG, "Invalid update manifest: missing buildNumber")
            return false
        }

        if (buildNumber != apkVersion) {
            Log.w(TAG, "Outdated update file for build " + apkVersion!!)
            return false
        }

        val baselineChecksum = manifest.optString("baselineChecksum", null)
        if (baselineChecksum == null) {
            Log.w(TAG, "Invalid update manifest: missing baselineChecksum")
            return false
        }

        val manager = context.resources.assets

        try {
            manager.open("flutter_assets/isolate_snapshot_data").use { `is` ->
                val checksum = CRC32()

                var count: Int
                val buffer = ByteArray(BUFFER_SIZE)

                while (true) {
                    count = `is`.read(buffer, 0, BUFFER_SIZE)

                    if (count == -1) break

                    checksum.update(buffer, 0, count)
                }

                if (baselineChecksum != checksum.getValue().toString()) {
                    Log.w(TAG, "Mismatched update file for APK")
                    return false
                }

                return true

            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read APK: $e")
            return false
        }
    }

    internal fun startUpdateDownloadOnce() {
        if (downloadTask != null) return

        downloadTask = DownloadTask(
            tag = ResourceUpdater.TAG,
            buildUpdateDownloadURL = ::buildUpdateDownloadURL,
            installedPatch = installedPatch,
            installationLock = installationLock,
            downloadedPatch = downloadedPatch
        ).apply {
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    internal fun waitForDownloadCompletion() {
        if (downloadTask == null) {
            return
        }

        try {
            downloadTask!!.get()
            downloadTask = null
        } catch (e: CancellationException) {
            Log.w(TAG, "Download cancelled: " + e.message)
        } catch (e: ExecutionException) {
            Log.w(TAG, "Download exception: " + e.message)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Download interrupted: " + e.message)
        }
    }

    private fun buildUpdateDownloadURL(): String? {
        val metaData: Bundle? =
            try {
                context.packageManager.getApplicationInfo(
                    context.packageName, PackageManager.GET_META_DATA
                ).metaData
            } catch (e: PackageManager.NameNotFoundException) {
                throw RuntimeException(e)
            }

        if (metaData?.getString("PatchServerURL") == null) return null

        val uri: URI =
            try {
                URI(metaData.getString("PatchServerURL") + "/" + apkVersion + ".zip")

            } catch (e: URISyntaxException) {
                Log.w(TAG, "Invalid AndroidManifest.xml PatchServerURL: " + e.message)
                return null
            }

        return uri.normalize().toString()
    }
}