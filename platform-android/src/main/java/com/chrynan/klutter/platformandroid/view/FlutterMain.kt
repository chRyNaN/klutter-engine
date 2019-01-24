package com.chrynan.klutter.platformandroid.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.chrynan.klutter.platformandroid.util.PathUtils
import java.io.File
import java.io.IOException
import java.util.*


/**
 * A class to intialize the Flutter engine.
 */
object FlutterMain {

    private const val TAG = "FlutterMain"

    // Must match values in sky::shell::switches
    private const val AOT_SHARED_LIBRARY_PATH = "aot-shared-library-path"
    private const val AOT_SNAPSHOT_PATH_KEY = "aot-snapshot-path"
    private const val AOT_VM_SNAPSHOT_DATA_KEY = "vm-snapshot-data"
    private const val AOT_VM_SNAPSHOT_INSTR_KEY = "vm-snapshot-instr"
    private const val AOT_ISOLATE_SNAPSHOT_DATA_KEY = "isolate-snapshot-data"
    private const val AOT_ISOLATE_SNAPSHOT_INSTR_KEY = "isolate-snapshot-instr"
    private const val FLX_KEY = "flx"
    private const val FLUTTER_ASSETS_DIR_KEY = "flutter-assets-dir"

    // XML Attribute keys supported in AndroidManifest.xml
    val PUBLIC_AOT_AOT_SHARED_LIBRARY_PATH =
        "${FlutterMain::class.java.name}.$AOT_SHARED_LIBRARY_PATH"
    val PUBLIC_AOT_VM_SNAPSHOT_DATA_KEY =
        "${FlutterMain::class.java.name}.$AOT_VM_SNAPSHOT_DATA_KEY"
    val PUBLIC_AOT_VM_SNAPSHOT_INSTR_KEY =
        "${FlutterMain::class.java.name}.$AOT_VM_SNAPSHOT_INSTR_KEY"
    val PUBLIC_AOT_ISOLATE_SNAPSHOT_DATA_KEY =
        "${FlutterMain::class.java.name}.$AOT_ISOLATE_SNAPSHOT_DATA_KEY"
    val PUBLIC_AOT_ISOLATE_SNAPSHOT_INSTR_KEY =
        "${FlutterMain::class.java.name}.$AOT_ISOLATE_SNAPSHOT_INSTR_KEY"
    val PUBLIC_FLX_KEY =
        "${FlutterMain::class.java.name}.$FLX_KEY"
    val PUBLIC_FLUTTER_ASSETS_DIR_KEY =
        "${FlutterMain::class.java.name}.$FLUTTER_ASSETS_DIR_KEY"

    // Resource names used for components of the precompiled snapshot.
    private const val DEFAULT_AOT_SHARED_LIBRARY_PATH = "app.so"
    private const val DEFAULT_AOT_VM_SNAPSHOT_DATA = "vm_snapshot_data"
    private const val DEFAULT_AOT_VM_SNAPSHOT_INSTR = "vm_snapshot_instr"
    private const val DEFAULT_AOT_ISOLATE_SNAPSHOT_DATA = "isolate_snapshot_data"
    private const val DEFAULT_AOT_ISOLATE_SNAPSHOT_INSTR = "isolate_snapshot_instr"
    private const val DEFAULT_FLX = "app.flx"
    private const val DEFAULT_KERNEL_BLOB = "kernel_blob.bin"
    private const val DEFAULT_FLUTTER_ASSETS_DIR = "flutter_assets"

    // Assets that are shared among all Flutter apps within an APK.
    private const val SHARED_ASSET_DIR = "flutter_shared"
    private const val SHARED_ASSET_ICU_DATA = "icudtl.dat"

    // Mutable because default values can be overridden via config properties
    private var sAotSharedLibraryPath = DEFAULT_AOT_SHARED_LIBRARY_PATH
    private var sAotVmSnapshotData = DEFAULT_AOT_VM_SNAPSHOT_DATA
    private var sAotVmSnapshotInstr = DEFAULT_AOT_VM_SNAPSHOT_INSTR
    private var sAotIsolateSnapshotData = DEFAULT_AOT_ISOLATE_SNAPSHOT_DATA
    private var sAotIsolateSnapshotInstr = DEFAULT_AOT_ISOLATE_SNAPSHOT_INSTR
    private var sFlx = DEFAULT_FLX
    private var sFlutterAssetsDir = DEFAULT_FLUTTER_ASSETS_DIR

    private var sInitialized = false
    /**
     * Returns the main internal interface for the dynamic patching subsystem.
     *
     * If this is null, it means that dynamic patching is disabled in this app.
     */
    @SuppressLint("StaticFieldLeak")
    var resourceUpdater: ResourceUpdater? = null
        private set
    @SuppressLint("StaticFieldLeak")
    private var sResourceExtractor: ResourceExtractor? = null
    private var sIsPrecompiledAsBlobs = false
    private var sIsPrecompiledAsSharedLibrary = false
    private var sSettings: Settings? = null
    private var sIcuDataPath: String? = null

    val isRunningPrecompiledCode: Boolean
        get() = sIsPrecompiledAsBlobs or sIsPrecompiledAsSharedLibrary

    private fun fromFlutterAssets(filePath: String) = "$sFlutterAssetsDir${File.separator}$filePath"

    /**
     * Starts initialization of the native system.
     * @param applicationContext The Android application context.
     * @param settings Configuration settings.
     */
    @JvmOverloads
    fun startInitialization(applicationContext: Context, settings: Settings = Settings()) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException("startInitialization must be called on the main thread")
        }

        // Do not run startInitialization more than once.
        if (sSettings != null) return

        sSettings = settings

        val initStartTimestampMillis = SystemClock.uptimeMillis()

        initConfig(applicationContext)
        initAot(applicationContext)
        initResources(applicationContext)

        System.loadLibrary("flutter")

        // We record the initialization time using SystemClock because at the start of the
        // initialization we have not yet loaded the native library to call into dart_tools_api.h.
        // To get Timeline timestamp of the start of initialization we simply subtract the delta
        // from the Timeline timestamp at the current moment (the assumption is that the overhead
        // of the JNI call is negligible).
        val initTimeMillis = SystemClock.uptimeMillis() - initStartTimestampMillis

        nativeRecordStartTimestamp(initTimeMillis)
    }

    /**
     * Blocks until initialization of the native system has completed.
     * @param applicationContext The Android application context.
     * @param args Flags sent to the Flutter runtime.
     */
    fun ensureInitializationComplete(applicationContext: Context, args: Array<String>?) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw IllegalStateException("ensureInitializationComplete must be called on the main thread")

        if (sSettings == null) throw IllegalStateException("ensureInitializationComplete must be called after startInitialization")

        if (sInitialized) return

        try {
            sResourceExtractor!!.waitForCompletion()

            val shellArgs = mutableListOf("--icu-data-file-path=$sIcuDataPath")

            args?.let { shellArgs.addAll(args) }

            if (sIsPrecompiledAsSharedLibrary) {
                shellArgs.add(
                    "--" + AOT_SHARED_LIBRARY_PATH + "=" +
                            File(PathUtils.getDataDirectory(applicationContext), sAotSharedLibraryPath)
                )
            } else {
                if (sIsPrecompiledAsBlobs) {
                    shellArgs.add(
                        ("--" + AOT_SNAPSHOT_PATH_KEY + "=" +
                                PathUtils.getDataDirectory(applicationContext))
                    )
                } else {
                    shellArgs.add(("--cache-dir-path=" + PathUtils.getCacheDirectory(applicationContext)))

                    shellArgs.add(
                        ("--" + AOT_SNAPSHOT_PATH_KEY + "=" +
                                PathUtils.getDataDirectory(applicationContext) + "/" + sFlutterAssetsDir)
                    )
                }
                shellArgs.add("--$AOT_VM_SNAPSHOT_DATA_KEY=$sAotVmSnapshotData")
                shellArgs.add("--$AOT_VM_SNAPSHOT_INSTR_KEY=$sAotVmSnapshotInstr")
                shellArgs.add("--$AOT_ISOLATE_SNAPSHOT_DATA_KEY=$sAotIsolateSnapshotData")
                shellArgs.add("--$AOT_ISOLATE_SNAPSHOT_INSTR_KEY=$sAotIsolateSnapshotInstr")
            }

            if (sSettings!!.logTag != null) {
                shellArgs.add("--log-tag=" + sSettings!!.logTag!!)
            }

            val appBundlePath = findAppBundlePath(applicationContext)
            val appStoragePath = PathUtils.getFilesDir(applicationContext)
            val engineCachesPath = PathUtils.getCacheDirectory(applicationContext)

            nativeInit(
                applicationContext, shellArgs.toTypedArray(),
                appBundlePath, appStoragePath, engineCachesPath
            )

            sInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Flutter initialization failed.", e)
            throw RuntimeException(e)
        }
    }

    fun onResume(context: Context) {
        if (resourceUpdater?.downloadMode === DownloadMode.ON_RESUME) {
            resourceUpdater?.startUpdateDownloadOnce()
        }
    }

    private fun findAppBundlePath(applicationContext: Context): String? {
        val dataDirectory = PathUtils.getDataDirectory(applicationContext)
        val appBundle = File(dataDirectory, sFlutterAssetsDir)

        return if (appBundle.exists()) appBundle.path else null
    }

    /**
     * Returns the file name for the given asset.
     * The returned file name can be used to access the asset in the APK
     * through the [android.content.res.AssetManager] API.
     *
     * @param asset the name of the asset. The name can be hierarchical
     * @return      the filename to be used with [android.content.res.AssetManager]
     */
    fun getLookupKeyForAsset(asset: String) = fromFlutterAssets(asset)

    /**
     * Returns the file name for the given asset which originates from the
     * specified packageName. The returned file name can be used to access
     * the asset in the APK through the [android.content.res.AssetManager] API.
     *
     * @param asset       the name of the asset. The name can be hierarchical
     * @param packageName the name of the package from which the asset originates
     * @return            the file name to be used with [android.content.res.AssetManager]
     */
    fun getLookupKeyForAsset(asset: String, packageName: String) =
        getLookupKeyForAsset("packages${File.separator}$packageName${File.separator}$asset")

    private external fun nativeInit(
        context: Context,
        args: Array<String>,
        bundlePath: String?,
        appStoragePath: String,
        engineCachesPath: String
    )

    private external fun nativeRecordStartTimestamp(initTimeMillis: Long)

    /**
     * Initialize our Flutter config values by obtaining them from the
     * manifest XML file, falling back to default values.
     */
    private fun initConfig(applicationContext: Context) {
        try {
            val metadata = applicationContext.packageManager.getApplicationInfo(
                applicationContext.packageName, PackageManager.GET_META_DATA
            ).metaData
            if (metadata != null) {
                sAotSharedLibraryPath =
                        metadata.getString(PUBLIC_AOT_AOT_SHARED_LIBRARY_PATH, DEFAULT_AOT_SHARED_LIBRARY_PATH)
                sAotVmSnapshotData = metadata.getString(PUBLIC_AOT_VM_SNAPSHOT_DATA_KEY, DEFAULT_AOT_VM_SNAPSHOT_DATA)
                sAotVmSnapshotInstr =
                        metadata.getString(PUBLIC_AOT_VM_SNAPSHOT_INSTR_KEY, DEFAULT_AOT_VM_SNAPSHOT_INSTR)
                sAotIsolateSnapshotData =
                        metadata.getString(PUBLIC_AOT_ISOLATE_SNAPSHOT_DATA_KEY, DEFAULT_AOT_ISOLATE_SNAPSHOT_DATA)
                sAotIsolateSnapshotInstr =
                        metadata.getString(PUBLIC_AOT_ISOLATE_SNAPSHOT_INSTR_KEY, DEFAULT_AOT_ISOLATE_SNAPSHOT_INSTR)
                sFlx = metadata.getString(PUBLIC_FLX_KEY, DEFAULT_FLX)
                sFlutterAssetsDir = metadata.getString(PUBLIC_FLUTTER_ASSETS_DIR_KEY, DEFAULT_FLUTTER_ASSETS_DIR)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException(e)
        }

    }

    private fun initResources(applicationContext: Context) {
        ResourceCleaner(applicationContext).start()

        var metaData: Bundle? = null
        try {
            metaData = applicationContext.packageManager.getApplicationInfo(
                applicationContext.packageName, PackageManager.GET_META_DATA
            ).metaData

        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Unable to read application info", e)
        }

        if (metaData != null && metaData.getBoolean("DynamicPatching")) {
            resourceUpdater = ResourceUpdater(applicationContext)
            // Also checking for ON_RESUME here since it's more efficient than waiting for actual
            // onResume. Even though actual onResume is imminent when the app has just restarted,
            // it's better to start downloading now, in parallel with the rest of initialization,
            // and avoid a second application restart a bit later when actual onResume happens.
            if ((resourceUpdater?.downloadMode === DownloadMode.ON_RESTART || resourceUpdater?.downloadMode === DownloadMode.ON_RESUME)) {
                resourceUpdater?.startUpdateDownloadOnce()

                if (resourceUpdater?.installMode === InstallMode.IMMEDIATE) {
                    resourceUpdater?.waitForDownloadCompletion()
                }
            }
        }

        sResourceExtractor = ResourceExtractor(applicationContext)

        val icuAssetPath = SHARED_ASSET_DIR + File.separator + SHARED_ASSET_ICU_DATA
        sResourceExtractor!!.addResource(icuAssetPath)
        sIcuDataPath = PathUtils.getDataDirectory(applicationContext) + File.separator + icuAssetPath

        sResourceExtractor!!
            .addResource(fromFlutterAssets(sFlx))
            .addResource(fromFlutterAssets(sAotVmSnapshotData))
            .addResource(fromFlutterAssets(sAotVmSnapshotInstr))
            .addResource(fromFlutterAssets(sAotIsolateSnapshotData))
            .addResource(fromFlutterAssets(sAotIsolateSnapshotInstr))
            .addResource(fromFlutterAssets(DEFAULT_KERNEL_BLOB))

        if (sIsPrecompiledAsSharedLibrary) {
            sResourceExtractor!!
                .addResource(sAotSharedLibraryPath)

        } else {
            sResourceExtractor!!
                .addResource(sAotVmSnapshotData)
                .addResource(sAotVmSnapshotInstr)
                .addResource(sAotIsolateSnapshotData)
                .addResource(sAotIsolateSnapshotInstr)
        }

        sResourceExtractor!!.start()
    }

    /**
     * Returns a list of the file names at the root of the application's asset
     * path.
     */
    private fun listAssets(applicationContext: Context, path: String): Set<String> {
        val manager = applicationContext.resources.assets

        try {
            return ImmutableSetBuilder.newInstance<String>()
                .add(*manager.list(path))
                .build()
        } catch (e: IOException) {
            Log.e(TAG, "Unable to list assets", e)
            throw RuntimeException(e)
        }
    }

    private fun initAot(applicationContext: Context) {
        val assets = listAssets(applicationContext, "")

        sIsPrecompiledAsBlobs = assets.containsAll(
            Arrays.asList(
                sAotVmSnapshotData,
                sAotVmSnapshotInstr,
                sAotIsolateSnapshotData,
                sAotIsolateSnapshotInstr
            )
        )

        sIsPrecompiledAsSharedLibrary = assets.contains(sAotSharedLibraryPath)

        if (sIsPrecompiledAsBlobs && sIsPrecompiledAsSharedLibrary) {
            throw RuntimeException(
                "Found precompiled app as shared library and as Dart VM snapshots."
            )
        }
    }
}