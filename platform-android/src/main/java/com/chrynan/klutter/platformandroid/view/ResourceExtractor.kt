package com.chrynan.klutter.platformandroid.view

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import com.chrynan.klutter.platformandroid.util.PathUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.zip.ZipFile

/**
 * A class to initialize the native code.
 */
internal class ResourceExtractor(private val context: Context) {

    companion object {

        private const val TAG = "ResourceExtractor"
        private const val TIMESTAMP_PREFIX = "res_timestamp-"

        private const val BUFFER_SIZE = 16 * 1024

        @Suppress("DEPRECATION")
        fun getVersionCode(packageInfo: PackageInfo): Long =
            if (Build.VERSION.SDK_INT >= 28) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
    }

    private val mResources: HashSet<String> = HashSet()
    private var mExtractTask: ExtractTask? = null

    fun addResource(resource: String): ResourceExtractor {
        mResources.add(resource)
        return this
    }

    fun addResources(resources: Collection<String>): ResourceExtractor {
        mResources.addAll(resources)
        return this
    }

    fun start(): ResourceExtractor {
        assert(mExtractTask == null)

        mExtractTask = ExtractTask(
            tag = ResourceExtractor.TAG,
            checkTimestamp = ::checkTimestamp,
            deleteFiles = ::deleteFiles,
            context = context,
            extractUpdate = ::extractUpdate,
            extractAPK = ::extractAPK
        )

        mExtractTask!!.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)

        return this
    }

    fun waitForCompletion() {
        if (mExtractTask == null) {
            return
        }

        try {
            mExtractTask!!.get()
        } catch (e: CancellationException) {
            deleteFiles()
        } catch (e: ExecutionException) {
            deleteFiles()
        } catch (e: InterruptedException) {
            deleteFiles()
        }
    }

    private fun getExistingTimestamps(dataDir: File) =
        dataDir.list { _, name -> name.startsWith(TIMESTAMP_PREFIX) }

    private fun deleteFiles() {
        val dataDir = File(PathUtils.getDataDirectory(context))
        for (resource in mResources) {
            val file = File(dataDir, resource)
            if (file.exists()) {
                file.delete()
            }
        }
        val existingTimestamps = getExistingTimestamps(dataDir) ?: return
        for (timestamp in existingTimestamps) {
            File(dataDir, timestamp).delete()
        }
    }

    /// Returns true if successfully unpacked APK resources,
    /// otherwise deletes all resources and returns false.
    private fun extractAPK(dataDir: File): Boolean {
        val manager = context.resources.assets

        var buffer: ByteArray? = null
        for (asset in mResources) {
            try {
                val output = File(dataDir, asset)
                if (output.exists()) {
                    continue
                }
                if (output.parentFile != null) {
                    output.parentFile.mkdirs()
                }

                manager.open(asset).use { inputStream ->
                    FileOutputStream(output).use { os ->
                        if (buffer == null) {
                            buffer = ByteArray(BUFFER_SIZE)
                        }

                        var count: Int

                        while (true) {
                            count = inputStream.read(buffer, 0, BUFFER_SIZE)

                            if (count == -1) break

                            os.write(buffer, 0, count)
                        }

                        os.flush()
                        Log.i(TAG, "Extracted baseline resource $asset")
                    }
                }

            } catch (fnfe: FileNotFoundException) {
                continue
            } catch (ioe: IOException) {
                Log.w(TAG, "Exception unpacking resources: " + ioe.message)
                deleteFiles()
                return false
            }
        }

        return true
    }

    /// Returns true if successfully unpacked update resources or if there is no update,
    /// otherwise deletes all resources and returns false.
    private fun extractUpdate(dataDir: File): Boolean {
        val resourceUpdater = FlutterMain.resourceUpdater ?: return true

        val updateFile = resourceUpdater.installedPatch
        if (!updateFile.exists()) {
            return true
        }

        val manifest = resourceUpdater.readManifest(updateFile)
        if (!resourceUpdater.validateManifest(manifest)) {
            // Obsolete patch file, nothing to install.
            return true
        }

        val zipFile: ZipFile
        try {
            zipFile = ZipFile(updateFile)

        } catch (e: IOException) {
            Log.w(TAG, "Exception unpacking resources: " + e.message)
            deleteFiles()
            return false
        }

        var buffer: ByteArray? = null
        for (asset in mResources) {
            val entry = zipFile.getEntry(asset) ?: continue

            val output = File(dataDir, asset)
            if (output.exists()) {
                continue
            }
            if (output.parentFile != null) {
                output.parentFile.mkdirs()
            }

            try {
                zipFile.getInputStream(entry).use { `is` ->
                    FileOutputStream(output).use { os ->
                        if (buffer == null) {
                            buffer = ByteArray(BUFFER_SIZE)
                        }

                        var count: Int

                        while (true) {
                            count = `is`.read(buffer, 0, BUFFER_SIZE)

                            if (count == -1) break

                            os.write(buffer, 0, count)
                        }

                        os.flush()
                        Log.i(TAG, "Extracted override resource $asset")

                    }
                }
            } catch (fnfe: FileNotFoundException) {
                continue
            } catch (ioe: IOException) {
                Log.w(TAG, "Exception unpacking resources: " + ioe.message)
                deleteFiles()
                return false
            }
        }

        return true
    }

    // Returns null if extracted resources are found and match the current APK version
    // and update version if any, otherwise returns the current APK and update version.
    private fun checkTimestamp(dataDir: File): String? {
        val packageManager = context.packageManager
        val packageInfo: PackageInfo?

        try {
            packageInfo = packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return TIMESTAMP_PREFIX
        }

        if (packageInfo == null) {
            return TIMESTAMP_PREFIX
        }

        var expectedTimestamp = TIMESTAMP_PREFIX + getVersionCode(packageInfo) + "-" + packageInfo.lastUpdateTime

        val resourceUpdater = FlutterMain.resourceUpdater
        if (resourceUpdater != null) {
            val patchFile = resourceUpdater.installedPatch
            val manifest = resourceUpdater.readManifest(patchFile)
            if (resourceUpdater.validateManifest(manifest)) {
                val patchNumber = manifest?.optString("patchNumber", null)
                expectedTimestamp += if (patchNumber != null) {
                    "-" + patchNumber + "-" + patchFile.lastModified()
                } else {
                    "-" + patchFile.lastModified()
                }
            }
        }

        val existingTimestamps = getExistingTimestamps(dataDir)

        if (existingTimestamps == null) {
            Log.i(TAG, "No extracted resources found")
            return expectedTimestamp
        }

        if (existingTimestamps.size == 1) {
            Log.i(TAG, "Found extracted resources " + existingTimestamps[0])
        }

        if (existingTimestamps.size != 1 || expectedTimestamp != existingTimestamps[0]) {
            Log.i(TAG, "Resource version mismatch $expectedTimestamp")
            return expectedTimestamp
        }

        return null
    }
}