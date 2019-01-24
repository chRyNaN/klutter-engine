package com.chrynan.klutter.platformandroid.view

import android.content.Context
import android.os.AsyncTask
import android.os.Handler
import android.util.Log
import java.io.File

/**
 * A class to clean up orphaned resource directories after unclean shutdowns.
 */
internal class ResourceCleaner(private val mContext: Context) {

    companion object {

        private const val TAG = "ResourceCleaner"
        private const val DELAY_MS = 5000L
    }

    fun start() {
        val cacheDir = mContext.cacheDir ?: return

        val task = CleanTask(cacheDir.listFiles { dir, name ->
            name.startsWith(ResourcePaths.TEMPORARY_RESOURCE_PREFIX)
        })

        if (!task.hasFilesToDelete()) return

        Handler().postDelayed({ task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR) }, DELAY_MS)
    }

    private class CleanTask(private val mFilesToDelete: Array<File>) : AsyncTask<Void, Void, Void>() {

        internal fun hasFilesToDelete() = mFilesToDelete.isNotEmpty()

        override fun doInBackground(vararg unused: Void): Void? {
            Log.i(TAG, "Cleaning ${mFilesToDelete.size} resources.")

            for (file in mFilesToDelete) {
                if (file.exists()) {
                    deleteRecursively(file)
                }
            }

            return null
        }

        private fun deleteRecursively(parent: File) {
            if (parent.isDirectory) {
                for (child in parent.listFiles()) {
                    deleteRecursively(child)
                }
            }
            parent.delete()
        }
    }
}