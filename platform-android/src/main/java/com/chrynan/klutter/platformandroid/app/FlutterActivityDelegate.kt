package com.chrynan.klutter.platformandroid.app

import com.sun.org.apache.xerces.internal.util.DOMUtil.getParent
import android.view.ViewGroup
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.content.pm.PackageManager
import com.sun.javaws.Globals.getComponentName
import android.content.pm.ActivityInfo
import android.content.res.Resources.NotFoundException
import android.util.TypedValue
import android.graphics.drawable.Drawable
import javax.swing.text.StyleConstants.setBackground
import org.json.JSONObject
import android.content.Intent
import com.sun.jmx.snmp.EnumRowStatus.destroy
import com.sun.javafx.scene.control.skin.FXVK.detach
import android.content.pm.ApplicationInfo
import com.sun.tools.javac.tree.TreeInfo.flags
import android.content.Intent.getIntent
import android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
import netscape.javascript.JSObject.getWindow
import android.os.Build
import com.chrynan.klutter.platformandroid.util.Preconditions
import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import android.view.View


/**
 * Class that performs the actual work of tying Android [Activity]
 * instances to Flutter.
 *
 *
 * This exists as a dedicated class (as opposed to being integrated directly
 * into [FlutterActivity]) to facilitate applications that don't wish
 * to subclass `FlutterActivity`. The most obvious example of when this
 * may come in handy is if an application wishes to subclass the Android v4
 * support library's `FragmentActivity`.
 *
 * <h3>Usage:</h3>
 *
 * To wire this class up to your activity, simply forward the events defined
 * in [FlutterActivityEvents] from your activity to an instance of this
 * class. Optionally, you can make your activity implement
 * [PluginRegistry] and/or [io.flutter.view.FlutterView.Provider]
 * and forward those methods to this class as well.
 */
class FlutterActivityDelegate(
    activity: Activity,
    viewFactory: ViewFactory
) : FlutterActivityEvents,
    FlutterView.Provider,
    PluginRegistry {

    companion object {

        private val SPLASH_SCREEN_META_DATA_KEY = "io.flutter.app.android.SplashScreenUntilFirstFrame"
        private val TAG = "FlutterActivityDelegate"
        private val matchParent =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        private fun getArgsFromIntent(intent: Intent): Array<String>? {
            // Before adding more entries to this list, consider that arbitrary
            // Android applications can generate intents with extra data and that
            // there are many security-sensitive args in the binary.
            val args = ArrayList()
            if (intent.getBooleanExtra("trace-startup", false)) {
                args.add("--trace-startup")
            }
            if (intent.getBooleanExtra("start-paused", false)) {
                args.add("--start-paused")
            }
            if (intent.getBooleanExtra("use-test-fonts", false)) {
                args.add("--use-test-fonts")
            }
            if (intent.getBooleanExtra("enable-dart-profiling", false)) {
                args.add("--enable-dart-profiling")
            }
            if (intent.getBooleanExtra("enable-software-rendering", false)) {
                args.add("--enable-software-rendering")
            }
            if (intent.getBooleanExtra("skia-deterministic-rendering", false)) {
                args.add("--skia-deterministic-rendering")
            }
            if (intent.getBooleanExtra("trace-skia", false)) {
                args.add("--trace-skia")
            }
            if (intent.getBooleanExtra("verbose-logging", false)) {
                args.add("--verbose-logging")
            }
            if (!args.isEmpty()) {
                val argsArray = arrayOfNulls<String>(args.size())
                return args.toArray(argsArray)
            }
            return null
        }
    }

    private val activity: Activity
    private val viewFactory: ViewFactory
    var flutterView: FlutterView? = null
        private set
    private var launchView: View? = null

    private val isDebuggable: Boolean
        get() = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Extracts a [Drawable] from the parent activity's `windowBackground`.
     *
     * `android:windowBackground` is specifically reused instead of a other attributes
     * because the Android framework can display it fast enough when launching the app as opposed
     * to anything defined in the Activity subclass.
     *
     * Returns null if no `windowBackground` is set for the activity.
     */
    private val launchScreenDrawableFromActivityTheme: Drawable?
        get() {
            val typedValue = TypedValue()
            if (!activity.theme.resolveAttribute(
                    android.R.attr.windowBackground,
                    typedValue,
                    true
                )
            ) {
                return null
            }
            if (typedValue.resourceId == 0) {
                return null
            }
            try {
                return activity.resources.getDrawable(typedValue.resourceId)
            } catch (e: NotFoundException) {
                Log.e(TAG, "Referenced launch screen windowBackground resource does not exist")
                return null
            }

        }

    /**
     * Specifies the mechanism by which Flutter views are created during the
     * operation of a `FlutterActivityDelegate`.
     *
     *
     * A delegate's view factory will be consulted during
     * [.onCreate]. If it returns `null`, then the delegate
     * will fall back to instantiating a new full-screen `FlutterView`.
     *
     *
     * A delegate's native view factory will be consulted during
     * [.onCreate]. If it returns `null`, then the delegate
     * will fall back to instantiating a new `FlutterNativeView`. This is
     * useful for applications to override to reuse the FlutterNativeView held
     * e.g. by a pre-existing background service.
     */
    interface ViewFactory {
        fun createFlutterView(context: Context): FlutterView
        fun createFlutterNativeView(): FlutterNativeView

        /**
         * Hook for subclasses to indicate that the `FlutterNativeView`
         * returned by [.createFlutterNativeView] should not be destroyed
         * when this activity is destroyed.
         */
        fun retainFlutterNativeView(): Boolean
    }

    init {
        this.activity = Preconditions.checkNotNull(activity)
        this.viewFactory = Preconditions.checkNotNull(viewFactory)
    }

    // The implementation of PluginRegistry forwards to flutterView.
    fun hasPlugin(key: String): Boolean {
        return flutterView!!.getPluginRegistry().hasPlugin(key)
    }

    fun <T> valuePublishedByPlugin(pluginKey: String): T {
        return flutterView!!.getPluginRegistry().valuePublishedByPlugin(pluginKey)
    }

    fun registrarFor(pluginKey: String): Registrar {
        return flutterView!!.getPluginRegistry().registrarFor(pluginKey)
    }

    fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ): Boolean {
        return flutterView!!.getPluginRegistry().onRequestPermissionsResult(requestCode, permissions, grantResults)
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
        return flutterView!!.getPluginRegistry().onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = activity.window
            window.addFlags(LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = 0x40000000
            window.decorView.systemUiVisibility = PlatformPlugin.DEFAULT_SYSTEM_UI
        }

        val args = getArgsFromIntent(activity.intent)
        FlutterMain.ensureInitializationComplete(activity.applicationContext, args)

        flutterView = viewFactory.createFlutterView(activity)
        if (flutterView == null) {
            val nativeView = viewFactory.createFlutterNativeView()
            flutterView = FlutterView(activity, null, nativeView)
            flutterView!!.setLayoutParams(matchParent)
            activity.setContentView(flutterView)
            launchView = createLaunchView()
            if (launchView != null) {
                addLaunchView()
            }
        }

        if (loadIntent(activity.intent)) {
            return
        }

        val appBundlePath = FlutterMain.findAppBundlePath(activity.applicationContext)
        if (appBundlePath != null) {
            runBundle(appBundlePath)
        }
    }

    override fun onNewIntent(intent: Intent) {
        // Only attempt to reload the Flutter Dart code during development. Use
        // the debuggable flag as an indicator that we are in development mode.
        if (!isDebuggable || !loadIntent(intent)) {
            flutterView!!.getPluginRegistry().onNewIntent(intent)
        }
    }

    override fun onPause() {
        val app = activity.applicationContext as Application
        if (app is FlutterApplication) {
            val flutterApp = app as FlutterApplication
            if (activity == flutterApp.currentActivity) {
                flutterApp.currentActivity = null
            }
        }
        if (flutterView != null) {
            flutterView!!.onPause()
        }
    }

    override fun onStart() {
        if (flutterView != null) {
            flutterView!!.onStart()
        }
    }

    override fun onResume() {
        val app = activity.applicationContext as Application
        FlutterMain.onResume(app)
        if (app is FlutterApplication) {
            val flutterApp = app as FlutterApplication
            flutterApp.currentActivity = activity
        }
    }

    override fun onStop() {
        flutterView!!.onStop()
    }

    override fun onPostResume() {
        if (flutterView != null) {
            flutterView!!.onPostResume()
        }
    }

    override fun onDestroy() {
        val app = activity.applicationContext as Application
        if (app is FlutterApplication) {
            val flutterApp = app as FlutterApplication
            if (activity == flutterApp.currentActivity) {
                flutterApp.currentActivity = null
            }
        }
        if (flutterView != null) {
            val detach = flutterView!!.getPluginRegistry().onViewDestroy(flutterView!!.getFlutterNativeView())
            if (detach || viewFactory.retainFlutterNativeView()) {
                // Detach, but do not destroy the FlutterView if a plugin
                // expressed interest in its FlutterNativeView.
                flutterView!!.detach()
            } else {
                flutterView!!.destroy()
            }
        }
    }

    override fun onBackPressed(): Boolean {
        if (flutterView != null) {
            flutterView!!.popRoute()
            return true
        }
        return false
    }

    override fun onUserLeaveHint() {
        flutterView!!.getPluginRegistry().onUserLeaveHint()
    }

    override fun onTrimMemory(level: Int) {
        // Use a trim level delivered while the application is running so the
        // framework has a chance to react to the notification.
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            flutterView!!.onMemoryPressure()
        }
    }

    override fun onLowMemory() {
        flutterView!!.onMemoryPressure()
    }

    fun onConfigurationChanged(newConfig: Configuration) {}

    private fun loadIntent(intent: Intent): Boolean {
        val action = intent.action
        if (Intent.ACTION_RUN == action) {
            val route = intent.getStringExtra("route")
            var appBundlePath = intent.dataString
            if (appBundlePath == null) {
                // Fall back to the installation path if no bundle path was specified.
                appBundlePath = FlutterMain.findAppBundlePath(activity.applicationContext)
            }
            if (route != null) {
                flutterView!!.setInitialRoute(route)
            }

            runBundle(appBundlePath)
            return true
        }

        return false
    }

    private fun runBundle(appBundlePath: String?) {
        if (!flutterView!!.getFlutterNativeView().isApplicationRunning()) {
            val args = FlutterRunArguments()
            val bundlePaths = ArrayList()
            val resourceUpdater = FlutterMain.getResourceUpdater()
            if (resourceUpdater != null) {
                val patchFile = resourceUpdater!!.getInstalledPatch()
                val manifest = resourceUpdater!!.readManifest(patchFile)
                if (resourceUpdater!!.validateManifest(manifest)) {
                    bundlePaths.add(patchFile.getPath())
                }
            }
            bundlePaths.add(appBundlePath)
            args.bundlePaths = bundlePaths.toArray(arrayOfNulls<String>(0))
            args.entrypoint = "main"
            flutterView!!.runFromBundle(args)
        }
    }

    /**
     * Creates a [View] containing the same [Drawable] as the one set as the
     * `windowBackground` of the parent activity for use as a launch splash view.
     *
     * Returns null if no `windowBackground` is set for the activity.
     */
    private fun createLaunchView(): View? {
        if (!showSplashScreenUntilFirstFrame()) {
            return null
        }
        val launchScreenDrawable = launchScreenDrawableFromActivityTheme ?: return null
        val view = View(activity)
        view.setLayoutParams(matchParent)
        view.setBackground(launchScreenDrawable)
        return view
    }

    /**
     * Let the user specify whether the activity's `windowBackground` is a launch screen
     * and should be shown until the first frame via a <meta-data> tag in the activity.
    </meta-data> */
    private fun showSplashScreenUntilFirstFrame(): Boolean {
        try {
            val activityInfo = activity.packageManager.getActivityInfo(
                activity.componentName,
                PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES
            )
            val metadata = activityInfo.metaData
            return metadata != null && metadata.getBoolean(SPLASH_SCREEN_META_DATA_KEY)
        } catch (e: NameNotFoundException) {
            return false
        }

    }

    /**
     * Show and then automatically animate out the launch view.
     *
     * If a launch screen is defined in the user application's AndroidManifest.xml as the
     * activity's `windowBackground`, display it on top of the [FlutterView] and
     * remove the activity's `windowBackground`.
     *
     * Fade it out and remove it when the [FlutterView] renders its first frame.
     */
    private fun addLaunchView() {
        if (launchView == null) {
            return
        }

        activity.addContentView(launchView, matchParent)
        flutterView!!.addFirstFrameListener(object : FlutterView.FirstFrameListener() {
            fun onFirstFrame() {
                this@FlutterActivityDelegate.launchView!!.animate()
                    .alpha(0f)
                    // Use Android's default animation duration.
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            // Views added to an Activity's addContentView is always added to its
                            // root FrameLayout.
                            (this@FlutterActivityDelegate.launchView!!.getParent() as ViewGroup)
                                .removeView(this@FlutterActivityDelegate.launchView)
                            this@FlutterActivityDelegate.launchView = null
                        }
                    })

                this@FlutterActivityDelegate.flutterView!!.removeFirstFrameListener(this)
            }
        })

        // Resets the activity theme from the one containing the launch screen in the window
        // background to a blank one since the launch screen is now in a view in front of the
        // FlutterView.
        //
        // We can make this configurable if users want it.
        activity.setTheme(android.R.style.Theme_Black_NoTitleBar)
    }
}