package com.chrynan.klutter.platformandroid.view

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import android.util.Log
import com.chrynan.klutter.platformandroid.util.PathUtils
import java.io.File
import java.io.IOException

@SuppressLint("StaticFieldLeak")
class ExtractTask(
    private val tag: String,
    private val context: Context,
    private val checkTimestamp: (File) -> String?,
    private val deleteFiles: () -> Unit,
    private val extractUpdate: (File) -> Boolean,
    private val extractAPK: (File) -> Boolean
) : AsyncTask<Void, Void, Void>() {

    override fun doInBackground(vararg unused: Void): Void? {
        val dataDir = File(PathUtils.getDataDirectory(context))

        val resourceUpdater = FlutterMain.resourceUpdater

        resourceUpdater?.installationLock?.lock()

        try {
            if (resourceUpdater != null) {
                val updateFile = resourceUpdater.downloadedPatch
                val activeFile = resourceUpdater.installedPatch

                if (updateFile.exists()) {
                    val manifest = resourceUpdater.readManifest(updateFile)
                    if (resourceUpdater.validateManifest(manifest)) {
                        // Graduate patch file as active for asset manager.
                        if (activeFile.exists() && !activeFile.delete()) {
                            Log.w(tag, "Could not delete file $activeFile")
                            return null
                        }
                        if (!updateFile.renameTo(activeFile)) {
                            Log.w(tag, "Could not create file $activeFile")
                            return null
                        }
                    }
                }
            }

            val timestamp = checkTimestamp(dataDir) ?: return null

            deleteFiles()

            if (!extractUpdate(dataDir)) return null

            if (!extractAPK(dataDir)) return null

            try {
                File(dataDir, timestamp).createNewFile()
            } catch (e: IOException) {
                Log.w(tag, "Failed to write resource timestamp")
            }

            return null
        } finally {
            resourceUpdater?.installationLock?.unlock()
        }
    }
}