package com.chrynan.klutter.platformandroid.view

import android.app.Activity
import android.content.Context
import android.util.Log
import com.chrynan.klutter.platformandroid.app.FlutterPluginRegistry
import com.chrynan.klutter.platformandroid.embedding.engine.FlutterJNI
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger.BinaryMessageHandler
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger.BinaryReply
import java.nio.ByteBuffer

class FlutterNativeView @JvmOverloads constructor(
    private val context: Context,
    isBackgroundView: Boolean = false
) : BinaryMessenger {

    companion object {

        private const val TAG = "FlutterNativeView"

        val observatoryUri: String
            get() = FlutterJNI.nativeGetObservatoryUri()
    }

    private val mMessageHandlers: MutableMap<String, BinaryMessageHandler> = mutableMapOf()
    private var mNextReplyId = 1
    private val mPendingReplies = mutableMapOf<Int, BinaryReply?>()

    val pluginRegistry = FlutterPluginRegistry(this, context)

    private var mFlutterView: FlutterView? = null

    internal val flutterJNI = FlutterJNI().apply {
        setRenderSurface(RenderSurfaceImpl(mFlutterView))
        setPlatformMessageHandler(
            PlatformMessageHandlerImpl(
                tag = TAG,
                assertAttached = ::assertAttached,
                mMessageHandlers = mMessageHandlers,
                pendingRepliesRemover = { mPendingReplies.remove(it) },
                flutterJNI = this
            )
        )
        addEngineLifecycleListener(EngineLifecycleListenerImpl(mFlutterView, pluginRegistry))
    }

    var isApplicationRunning: Boolean = false
        private set

    val isAttached: Boolean
        get() = flutterJNI.isAttached

    init {
        attach(this, isBackgroundView)
        assertAttached()
    }

    fun detach() {
        pluginRegistry.detach()
        mFlutterView = null
        flutterJNI.detachFromNativeButKeepNativeResources()
    }

    fun destroy() {
        pluginRegistry.destroy()
        mFlutterView = null
        flutterJNI.detachFromNativeAndReleaseResources()
        isApplicationRunning = false
    }

    fun attachViewAndActivity(flutterView: FlutterView, activity: Activity) {
        mFlutterView = flutterView
        pluginRegistry.attach(flutterView, activity)
    }

    fun assertAttached() {
        if (!isAttached) throw AssertionError("Platform view is not attached")
    }

    fun runFromBundle(args: FlutterRunArguments?) {
        val hasBundlePaths = args?.bundlePaths != null && args.bundlePaths.isNotEmpty()

        if (args?.bundlePath == null && !hasBundlePaths) {
            throw AssertionError("Either bundlePath or bundlePaths must be specified")
        } else if ((args?.bundlePath != null || args?.defaultPath != null) && hasBundlePaths) {
            throw AssertionError("Can't specify both bundlePath and bundlePaths")
        } else if (args?.entrypoint == null) {
            throw AssertionError("An entrypoint must be specified")
        }

        if (hasBundlePaths) {
            runFromBundleInternal(args.bundlePaths.toTypedArray(), args.entrypoint!!, args.libraryPath)
        } else {
            runFromBundleInternal(
                arrayOf(args.bundlePath!!, args.defaultPath!!),
                args.entrypoint!!, args.libraryPath
            )
        }
    }

    @Deprecated(
        " Please use runFromBundle with `FlutterRunArguments`.\n" +
                "      Parameter `reuseRuntimeController` has no effect."
    )
    fun runFromBundle(
        bundlePath: String, defaultPath: String, entrypoint: String,
        reuseRuntimeController: Boolean
    ) {
        runFromBundleInternal(arrayOf(bundlePath, defaultPath), entrypoint, null)
    }

    private fun runFromBundleInternal(bundlePaths: Array<String>, entrypoint: String, libraryPath: String?) {
        assertAttached()
        if (isApplicationRunning)
            throw AssertionError(
                "This Flutter engine instance is already running an application"
            )
        flutterJNI.runBundleAndSnapshotFromLibrary(
            bundlePaths,
            entrypoint,
            libraryPath,
            context.resources.assets
        )

        isApplicationRunning = true
    }

    override fun send(channel: String, message: ByteBuffer) =
        send(channel = channel, message = message, callback = null)

    override fun send(channel: String, message: ByteBuffer?, callback: BinaryReply?) {
        if (!isAttached) {
            Log.d(TAG, "FlutterView.send called on a detached view, channel=$channel")
            return
        }

        var replyId = 0

        if (callback != null) {
            replyId = mNextReplyId++
            mPendingReplies[replyId] = callback
        }

        if (message == null) {
            flutterJNI.dispatchEmptyPlatformMessage(channel, replyId)
        } else {
            flutterJNI.dispatchPlatformMessage(
                channel,
                message,
                message.position(),
                replyId
            )
        }
    }

    override fun setMessageHandler(channel: String, handler: BinaryMessageHandler?) {
        if (handler == null) {
            mMessageHandlers.remove(channel)
        } else {
            mMessageHandlers[channel] = handler
        }
    }

    private fun attach(view: FlutterNativeView, isBackgroundView: Boolean) {
        flutterJNI.attachToNative(isBackgroundView)
    }
}