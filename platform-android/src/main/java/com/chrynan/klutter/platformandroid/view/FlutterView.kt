package com.chrynan.klutter.platformandroid.view

import android.graphics.SurfaceTexture
import android.os.Build
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger.BinaryMessageHandler
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger.BinaryReply
import android.view.accessibility.AccessibilityNodeProvider
import android.view.accessibility.AccessibilityManager
import com.chrynan.klutter.platformandroid.plugin.common.StandardMessageCodec
import android.graphics.Bitmap
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.chrynan.klutter.platformandroid.plugin.common.ActivityLifecycleListener
import com.chrynan.klutter.platformandroid.app.FlutterPluginRegistry
import com.chrynan.klutter.platformandroid.plugin.editing.TextInputPlugin
import com.chrynan.klutter.platformandroid.plugin.common.JSONMethodCodec
import com.chrynan.klutter.platformandroid.plugin.common.MethodChannel
import com.chrynan.klutter.platformandroid.plugin.platform.PlatformPlugin
import com.chrynan.klutter.platformandroid.plugin.common.JSONMessageCodec
import com.chrynan.klutter.platformandroid.plugin.common.BasicMessageChannel
import com.chrynan.klutter.platformandroid.plugin.common.StringCodec
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger
import org.json.JSONException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * An Android view containing a Flutter app.
 */
class FlutterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    nativeView: FlutterNativeView? = null
) : SurfaceView(context, attrs), BinaryMessenger, TextureRegistry,
    AccessibilityManager.AccessibilityStateChangeListener {

    companion object {

        private const val TAG = "FlutterView"

        // Must match the PointerChange enum in pointer.dart.
        private const val kPointerChangeCancel = 0
        private const val kPointerChangeAdd = 1
        private const val kPointerChangeRemove = 2
        private const val kPointerChangeHover = 3
        private const val kPointerChangeDown = 4
        private const val kPointerChangeMove = 5
        private const val kPointerChangeUp = 6

        // Must match the PointerDeviceKind enum in pointer.dart.
        private const val kPointerDeviceKindTouch = 0
        private const val kPointerDeviceKindMouse = 1
        private const val kPointerDeviceKindStylus = 2
        private const val kPointerDeviceKindInvertedStylus = 3
        private const val kPointerDeviceKindUnknown = 4

        // These values must match the unpacking code in hooks.dart.
        private const val kPointerDataFieldCount = 21
        private const val kPointerBytesPerField = 8
    }

    private val mImm: InputMethodManager
    private val mTextInputPlugin: TextInputPlugin
    private val mSurfaceCallback: SurfaceHolder.Callback
    private val mMetrics: ViewportMetrics
    private val mAccessibilityManager: AccessibilityManager
    private val mFlutterLocalizationChannel: MethodChannel
    private val mFlutterNavigationChannel: MethodChannel
    private val mFlutterKeyEventChannel: BasicMessageChannel<Any>
    private val mFlutterLifecycleChannel: BasicMessageChannel<String>
    private val mFlutterSystemChannel: BasicMessageChannel<Any>
    private val mFlutterSettingsChannel: BasicMessageChannel<Any>
    private val mActivityLifecycleListeners: MutableList<ActivityLifecycleListener>
    private val mFirstFrameListeners: MutableList<FirstFrameListener>
    private val nextTextureId = AtomicLong(0L)
    var flutterNativeView: FlutterNativeView? = null
        private set
    private val mAnimationScaleObserver: AnimationScaleObserver
    private var mIsSoftwareRenderingEnabled = false // using the software renderer or not
    private var mLastInputConnection: InputConnection? = null

    val pluginRegistry: FlutterPluginRegistry?
        get() = flutterNativeView!!.pluginRegistry

    internal val devicePixelRatio: Float
        get() = mMetrics.devicePixelRatio

    private val isAttached: Boolean
        get() = flutterNativeView != null && flutterNativeView!!.isAttached

    /**
     * Return the most recent frame as a bitmap.
     *
     * @return A bitmap.
     */
    val bitmap: Bitmap
        get() {
            assertAttached()
            return flutterNativeView!!.flutterJNI.bitmap
        }

    // ACCESSIBILITY

    private var mAccessibilityEnabled = false
    private var mTouchExplorationEnabled = false
    private var mAccessibilityFeatureFlags = 0
    private var mTouchExplorationListener: TouchExplorationListener? = null

    private var mAccessibilityNodeProvider: AccessibilityBridge? = null

    init {
        val activity = getContext() as Activity

        flutterNativeView = nativeView ?: FlutterNativeView(activity.applicationContext)

        mIsSoftwareRenderingEnabled = flutterNativeView?.flutterJNI?.nativeGetIsSoftwareRenderingEnabled == true

        mAnimationScaleObserver = AnimationScaleObserver(Handler())
        mMetrics = ViewportMetrics()
        mMetrics.devicePixelRatio = context.resources.displayMetrics.density
        isFocusable = true
        isFocusableInTouchMode = true

        flutterNativeView?.attachViewAndActivity(this, activity)

        mSurfaceCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                assertAttached()
                flutterNativeView?.flutterJNI.onSurfaceCreated(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                assertAttached()
                flutterNativeView?.flutterJNI.onSurfaceChanged(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                assertAttached()
                flutterNativeView?.flutterJNI.onSurfaceDestroyed()
            }
        }

        holder.addCallback(mSurfaceCallback)

        mAccessibilityManager = getContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        mActivityLifecycleListeners = ArrayList()
        mFirstFrameListeners = ArrayList()

        // Configure the platform plugins and flutter channels.
        mFlutterLocalizationChannel = MethodChannel(this, "flutter/localization", JSONMethodCodec.INSTANCE)
        mFlutterNavigationChannel = MethodChannel(this, "flutter/navigation", JSONMethodCodec.INSTANCE)
        mFlutterKeyEventChannel = BasicMessageChannel(this, "flutter/keyevent", JSONMessageCodec.INSTANCE)
        mFlutterLifecycleChannel = BasicMessageChannel(this, "flutter/lifecycle", StringCodec.INSTANCE)
        mFlutterSystemChannel = BasicMessageChannel(this, "flutter/system", JSONMessageCodec.INSTANCE)
        mFlutterSettingsChannel = BasicMessageChannel(this, "flutter/settings", JSONMessageCodec.INSTANCE)

        val platformPlugin = PlatformPlugin(activity)
        val flutterPlatformChannel = MethodChannel(this, "flutter/platform", JSONMethodCodec.INSTANCE)
        flutterPlatformChannel.setMethodCallHandler(platformPlugin)
        addActivityLifecycleListener(platformPlugin)
        mImm = getContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        mTextInputPlugin = TextInputPlugin(this)

        setLocales(resources.configuration)
        setUserSettings()
    }

    private fun encodeKeyEvent(event: KeyEvent, message: MutableMap<String, Any>) {
        message["flags"] = event.flags
        message["codePoint"] = event.unicodeChar
        message["keyCode"] = event.keyCode
        message["scanCode"] = event.scanCode
        message["metaState"] = event.metaState
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isAttached) return super.onKeyUp(keyCode, event)

        val message = mutableMapOf<String, Any>("type" to "keyup", "keymap" to "android")

        encodeKeyEvent(event, message)

        mFlutterKeyEventChannel.send(message)

        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isAttached) return super.onKeyDown(keyCode, event)

        if (event.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD) {
            if (mLastInputConnection != null && mImm.isAcceptingText) {
                mLastInputConnection!!.sendKeyEvent(event)
            }
        }

        val message = mutableMapOf<String, Any>("type" to "keydown", "keymap" to "android")

        encodeKeyEvent(event, message)

        mFlutterKeyEventChannel.send(message)

        return super.onKeyDown(keyCode, event)
    }

    fun getLookupKeyForAsset(asset: String) = FlutterMain.getLookupKeyForAsset(asset)

    fun getLookupKeyForAsset(asset: String, packageName: String) = FlutterMain.getLookupKeyForAsset(asset, packageName)

    fun addActivityLifecycleListener(listener: ActivityLifecycleListener) {
        mActivityLifecycleListeners.add(listener)
    }

    fun onStart() {
        mFlutterLifecycleChannel.send("AppLifecycleState.inactive")
    }

    fun onPause() {
        mFlutterLifecycleChannel.send("AppLifecycleState.inactive")
    }

    fun onPostResume() {
        updateAccessibilityFeatures()

        for (listener in mActivityLifecycleListeners) {
            listener.onPostResume()
        }

        mFlutterLifecycleChannel.send("AppLifecycleState.resumed")
    }

    fun onStop() {
        mFlutterLifecycleChannel.send("AppLifecycleState.paused")
    }

    fun onMemoryPressure() {
        mFlutterSystemChannel.send(mapOf("type" to "memoryPressure"))
    }

    /**
     * Provide a listener that will be called once when the FlutterView renders its
     * first frame to the underlaying SurfaceView.
     */
    fun addFirstFrameListener(listener: FirstFrameListener) {
        mFirstFrameListeners.add(listener)
    }

    /**
     * Remove an existing first frame listener.
     */
    fun removeFirstFrameListener(listener: FirstFrameListener) {
        mFirstFrameListeners.remove(listener)
    }

    /**
     * Updates this to support rendering as a transparent [SurfaceView].
     *
     * Sets it on top of its window. The background color still needs to be
     * controlled from within the Flutter UI itself.
     */
    fun enableTransparentBackground() {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
    }

    /**
     * Reverts this back to the [SurfaceView] defaults, at the back of its
     * window and opaque.
     */
    fun disableTransparentBackground() {
        setZOrderOnTop(false)
        holder.setFormat(PixelFormat.OPAQUE)
    }

    fun setInitialRoute(route: String) {
        mFlutterNavigationChannel.invokeMethod("setInitialRoute", route)
    }

    fun pushRoute(route: String) {
        mFlutterNavigationChannel.invokeMethod("pushRoute", route)
    }

    fun popRoute() {
        mFlutterNavigationChannel.invokeMethod("popRoute", null)
    }

    private fun setUserSettings() {
        val message = mapOf(
            "textScaleFactor" to resources.configuration.fontScale,
            "alwaysUse24HourFormat" to DateFormat.is24HourFormat(context)
        )

        mFlutterSettingsChannel.send(message)
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setLocales(config: Configuration) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                // Passes the full list of locales for android API >= 24 with reflection.
                val localeList = config.javaClass.getDeclaredMethod("getLocales").invoke(config)
                val localeListGet = localeList.javaClass.getDeclaredMethod("get", Int::class.javaPrimitiveType)
                val localeListSize = localeList.javaClass.getDeclaredMethod("size")
                val localeCount = localeListSize.invoke(localeList) as Int

                val data = mutableListOf<String>()

                for (index in 0 until localeCount) {
                    val locale = localeListGet.invoke(localeList, index) as Locale
                    data.add(locale.language)
                    data.add(locale.country)
                    data.add(locale.script)
                    data.add(locale.variant)
                }

                mFlutterLocalizationChannel.invokeMethod("setLocale", data)
                return
            } catch (exception: Exception) {
                // Any exception is a failure. Resort to fallback of sending only one locale.
            }
        }

        // Fallback single locale passing for android API < 24. Should work always.
        val locale = config.locale
        // getScript() is gated because it is added in API 21.
        mFlutterLocalizationChannel.invokeMethod(
            "setLocale",
            Arrays.asList(
                locale.language,
                locale.country,
                if (Build.VERSION.SDK_INT >= 21) locale.getScript() else "",
                locale.variant
            )
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        setLocales(newConfig)
        setUserSettings()
    }

    fun detach(): FlutterNativeView? {
        if (!isAttached) return null

        holder.removeCallback(mSurfaceCallback)

        flutterNativeView?.detach()

        val view = flutterNativeView
        flutterNativeView = null

        return view
    }

    fun destroy() {
        if (!isAttached) return

        holder.removeCallback(mSurfaceCallback)

        flutterNativeView?.destroy()
        flutterNativeView = null
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo) =
        try {
            mLastInputConnection = mTextInputPlugin.createInputConnection(this, outAttrs)
            mLastInputConnection
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to create input connection", e)
            null
        }

    private fun getPointerChangeForAction(maskedAction: Int): Int {
        // Primary pointer:
        if (maskedAction == MotionEvent.ACTION_DOWN) {
            return kPointerChangeDown
        }
        if (maskedAction == MotionEvent.ACTION_UP) {
            return kPointerChangeUp
        }
        // Secondary pointer:
        if (maskedAction == MotionEvent.ACTION_POINTER_DOWN) {
            return kPointerChangeDown
        }
        if (maskedAction == MotionEvent.ACTION_POINTER_UP) {
            return kPointerChangeUp
        }
        // All pointers:
        if (maskedAction == MotionEvent.ACTION_MOVE) {
            return kPointerChangeMove
        }
        if (maskedAction == MotionEvent.ACTION_HOVER_MOVE) {
            return kPointerChangeHover
        }
        return if (maskedAction == MotionEvent.ACTION_CANCEL) {
            kPointerChangeCancel
        } else -1
    }

    private fun getPointerDeviceTypeForToolType(toolType: Int) =
        when (toolType) {
            MotionEvent.TOOL_TYPE_FINGER -> kPointerDeviceKindTouch
            MotionEvent.TOOL_TYPE_STYLUS -> kPointerDeviceKindStylus
            MotionEvent.TOOL_TYPE_MOUSE -> kPointerDeviceKindMouse
            MotionEvent.TOOL_TYPE_ERASER -> kPointerDeviceKindInvertedStylus
            else ->
                // MotionEvent.TOOL_TYPE_UNKNOWN will reach here.
                kPointerDeviceKindUnknown
        }

    private fun addPointerForIndex(
        event: MotionEvent, pointerIndex: Int, pointerChange: Int,
        pointerData: Int, packet: ByteBuffer
    ) {
        if (pointerChange == -1) return

        val pointerKind = getPointerDeviceTypeForToolType(event.getToolType(pointerIndex))

        val timeStamp = event.eventTime * 1000 // Convert from milliseconds to microseconds.

        packet.putLong(timeStamp) // time_stamp
        packet.putLong(pointerChange.toLong()) // change
        packet.putLong(pointerKind.toLong()) // kind
        packet.putLong(event.getPointerId(pointerIndex).toLong()) // device
        packet.putDouble(event.getX(pointerIndex).toDouble()) // physical_x
        packet.putDouble(event.getY(pointerIndex).toDouble()) // physical_y

        when (pointerKind) {
            kPointerDeviceKindMouse -> packet.putLong((event.buttonState and 0x1F).toLong()) // buttons
            kPointerDeviceKindStylus -> packet.putLong((event.buttonState shr 4 and 0xF).toLong()) // buttons
            else -> packet.putLong(0) // buttons
        }

        packet.putLong(0) // obscured

        val pressureRange = event.device.getMotionRange(MotionEvent.AXIS_PRESSURE)
        packet.putDouble(event.getPressure(pointerIndex).toDouble()) // pressure
        packet.putDouble(pressureRange.min.toDouble()) // pressure_min
        packet.putDouble(pressureRange.max.toDouble()) // pressure_max

        if (pointerKind == kPointerDeviceKindStylus) {
            packet.putDouble(event.getAxisValue(MotionEvent.AXIS_DISTANCE, pointerIndex).toDouble()) // distance
            packet.putDouble(0.0) // distance_max
        } else {
            packet.putDouble(0.0) // distance
            packet.putDouble(0.0) // distance_max
        }

        packet.putDouble(event.getSize(pointerIndex).toDouble()) // size

        packet.putDouble(event.getToolMajor(pointerIndex).toDouble()) // radius_major
        packet.putDouble(event.getToolMinor(pointerIndex).toDouble()) // radius_minor

        packet.putDouble(0.0) // radius_min
        packet.putDouble(0.0) // radius_max

        packet.putDouble(event.getAxisValue(MotionEvent.AXIS_ORIENTATION, pointerIndex).toDouble()) // orientation

        if (pointerKind == kPointerDeviceKindStylus) {
            packet.putDouble(event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex).toDouble()) // tilt
        } else {
            packet.putDouble(0.0) // tilt
        }

        packet.putLong(pointerData.toLong()) // platformData
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isAttached) return false

        // TODO(abarth): This version check might not be effective in some
        // versions of Android that statically compile code and will be upset
        // at the lack of |requestUnbufferedDispatch|. Instead, we should factor
        // version-dependent code into separate classes for each supported
        // version and dispatch dynamically.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            requestUnbufferedDispatch(event)
        }

        // This value must match the value in framework's platform_view.dart.
        // This flag indicates whether the original Android pointer events were batched together.
        val kPointerDataFlagBatched = 1

        val pointerCount = event.pointerCount

        val packet = ByteBuffer.allocateDirect(pointerCount * kPointerDataFieldCount * kPointerBytesPerField)
        packet.order(ByteOrder.LITTLE_ENDIAN)

        val maskedAction = event.actionMasked
        val pointerChange = getPointerChangeForAction(event.actionMasked)
        if (maskedAction == MotionEvent.ACTION_DOWN || maskedAction == MotionEvent.ACTION_POINTER_DOWN) {
            // ACTION_DOWN and ACTION_POINTER_DOWN always apply to a single pointer only.
            addPointerForIndex(event, event.actionIndex, pointerChange, 0, packet)
        } else if (maskedAction == MotionEvent.ACTION_UP || maskedAction == MotionEvent.ACTION_POINTER_UP) {
            // ACTION_UP and ACTION_POINTER_UP may contain position updates for other pointers.
            // We are converting these updates to move events here in order to preserve this data.
            // We also mark these events with a flag in order to help the framework reassemble
            // the original Android event later, should it need to forward it to a PlatformView.
            for (p in 0 until pointerCount) {
                if (p != event.actionIndex) {
                    if (event.getToolType(p) == MotionEvent.TOOL_TYPE_FINGER) {
                        addPointerForIndex(event, p, kPointerChangeMove, kPointerDataFlagBatched, packet)
                    }
                }
            }
            // It's important that we're sending the UP event last. This allows PlatformView
            // to correctly batch everything back into the original Android event if needed.
            addPointerForIndex(event, event.actionIndex, pointerChange, 0, packet)
        } else {
            // ACTION_MOVE may not actually mean all pointers have moved
            // but it's the responsibility of a later part of the system to
            // ignore 0-deltas if desired.
            for (p in 0 until pointerCount) {
                addPointerForIndex(event, p, pointerChange, 0, packet)
            }
        }

        if (packet.position() % (kPointerDataFieldCount * kPointerBytesPerField) != 0) {
            throw AssertionError("Packet position is not on field boundary")
        }

        flutterNativeView!!.flutterJNI.dispatchPointerDataPacket(packet, packet.position())

        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (!isAttached) return false

        val handled = handleAccessibilityHoverEvent(event)

        if (!handled) {
            // TODO(ianh): Expose hover events to the platform,
            // implementing ADD, REMOVE, etc.
        }

        return handled
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) ||
            event.actionMasked != MotionEvent.ACTION_HOVER_MOVE ||
            !isAttached
        ) return super.onGenericMotionEvent(event)

        val pointerChange = getPointerChangeForAction(event.actionMasked)
        val packet = ByteBuffer.allocateDirect(
            event.pointerCount * kPointerDataFieldCount * kPointerBytesPerField
        )

        packet.order(ByteOrder.LITTLE_ENDIAN)

        // ACTION_HOVER_MOVE always applies to a single pointer only.
        addPointerForIndex(event, event.actionIndex, pointerChange, 0, packet)

        if (packet.position() % (kPointerDataFieldCount * kPointerBytesPerField) != 0) {
            throw AssertionError("Packet position is not on field boundary")
        }

        flutterNativeView!!.flutterJNI.dispatchPointerDataPacket(packet, packet.position())

        return true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        mMetrics.physicalWidth = width
        mMetrics.physicalHeight = height
        updateViewportMetrics()
        super.onSizeChanged(width, height, oldWidth, oldHeight)
    }

    private fun calculateShouldZeroSides(): ZeroSides {
        // We get both orientation and rotation because rotation is all 4
        // rotations relative to default rotation while orientation is portrait
        // or landscape. By combining both, we can obtain a more precise measure
        // of the rotation.
        val activity = context as Activity
        val orientation = activity.resources.configuration.orientation
        val rotation = activity.windowManager.defaultDisplay.rotation

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (rotation == Surface.ROTATION_90) {
                return ZeroSides.RIGHT
            } else if (rotation == Surface.ROTATION_270) {
                // In android API >= 23, the nav bar always appears on the "bottom" (USB) side.
                return if (Build.VERSION.SDK_INT >= 23) ZeroSides.LEFT else ZeroSides.RIGHT
            } else if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                return ZeroSides.BOTH
            }// Ambiguous orientation due to landscape left/right default. Zero both sides.
        }

        // Square orientation deprecated in API 16, we will not check for it and return false
        // to be safe and not remove any unique padding for the devices that do use it.
        return ZeroSides.NONE
    }

    // TODO(garyq): Use clean ways to detect keyboard instead of heuristics if possible
    // TODO(garyq): The keyboard detection may interact strangely with
    //   https://github.com/flutter/flutter/issues/22061

    // Uses inset heights and screen heights as a heuristic to determine if the insets should
    // be padded. When the on-screen keyboard is detected, we want to include the full inset
    // but when the inset is just the hidden nav bar, we want to provide a zero inset so the space
    // can be used.
    private fun calculateBottomKeyboardInset(insets: WindowInsets): Int {
        val screenHeight = rootView.height

        // Magic number due to this being a heuristic. This should be replaced, but we have not
        // found a clean way to do it yet (Sept. 2018)
        val keyboardHeightRatioHeuristic = 0.18

        return if (insets.systemWindowInsetBottom < screenHeight * keyboardHeightRatioHeuristic) {
            // Is not a keyboard, so return zero as inset.
            0
        } else {
            // Is a keyboard, so return the full inset.
            insets.systemWindowInsetBottom
        }
    }

    // This callback is not present in API < 20, which means lower API devices will see
    // the wider than expected padding when the status and navigation bars are hidden.
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val statusBarHidden = SYSTEM_UI_FLAG_FULLSCREEN and windowSystemUiVisibility != 0
        val navigationBarHidden = SYSTEM_UI_FLAG_HIDE_NAVIGATION and windowSystemUiVisibility != 0

        // We zero the left and/or right sides to prevent the padding the
        // navigation bar would have caused.
        var zeroSides = ZeroSides.NONE

        if (navigationBarHidden) {
            zeroSides = calculateShouldZeroSides()
        }

        // The padding on top should be removed when the statusbar is hidden.
        mMetrics.physicalPaddingTop = if (statusBarHidden) 0 else insets.systemWindowInsetTop
        mMetrics.physicalPaddingRight =
                if (zeroSides == ZeroSides.RIGHT || zeroSides == ZeroSides.BOTH) 0 else insets.systemWindowInsetRight
        mMetrics.physicalPaddingBottom = 0
        mMetrics.physicalPaddingLeft =
                if (zeroSides == ZeroSides.LEFT || zeroSides == ZeroSides.BOTH) 0 else insets.systemWindowInsetLeft

        // Bottom system inset (keyboard) should adjust scrollable bottom edge (inset).
        mMetrics.physicalViewInsetTop = 0
        mMetrics.physicalViewInsetRight = 0

        // We perform hidden navbar and keyboard handling if the navbar is set to hidden. Otherwise,
        // the navbar padding should always be provided.
        mMetrics.physicalViewInsetBottom =
                if (navigationBarHidden) calculateBottomKeyboardInset(insets) else insets.systemWindowInsetBottom
        mMetrics.physicalViewInsetLeft = 0

        updateViewportMetrics()

        return super.onApplyWindowInsets(insets)
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun fitSystemWindows(insets: Rect): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            // Status bar, left/right system insets partially obscure content (padding).
            mMetrics.physicalPaddingTop = insets.top
            mMetrics.physicalPaddingRight = insets.right
            mMetrics.physicalPaddingBottom = 0
            mMetrics.physicalPaddingLeft = insets.left

            // Bottom system inset (keyboard) should adjust scrollable bottom edge (inset).
            mMetrics.physicalViewInsetTop = 0
            mMetrics.physicalViewInsetRight = 0
            mMetrics.physicalViewInsetBottom = insets.bottom
            mMetrics.physicalViewInsetLeft = 0

            updateViewportMetrics()

            return true
        } else {
            return super.fitSystemWindows(insets)
        }
    }

    internal fun assertAttached() {
        if (!isAttached) throw AssertionError("Platform view is not attached")
    }

    private fun preRun() {
        resetAccessibilityTree()
    }

    private fun postRun() {}

    private fun runFromBundle(args: FlutterRunArguments) {
        assertAttached()
        preRun()
        flutterNativeView?.runFromBundle(args)
        postRun()
    }

    @Deprecated(
        " Please use runFromBundle with `FlutterRunArguments`.\n" +
                "      Parameter `reuseRuntimeController` has no effect."
    )
    @JvmOverloads
    fun runFromBundle(
        bundlePath: String,
        defaultPath: String,
        entrypoint: String = "main",
        reuseRuntimeController: Boolean = false
    ) {
        val args = FlutterRunArguments().apply {
            this.bundlePath = bundlePath
            this.entrypoint = entrypoint
            this.defaultPath = defaultPath
        }

        runFromBundle(args)
    }

    private fun updateViewportMetrics() {
        if (!isAttached) return

        flutterNativeView?.flutterJNI?.setViewportMetrics(
            mMetrics.devicePixelRatio, mMetrics.physicalWidth,
            mMetrics.physicalHeight, mMetrics.physicalPaddingTop, mMetrics.physicalPaddingRight,
            mMetrics.physicalPaddingBottom, mMetrics.physicalPaddingLeft, mMetrics.physicalViewInsetTop,
            mMetrics.physicalViewInsetRight, mMetrics.physicalViewInsetBottom, mMetrics.physicalViewInsetLeft
        )

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val fps = wm.defaultDisplay.refreshRate

        VsyncWaiter.refreshPeriodNanos = (1000000000.0 / fps).toLong()
        VsyncWaiter.refreshRateFPS = fps
    }

    // Called by native to update the semantics/accessibility tree.
    fun updateSemantics(buffer: ByteBuffer, strings: Array<String>) {
        try {
            if (mAccessibilityNodeProvider != null) {
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                mAccessibilityNodeProvider!!.updateSemantics(buffer, strings)
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Uncaught exception while updating semantics", ex)
        }
    }

    fun updateCustomAccessibilityActions(buffer: ByteBuffer, strings: Array<String>) {
        try {
            if (mAccessibilityNodeProvider != null) {
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                mAccessibilityNodeProvider!!.updateCustomAccessibilityActions(buffer, strings)
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Uncaught exception while updating local context actions", ex)
        }
    }

    // Called by native to notify first Flutter frame rendered.
    fun onFirstFrame() {
        // Allow listeners to remove themselves when they are called.
        val listeners = ArrayList(mFirstFrameListeners)
        for (listener in listeners) {
            listener.onFirstFrame()
        }
    }

    private fun dispatchSemanticsAction(id: Int, action: Action, args: Any? = null) {
        if (!isAttached) return

        var encodedArgs: ByteBuffer? = null
        var position = 0

        if (args != null) {
            encodedArgs = StandardMessageCodec.INSTANCE.encodeMessage(args)
            position = encodedArgs.position()
        }

        flutterNativeView?.flutterJNI?.dispatchSemanticsAction(id, action.value, encodedArgs, position)
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        mAccessibilityEnabled = mAccessibilityManager.isEnabled
        mTouchExplorationEnabled = mAccessibilityManager.isTouchExplorationEnabled

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val transitionUri = Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE)
            context.contentResolver.registerContentObserver(transitionUri, false, mAnimationScaleObserver)
        }

        if (mAccessibilityEnabled || mTouchExplorationEnabled) {
            ensureAccessibilityEnabled()
        }

        mAccessibilityFeatureFlags = if (mTouchExplorationEnabled) {
            mAccessibilityFeatureFlags or AccessibilityFeature.ACCESSIBLE_NAVIGATION.value
        } else {
            mAccessibilityFeatureFlags and AccessibilityFeature.ACCESSIBLE_NAVIGATION.value.inv()
        }

        // Apply additional accessibility settings
        updateAccessibilityFeatures()
        resetWillNotDraw()

        mAccessibilityManager.addAccessibilityStateChangeListener(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (mTouchExplorationListener == null) {
                mTouchExplorationListener = TouchExplorationListener()
            }
            mAccessibilityManager.addTouchExplorationStateChangeListener(mTouchExplorationListener!!)
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun updateAccessibilityFeatures() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val transitionAnimationScale = Settings.Global.getString(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE
            )

            mAccessibilityFeatureFlags = if (transitionAnimationScale != null && transitionAnimationScale == "0") {
                mAccessibilityFeatureFlags or AccessibilityFeature.DISABLE_ANIMATIONS.value
            } else {
                mAccessibilityFeatureFlags and AccessibilityFeature.DISABLE_ANIMATIONS.value.inv()
            }
        }

        flutterNativeView?.flutterJNI?.setAccessibilityFeatures(mAccessibilityFeatureFlags)
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        context.contentResolver.unregisterContentObserver(mAnimationScaleObserver)
        mAccessibilityManager.removeAccessibilityStateChangeListener(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mAccessibilityManager.removeTouchExplorationStateChangeListener(mTouchExplorationListener)
        }
    }

    private fun resetWillNotDraw() {
        if (!mIsSoftwareRenderingEnabled) {
            setWillNotDraw(!(mAccessibilityEnabled || mTouchExplorationEnabled))
        } else {
            setWillNotDraw(false)
        }
    }

    override fun onAccessibilityStateChanged(enabled: Boolean) {
        if (enabled) {
            ensureAccessibilityEnabled()
        } else {
            mAccessibilityEnabled = false

            if (mAccessibilityNodeProvider != null) {
                mAccessibilityNodeProvider!!.setAccessibilityEnabled(false)
            }

            flutterNativeView?.flutterJNI?.setSemanticsEnabled(false)
        }

        resetWillNotDraw()
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider? {
        return if (mAccessibilityEnabled) mAccessibilityNodeProvider else null
        // TODO(goderbauer): when a11y is off this should return a one-off snapshot of
        // the a11y
        // tree.
    }

    private fun ensureAccessibilityEnabled() {
        if (!isAttached) return

        mAccessibilityEnabled = true

        if (mAccessibilityNodeProvider == null) {
            mAccessibilityNodeProvider = AccessibilityBridge(this)
        }

        flutterNativeView?.flutterJNI?.setSemanticsEnabled(true)
        mAccessibilityNodeProvider?.setAccessibilityEnabled(true)
    }

    internal fun resetAccessibilityTree() {
        mAccessibilityNodeProvider?.reset()
    }

    private fun handleAccessibilityHoverEvent(event: MotionEvent): Boolean {
        if (!mTouchExplorationEnabled) return false

        if (event.action == MotionEvent.ACTION_HOVER_ENTER || event.action == MotionEvent.ACTION_HOVER_MOVE) {
            mAccessibilityNodeProvider?.handleTouchExploration(event.x, event.y)
        } else if (event.action == MotionEvent.ACTION_HOVER_EXIT) {
            mAccessibilityNodeProvider?.handleTouchExplorationExit()
        } else {
            Log.d("flutter", "unexpected accessibility hover event: $event")
            return false
        }

        return true
    }

    override fun send(channel: String, message: ByteBuffer) =
        send(channel = channel, message = message, callback = null)

    override fun send(channel: String, message: ByteBuffer?, callback: BinaryReply?) {
        if (!isAttached) {
            Log.d(TAG, "FlutterView.send called on a detached view, channel=$channel")
            return
        }

        flutterNativeView?.send(channel, message, callback)
    }

    override fun setMessageHandler(channel: String, handler: BinaryMessageHandler?) {
        flutterNativeView?.setMessageHandler(channel, handler)
    }

    override fun createSurfaceTexture(): TextureRegistry.SurfaceTextureEntry {
        val surfaceTexture = SurfaceTexture(0).apply {
            detachFromGLContext()
        }

        val entry = SurfaceTextureRegistryEntry(
            nextTextureId.getAndIncrement(),
            surfaceTexture,
            flutterNativeView
        )

        flutterNativeView?.flutterJNI?.registerTexture(entry.id(), surfaceTexture)

        return entry
    }
}