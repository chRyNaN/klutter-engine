package com.chrynan.klutter.platformandroid.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.ViewSwitcher

/**
 * Base class for activities that use Flutter who also require the use of the
 * Android v4 Support library's [FragmentActivity]. Applications that
 * don't have this need will likely want to use [FlutterActivity] instead.
 *
 *
 * **Important!** Flutter does not bundle the necessary Android
 * v4 Support library classes for this class to work at runtime. It is the
 * responsibility of the app developer using this class to ensure that they
 * link against the v4 support library .jar file when creating their app to
 * ensure that [FragmentActivity] is available at runtime.
 *
 * @see [https://developer.android.com/topic/libraries/support-library/setup.html](https://developer.android.com/topic/libraries/support-library/setup.html)
 */
class FlutterFragmentActivity : FragmentActivity(),
    FlutterView.Provider,
    PluginRegistry,
    ViewSwitcher.ViewFactory {

    private val delegate = FlutterActivityDelegate(this, this)

    // These aliases ensure that the methods we forward to the delegate adhere
    // to relevant interfaces versus just existing in FlutterActivityDelegate.
    private val eventDelegate = delegate
    private val viewProvider = delegate
    private val pluginRegistry = delegate

    /**
     * Returns the Flutter view used by this activity; will be null before
     * [.onCreate] is called.
     */
    val flutterView: FlutterView
        get() = viewProvider.getFlutterView()

    /**
     * Hook for subclasses to customize the creation of the
     * `FlutterView`.
     *
     *
     * The default implementation returns `null`, which will cause the
     * activity to use a newly instantiated full-screen view.
     */
    fun createFlutterView(context: Context): FlutterView? {
        return null
    }

    fun createFlutterNativeView(): FlutterNativeView? {
        return null
    }

    fun retainFlutterNativeView(): Boolean {
        return false
    }

    fun hasPlugin(key: String): Boolean {
        return pluginRegistry.hasPlugin(key)
    }

    fun <T> valuePublishedByPlugin(pluginKey: String): T {
        return pluginRegistry.valuePublishedByPlugin(pluginKey)
    }

    fun registrarFor(pluginKey: String): Registrar {
        return pluginRegistry.registrarFor(pluginKey)
    }

    protected fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        eventDelegate.onCreate(savedInstanceState)
    }

    protected fun onDestroy() {
        eventDelegate.onDestroy()
        super.onDestroy()
    }

    fun onBackPressed() {
        if (!eventDelegate.onBackPressed()) {
            super.onBackPressed()
        }
    }

    protected fun onStart() {
        super.onStart()
        eventDelegate.onStart()
    }

    protected fun onStop() {
        eventDelegate.onStop()
        super.onStop()
    }

    protected fun onPause() {
        super.onPause()
        eventDelegate.onPause()
    }

    protected fun onPostResume() {
        super.onPostResume()
        eventDelegate.onPostResume()
    }

    // @Override - added in API level 23
    fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        eventDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    protected fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (!eventDelegate.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    protected fun onNewIntent(intent: Intent) {
        eventDelegate.onNewIntent(intent)
    }

    fun onUserLeaveHint() {
        eventDelegate.onUserLeaveHint()
    }

    fun onTrimMemory(level: Int) {
        eventDelegate.onTrimMemory(level)
    }

    fun onLowMemory() {
        eventDelegate.onLowMemory()
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        eventDelegate.onConfigurationChanged(newConfig)
    }
}