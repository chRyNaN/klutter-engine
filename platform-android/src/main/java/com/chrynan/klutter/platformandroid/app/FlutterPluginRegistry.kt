package com.chrynan.klutter.platformandroid.app

import android.content.Intent
import android.app.Activity
import android.content.Context
import com.sun.javafx.scene.control.skin.FXVK.detach


class FlutterPluginRegistry(
    private val mNativeView: FlutterNativeView,
    private val mAppContext: Context
) : PluginRegistry,
    PluginRegistry.RequestPermissionsResultListener,
    PluginRegistry.ActivityResultListener,
    PluginRegistry.NewIntentListener,
    PluginRegistry.UserLeaveHintListener,
    PluginRegistry.ViewDestroyListener {

    companion object {

        private const val TAG = "FlutterPluginRegistry"
    }

    private var mActivity: Activity? = null
    private var mFlutterView: FlutterView? = null

    private val mPlatformViewsController: PlatformViewsController
    private val mPluginMap = LinkedHashMap(0)
    private val mRequestPermissionsResultListeners = ArrayList(0)
    private val mActivityResultListeners = ArrayList(0)
    private val mNewIntentListeners = ArrayList(0)
    private val mUserLeaveHintListeners = ArrayList(0)
    private val mViewDestroyListeners = ArrayList(0)

    init {
        mPlatformViewsController = PlatformViewsController()
    }

    fun hasPlugin(key: String): Boolean {
        return mPluginMap.containsKey(key)
    }

    fun <T> valuePublishedByPlugin(pluginKey: String): T {
        return mPluginMap.get(pluginKey)
    }

    fun registrarFor(pluginKey: String): Registrar {
        if (mPluginMap.containsKey(pluginKey)) {
            throw IllegalStateException("Plugin key $pluginKey is already in use")
        }
        mPluginMap.put(pluginKey, null)
        return FlutterRegistrar(pluginKey)
    }

    fun attach(flutterView: FlutterView, activity: Activity) {
        mFlutterView = flutterView
        mActivity = activity
        mPlatformViewsController.attach(activity, flutterView, flutterView)
    }

    fun detach() {
        mPlatformViewsController.detach()
        mPlatformViewsController.onFlutterViewDestroyed()
        mFlutterView = null
        mActivity = null
    }

    fun onPreEngineRestart() {
        mPlatformViewsController.onPreEngineRestart()
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        for (listener in mRequestPermissionsResultListeners) {
            if (listener.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
                return true
            }
        }
        return false
    }

    /*
     * Method onRequestPermissionResult(int, String[], int[]) was made
     * unavailable on 2018-02-28, following deprecation. This comment is left as
     * a temporary tombstone for reference, to be removed on 2018-03-28 (or at
     * least four weeks after release of unavailability).
     *
     * https://github.com/flutter/flutter/wiki/Changelog#typo-fixed-in-flutter-engine-android-api
     */

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Boolean {
        for (listener in mActivityResultListeners) {
            if (listener.onActivityResult(requestCode, resultCode, data)) {
                return true
            }
        }
        return false
    }

    fun onNewIntent(intent: Intent): Boolean {
        for (listener in mNewIntentListeners) {
            if (listener.onNewIntent(intent)) {
                return true
            }
        }
        return false
    }

    fun onUserLeaveHint() {
        for (listener in mUserLeaveHintListeners) {
            listener.onUserLeaveHint()
        }
    }

    fun onViewDestroy(view: FlutterNativeView): Boolean {
        var handled = false
        for (listener in mViewDestroyListeners) {
            if (listener.onViewDestroy(view)) {
                handled = true
            }
        }
        return handled
    }

    fun destroy() {
        mPlatformViewsController.onFlutterViewDestroyed()
    }

    private inner class FlutterRegistrar internal constructor(private val pluginKey: String) : Registrar {

        fun activity(): Activity? {
            return mActivity
        }

        fun context(): Context {
            return mAppContext
        }

        fun activeContext(): Context {
            return if (mActivity != null) mActivity else mAppContext
        }

        fun messenger(): BinaryMessenger {
            return mNativeView
        }

        fun textures(): TextureRegistry? {
            return mFlutterView
        }

        fun platformViewRegistry(): PlatformViewRegistry {
            return mPlatformViewsController.getRegistry()
        }

        fun view(): FlutterView? {
            return mFlutterView
        }

        fun lookupKeyForAsset(asset: String): String {
            return FlutterMain.getLookupKeyForAsset(asset)
        }

        fun lookupKeyForAsset(asset: String, packageName: String): String {
            return FlutterMain.getLookupKeyForAsset(asset, packageName)
        }

        fun publish(value: Any): Registrar {
            mPluginMap.put(pluginKey, value)
            return this
        }

        /*
        * Method addRequestPermissionResultListener(RequestPermissionResultListener)
        * was made unavailable on 2018-02-28, following deprecation.
        * This comment is left as a temporary tombstone for reference, to be removed
        * on 2018-03-28 (or at least four weeks after release of unavailability).
        *
        * https://github.com/flutter/flutter/wiki/Changelog#typo-fixed-in-flutter-engine-android-api
        */

        fun addRequestPermissionsResultListener(
            listener: RequestPermissionsResultListener
        ): Registrar {
            mRequestPermissionsResultListeners.add(listener)
            return this
        }

        fun addActivityResultListener(listener: ActivityResultListener): Registrar {
            mActivityResultListeners.add(listener)
            return this
        }

        fun addNewIntentListener(listener: NewIntentListener): Registrar {
            mNewIntentListeners.add(listener)
            return this
        }

        fun addUserLeaveHintListener(listener: UserLeaveHintListener): Registrar {
            mUserLeaveHintListeners.add(listener)
            return this
        }

        fun addViewDestroyListener(listener: ViewDestroyListener): Registrar {
            mViewDestroyListeners.add(listener)
            return this
        }
    }
}