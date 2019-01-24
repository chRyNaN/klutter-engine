package com.chrynan.klutter.platformandroid.app

import android.content.Intent
import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.content.res.Configuration

/**
 * Base class for activities that use Flutter.
 */
class FlutterActivity : Activity(),
    FlutterView.Provider,
    PluginRegistry,
    ViewFactory {

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

    /**
     * Hook for subclasses to customize the creation of the
     * `FlutterNativeView`.
     *
     *
     * The default implementation returns `null`, which will cause the
     * activity to use a newly instantiated native view object.
     */
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventDelegate.onCreate(savedInstanceState!!)
    }

    override fun onStart() {
        super.onStart()
        eventDelegate.onStart()
    }

    override fun onResume() {
        super.onResume()
        eventDelegate.onResume()
    }

    override fun onDestroy() {
        eventDelegate.onDestroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (!eventDelegate.onBackPressed()) {
            super.onBackPressed()
        }
    }

    override fun onStop() {
        eventDelegate.onStop()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        eventDelegate.onPause()
    }

    override fun onPostResume() {
        super.onPostResume()
        eventDelegate.onPostResume()
    }

    // @Override - added in API level 23
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        eventDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (!eventDelegate.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onNewIntent(intent: Intent) {
        eventDelegate.onNewIntent(intent)
    }

    public override fun onUserLeaveHint() {
        eventDelegate.onUserLeaveHint()
    }

    override fun onTrimMemory(level: Int) {
        eventDelegate.onTrimMemory(level)
    }

    override fun onLowMemory() {
        eventDelegate.onLowMemory()
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        eventDelegate.onConfigurationChanged(newConfig)
    }
}