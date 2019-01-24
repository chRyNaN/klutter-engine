package com.chrynan.klutter.platformandroid.embedding.engine

import com.chrynan.klutter.platformandroid.embedding.engine.FlutterEngine.EngineLifecycleListener
import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.graphics.Bitmap
import android.view.Surface
import com.chrynan.klutter.platformandroid.embedding.engine.dart.PlatformMessageHandler
import com.chrynan.klutter.platformandroid.embedding.engine.renderer.FlutterRenderer
import com.chrynan.klutter.platformandroid.embedding.engine.renderer.OnFirstFrameRenderedListener
import org.jetbrains.annotations.Nullable
import java.nio.ByteBuffer


/**
 * Interface between Flutter embedding's Java code and Flutter engine's C/C++ code.
 *
 * WARNING: THIS CLASS IS EXPERIMENTAL. DO NOT SHIP A DEPENDENCY ON THIS CODE.
 * IF YOU USE IT, WE WILL BREAK YOU.
 *
 * Flutter's engine is built with C/C++. The Android Flutter embedding is responsible for
 * coordinating Android OS events and app user interactions with the C/C++ engine. Such coordination
 * requires messaging from an Android app in Java code to the C/C++ engine code. This
 * communication requires a JNI (Java Native Interface) API to cross the Java/native boundary.
 *
 * The entirety of Flutter's JNI API is codified in `FlutterJNI`. There are multiple reasons
 * that all such calls are centralized in one class. First, JNI calls are inherently static and
 * contain no Java implementation, therefore there is little reason to associate calls with different
 * classes. Second, every JNI call must be registered in C/C++ code and this registration becomes
 * more complicated with every additional Java class that contains JNI calls. Third, most Android
 * developers are not familiar with native development or JNI intricacies, therefore it is in the
 * interest of future maintenance to reduce the API surface that includes JNI declarations. Thus,
 * all Flutter JNI calls are centralized in `FlutterJNI`.
 *
 * Despite the fact that individual JNI calls are inherently static, there is state that exists
 * within `FlutterJNI`. Most calls within `FlutterJNI` correspond to a specific
 * "platform view", of which there may be many. Therefore, each `FlutterJNI` instance holds
 * onto a "native platform view ID" after [.attachToNative], which is shared with
 * the native C/C++ engine code. That ID is passed to every platform-view-specific native method.
 * ID management is handled within `FlutterJNI` so that developers don't have to hold onto
 * that ID.
 *
 * To connect part of an Android app to Flutter's C/C++ engine, instantiate a `FlutterJNI` and
 * then attach it to the native side:
 *
 * `// Instantiate FlutterJNI and attach to the native side.
 * FlutterJNI flutterJNI = new FlutterJNI();
 * flutterJNI.attachToNative();
 *
 * // Use FlutterJNI as desired.
 * flutterJNI.dispatchPointerDataPacket(...);
 *
 * // Destroy the connection to the native side and cleanup.
 * flutterJNI.detachFromNativeAndReleaseResources();
` *
 *
 * To provide a visual, interactive surface for Flutter rendering and touch events, register a
 * [FlutterRenderer.RenderSurface] with [.setRenderSurface]
 *
 * To receive callbacks for certain events that occur on the native side, register listeners:
 *
 *
 *  1. [.addEngineLifecycleListener]
 *  1. [.addOnFirstFrameRenderedListener]
 *
 *
 * To facilitate platform messages between Java and Dart running in Flutter, register a handler:
 *
 * [.setPlatformMessageHandler]
 *
 * To invoke a native method that is not associated with a platform view, invoke it statically:
 *
 * `String uri = FlutterJNI.nativeGetObservatoryUri();
` *
 */
class FlutterJNI {

    companion object {

        private val TAG = "FlutterJNI"

        @UiThread
        external fun nativeGetIsSoftwareRenderingEnabled(): Boolean

        @UiThread
        external fun nativeGetObservatoryUri(): String
    }

    private var nativePlatformViewId: Long? = null
    private var renderSurface: FlutterRenderer.RenderSurface? = null
    private var platformMessageHandler: PlatformMessageHandler? = null
    private val engineLifecycleListeners = HashSet()
    private val firstFrameListeners = HashSet()

    val bitmap: Bitmap
        @UiThread
        get() {
            ensureAttachedToNative()
            return nativeGetBitmap(nativePlatformViewId!!)
        }
    //------- End from FlutterView -----

    // TODO(mattcarroll): rename comments after refactor is done and their origin no longer matters (https://github.com/flutter/flutter/issues/25533)
    //------ Start from FlutterNativeView ----
    val isAttached: Boolean
        get() = nativePlatformViewId != null

    /**
     * Sets the [FlutterRenderer.RenderSurface] delegate for the attached Flutter context.
     *
     * Flutter expects a user interface to exist on the platform side (Android), and that interface
     * is expected to offer some capabilities that Flutter depends upon. The [FlutterRenderer.RenderSurface]
     * interface represents those expectations. For example, Flutter expects to be able to request
     * that its user interface "update custom accessibility actions" and therefore the delegate interface
     * declares a corresponding method, [FlutterRenderer.RenderSurface.updateCustomAccessibilityActions].
     *
     * If an app includes a user interface that renders a Flutter UI then a [FlutterRenderer.RenderSurface]
     * should be set (this is the typical Flutter scenario). If no UI is being rendered, such as a
     * Flutter app that is running Dart code in the background, then no registration may be necessary.
     *
     * If no [FlutterRenderer.RenderSurface] is registered then related messages coming from
     * Flutter will be dropped (ignored).
     */
    @UiThread
    fun setRenderSurface(@Nullable renderSurface: FlutterRenderer.RenderSurface) {
        this.renderSurface = renderSurface
    }

    /**
     * Call invoked by native to be forwarded to an [io.flutter.view.AccessibilityBridge].
     *
     * The `buffer` and `strings` form a communication protocol that is implemented here:
     * https://github.com/flutter/engine/blob/master/shell/platform/android/platform_view_android.cc#L207
     */
    @UiThread
    private fun updateSemantics(buffer: ByteBuffer, strings: Array<String>) {
        if (renderSurface != null) {
            renderSurface!!.updateSemantics(buffer, strings)
        }
        // TODO(mattcarroll): log dropped messages when in debug mode (https://github.com/flutter/flutter/issues/25391)
    }

    /**
     * Call invoked by native to be forwarded to an [io.flutter.view.AccessibilityBridge].
     *
     * The `buffer` and `strings` form a communication protocol that is implemented here:
     * https://github.com/flutter/engine/blob/master/shell/platform/android/platform_view_android.cc#L207
     *
     * // TODO(cbracken): expand these docs to include more actionable information.
     */
    @UiThread
    private fun updateCustomAccessibilityActions(buffer: ByteBuffer, strings: Array<String>) {
        if (renderSurface != null) {
            renderSurface!!.updateCustomAccessibilityActions(buffer, strings)
        }
        // TODO(mattcarroll): log dropped messages when in debug mode (https://github.com/flutter/flutter/issues/25391)
    }

    // Called by native to notify first Flutter frame rendered.
    @UiThread
    private fun onFirstFrame() {
        if (renderSurface != null) {
            renderSurface!!.onFirstFrameRendered()
        }
        // TODO(mattcarroll): log dropped messages when in debug mode (https://github.com/flutter/flutter/issues/25391)

        for (listener in firstFrameListeners) {
            listener.onFirstFrameRendered()
        }
    }

    /**
     * Sets the handler for all platform messages that come from the attached platform view to Java.
     *
     * Communication between a specific Flutter context (Dart) and the host platform (Java) is
     * accomplished by passing messages. Messages can be sent from Java to Dart with the corresponding
     * `FlutterJNI` methods:
     *
     *  * [.dispatchPlatformMessage]
     *  * [.dispatchEmptyPlatformMessage]
     *
     *
     * `FlutterJNI` is also the recipient of all platform messages sent from its attached
     * Flutter context (AKA platform view). `FlutterJNI` does not know what to do with these
     * messages, so a handler is exposed to allow these messages to be processed in whatever manner is
     * desired:
     *
     * `setPlatformMessageHandler(PlatformMessageHandler)`
     *
     * If a message is received but no [PlatformMessageHandler] is registered, that message will
     * be dropped (ignored). Therefore, when using `FlutterJNI` to integrate a Flutter context
     * in an app, a [PlatformMessageHandler] must be registered for 2-way Java/Dart communication
     * to operate correctly. Moreover, the handler must be implemented such that fundamental platform
     * messages are handled as expected. See [FlutterNativeView] for an example implementation.
     */
    @UiThread
    fun setPlatformMessageHandler(@Nullable platformMessageHandler: PlatformMessageHandler) {
        this.platformMessageHandler = platformMessageHandler
    }

    // Called by native.
    private fun handlePlatformMessage(channel: String, message: ByteArray, replyId: Int) {
        if (platformMessageHandler != null) {
            platformMessageHandler!!.handlePlatformMessage(channel, message, replyId)
        }
        // TODO(mattcarroll): log dropped messages when in debug mode (https://github.com/flutter/flutter/issues/25391)
    }

    // Called by native to respond to a platform message that we sent.
    private fun handlePlatformMessageResponse(replyId: Int, reply: ByteArray) {
        if (platformMessageHandler != null) {
            platformMessageHandler!!.handlePlatformMessageResponse(replyId, reply)
        }
        // TODO(mattcarroll): log dropped messages when in debug mode (https://github.com/flutter/flutter/issues/25391)
    }

    @UiThread
    fun addEngineLifecycleListener(@NonNull engineLifecycleListener: EngineLifecycleListener) {
        engineLifecycleListeners.add(engineLifecycleListener)
    }

    @UiThread
    fun removeEngineLifecycleListener(@NonNull engineLifecycleListener: EngineLifecycleListener) {
        engineLifecycleListeners.remove(engineLifecycleListener)
    }

    @UiThread
    fun addOnFirstFrameRenderedListener(@NonNull listener: OnFirstFrameRenderedListener) {
        firstFrameListeners.add(listener)
    }

    @UiThread
    fun removeOnFirstFrameRenderedListener(@NonNull listener: OnFirstFrameRenderedListener) {
        firstFrameListeners.remove(listener)
    }

    // TODO(mattcarroll): rename comments after refactor is done and their origin no longer matters (https://github.com/flutter/flutter/issues/25533)
    //----- Start from FlutterView -----
    @UiThread
    fun onSurfaceCreated(@NonNull surface: Surface) {
        ensureAttachedToNative()
        nativeSurfaceCreated(nativePlatformViewId!!, surface)
    }

    private external fun nativeSurfaceCreated(nativePlatformViewId: Long, surface: Surface)

    @UiThread
    fun onSurfaceChanged(width: Int, height: Int) {
        ensureAttachedToNative()
        nativeSurfaceChanged(nativePlatformViewId!!, width, height)
    }

    private external fun nativeSurfaceChanged(nativePlatformViewId: Long, width: Int, height: Int)

    @UiThread
    fun onSurfaceDestroyed() {
        ensureAttachedToNative()
        nativeSurfaceDestroyed(nativePlatformViewId!!)
    }

    private external fun nativeSurfaceDestroyed(nativePlatformViewId: Long)

    @UiThread
    fun setViewportMetrics(
        devicePixelRatio: Float,
        physicalWidth: Int,
        physicalHeight: Int,
        physicalPaddingTop: Int,
        physicalPaddingRight: Int,
        physicalPaddingBottom: Int,
        physicalPaddingLeft: Int,
        physicalViewInsetTop: Int,
        physicalViewInsetRight: Int,
        physicalViewInsetBottom: Int,
        physicalViewInsetLeft: Int
    ) {
        ensureAttachedToNative()
        nativeSetViewportMetrics(
            nativePlatformViewId!!,
            devicePixelRatio,
            physicalWidth,
            physicalHeight,
            physicalPaddingTop,
            physicalPaddingRight,
            physicalPaddingBottom,
            physicalPaddingLeft,
            physicalViewInsetTop,
            physicalViewInsetRight,
            physicalViewInsetBottom,
            physicalViewInsetLeft
        )
    }

    private external fun nativeSetViewportMetrics(
        nativePlatformViewId: Long,
        devicePixelRatio: Float,
        physicalWidth: Int,
        physicalHeight: Int,
        physicalPaddingTop: Int,
        physicalPaddingRight: Int,
        physicalPaddingBottom: Int,
        physicalPaddingLeft: Int,
        physicalViewInsetTop: Int,
        physicalViewInsetRight: Int,
        physicalViewInsetBottom: Int,
        physicalViewInsetLeft: Int
    )

    private external fun nativeGetBitmap(nativePlatformViewId: Long): Bitmap

    @UiThread
    fun dispatchPointerDataPacket(buffer: ByteBuffer, position: Int) {
        ensureAttachedToNative()
        nativeDispatchPointerDataPacket(nativePlatformViewId!!, buffer, position)
    }

    private external fun nativeDispatchPointerDataPacket(
        nativePlatformViewId: Long,
        buffer: ByteBuffer,
        position: Int
    )

    @UiThread
    fun dispatchSemanticsAction(id: Int, action: Int, args: ByteBuffer?, argsPosition: Int) {
        ensureAttachedToNative()
        nativeDispatchSemanticsAction(nativePlatformViewId!!, id, action, args!!, argsPosition)
    }

    private external fun nativeDispatchSemanticsAction(
        nativePlatformViewId: Long,
        id: Int,
        action: Int,
        args: ByteBuffer,
        argsPosition: Int
    )

    @UiThread
    fun setSemanticsEnabled(enabled: Boolean) {
        ensureAttachedToNative()
        nativeSetSemanticsEnabled(nativePlatformViewId!!, enabled)
    }

    private external fun nativeSetSemanticsEnabled(nativePlatformViewId: Long, enabled: Boolean)

    @UiThread
    fun setAccessibilityFeatures(flags: Int) {
        ensureAttachedToNative()
        nativeSetAccessibilityFeatures(nativePlatformViewId!!, flags)
    }

    private external fun nativeSetAccessibilityFeatures(nativePlatformViewId: Long, flags: Int)

    @UiThread
    fun registerTexture(textureId: Long, surfaceTexture: SurfaceTexture) {
        ensureAttachedToNative()
        nativeRegisterTexture(nativePlatformViewId!!, textureId, surfaceTexture)
    }

    private external fun nativeRegisterTexture(
        nativePlatformViewId: Long,
        textureId: Long,
        surfaceTexture: SurfaceTexture
    )

    @UiThread
    fun markTextureFrameAvailable(textureId: Long) {
        ensureAttachedToNative()
        nativeMarkTextureFrameAvailable(nativePlatformViewId!!, textureId)
    }

    private external fun nativeMarkTextureFrameAvailable(nativePlatformViewId: Long, textureId: Long)

    @UiThread
    fun unregisterTexture(textureId: Long) {
        ensureAttachedToNative()
        nativeUnregisterTexture(nativePlatformViewId!!, textureId)
    }

    private external fun nativeUnregisterTexture(nativePlatformViewId: Long, textureId: Long)

    @UiThread
    fun attachToNative(isBackgroundView: Boolean) {
        ensureNotAttachedToNative()
        nativePlatformViewId = nativeAttach(this, isBackgroundView)
    }

    private external fun nativeAttach(flutterJNI: FlutterJNI, isBackgroundView: Boolean): Long

    @UiThread
    fun detachFromNativeButKeepNativeResources() {
        ensureAttachedToNative()
        nativeDetach(nativePlatformViewId!!)
        nativePlatformViewId = null
    }

    private external fun nativeDetach(nativePlatformViewId: Long)

    @UiThread
    fun detachFromNativeAndReleaseResources() {
        ensureAttachedToNative()
        nativeDestroy(nativePlatformViewId!!)
        nativePlatformViewId = null
    }

    private external fun nativeDestroy(nativePlatformViewId: Long)

    @UiThread
    fun runBundleAndSnapshotFromLibrary(
        @NonNull prioritizedBundlePaths: Array<String>,
        @Nullable entrypointFunctionName: String?,
        @Nullable pathToEntrypointFunction: String?,
        @NonNull assetManager: AssetManager
    ) {
        ensureAttachedToNative()
        nativeRunBundleAndSnapshotFromLibrary(
            nativePlatformViewId!!,
            prioritizedBundlePaths,
            entrypointFunctionName,
            pathToEntrypointFunction,
            assetManager
        )
    }

    private external fun nativeRunBundleAndSnapshotFromLibrary(
        nativePlatformViewId: Long,
        @NonNull prioritizedBundlePaths: Array<String>,
        @Nullable entrypointFunctionName: String,
        @Nullable pathToEntrypointFunction: String,
        @NonNull manager: AssetManager
    )

    @UiThread
    fun dispatchEmptyPlatformMessage(channel: String, responseId: Int) {
        ensureAttachedToNative()
        nativeDispatchEmptyPlatformMessage(nativePlatformViewId!!, channel, responseId)
    }

    // Send an empty platform message to Dart.
    private external fun nativeDispatchEmptyPlatformMessage(
        nativePlatformViewId: Long,
        channel: String,
        responseId: Int
    )

    @UiThread
    fun dispatchPlatformMessage(channel: String, message: ByteBuffer, position: Int, responseId: Int) {
        ensureAttachedToNative()
        nativeDispatchPlatformMessage(
            nativePlatformViewId!!,
            channel,
            message,
            position,
            responseId
        )
    }

    // Send a data-carrying platform message to Dart.
    private external fun nativeDispatchPlatformMessage(
        nativePlatformViewId: Long,
        channel: String,
        message: ByteBuffer,
        position: Int,
        responseId: Int
    )

    @UiThread
    fun invokePlatformMessageEmptyResponseCallback(responseId: Int) {
        ensureAttachedToNative()
        nativeInvokePlatformMessageEmptyResponseCallback(nativePlatformViewId!!, responseId)
    }

    // Send an empty response to a platform message received from Dart.
    private external fun nativeInvokePlatformMessageEmptyResponseCallback(
        nativePlatformViewId: Long,
        responseId: Int
    )

    @UiThread
    fun invokePlatformMessageResponseCallback(responseId: Int, message: ByteBuffer, position: Int) {
        ensureAttachedToNative()
        nativeInvokePlatformMessageResponseCallback(
            nativePlatformViewId!!,
            responseId,
            message,
            position
        )
    }

    // Send a data-carrying response to a platform message received from Dart.
    private external fun nativeInvokePlatformMessageResponseCallback(
        nativePlatformViewId: Long,
        responseId: Int,
        message: ByteBuffer,
        position: Int
    )
    //------ End from FlutterNativeView ----

    // TODO(mattcarroll): rename comments after refactor is done and their origin no longer matters (https://github.com/flutter/flutter/issues/25533)
    //------ Start from Engine ---
    // Called by native.
    private fun onPreEngineRestart() {
        for (listener in engineLifecycleListeners) {
            listener.onPreEngineRestart()
        }
    }
    //------ End from Engine ---

    private fun ensureNotAttachedToNative() {
        if (nativePlatformViewId != null) {
            throw RuntimeException("Cannot execute operation because FlutterJNI is attached to native.")
        }
    }

    private fun ensureAttachedToNative() {
        if (nativePlatformViewId == null) {
            throw RuntimeException("Cannot execute operation because FlutterJNI is not attached to native.")
        }
    }
}