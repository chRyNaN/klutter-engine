package com.chrynan.klutter.platformandroid.plugin.platform

import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.os.Build
import android.annotation.TargetApi
import android.content.Context
import android.util.Log
import android.view.View
import org.junit.runner.Request.method
import java.nio.ByteBuffer

/**
 * Manages platform views.
 *
 *
 * Each [io.flutter.app.FlutterPluginRegistry] has a single platform views controller.
 * A platform views controller can be attached to at most one Flutter view.
 */
class PlatformViewsController : MethodChannel.MethodCallHandler {

    private val mRegistry: PlatformViewRegistryImpl

    // The context of the Activity or Fragment hosting the render target for the Flutter engine.
    private var mContext: Context? = null

    // The texture registry maintaining the textures into which the embedded views will be rendered.
    private var mTextureRegistry: TextureRegistry? = null

    // The messenger used to communicate with the framework over the platform views channel.
    private var mMessenger: BinaryMessenger? = null

    private val vdControllers: HashMap<Int, VirtualDisplayController>

    val registry: PlatformViewRegistry
        get() = mRegistry

    init {
        mRegistry = PlatformViewRegistryImpl()
        vdControllers = HashMap()
    }

    /**
     * Attaches this platform views controller to its input and output channels.
     *
     * @param context The base context that will be passed to embedded views created by this controller.
     * This should be the context of the Activity hosting the Flutter application.
     * @param textureRegistry The texture registry which provides the output textures into which the embedded views
     * will be rendered.
     * @param messenger The Flutter application on the other side of this messenger drives this platform views controller.
     */
    fun attach(context: Context, textureRegistry: TextureRegistry, messenger: BinaryMessenger) {
        if (mContext != null) {
            throw AssertionError(
                "A PlatformViewsController can only be attached to a single output target.\n" + "attach was called while the PlatformViewsController was already attached."
            )
        }
        mContext = context
        mTextureRegistry = textureRegistry
        mMessenger = messenger
        val channel = MethodChannel(messenger,
            CHANNEL_NAME, StandardMethodCodec.INSTANCE)
        channel.setMethodCallHandler(this)
    }

    /**
     * Detaches this platform views controller.
     *
     * This is typically called when a Flutter applications moves to run in the background, or is destroyed.
     * After calling this the platform views controller will no longer listen to it's previous messenger, and will
     * not maintain references to the texture registry, context, and messenger passed to the previous attach call.
     */
    fun detach() {
        mMessenger!!.setMessageHandler(CHANNEL_NAME, null)
        mMessenger = null
        mContext = null
        mTextureRegistry = null
    }

    fun onFlutterViewDestroyed() {
        flushAllViews()
    }

    fun onPreEngineRestart() {
        flushAllViews()
    }

    fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < MINIMAL_SDK) {
            Log.e(
                TAG, "Trying to use platform views with API " + Build.VERSION.SDK_INT
                        + ", required API level is: " + MINIMAL_SDK
            )
            return
        }
        when (call.method) {
            "create" -> {
                createPlatformView(call, result)
                return
            }
            "dispose" -> {
                disposePlatformView(call, result)
                return
            }
            "resize" -> {
                resizePlatformView(call, result)
                return
            }
            "touch" -> {
                onTouch(call, result)
                return
            }
            "setDirection" -> {
                setDirection(call, result)
                return
            }
        }
        result.notImplemented()
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun createPlatformView(call: MethodCall, result: MethodChannel.Result) {
        val args = call.arguments()
        val id = args.get("id") as Int
        val viewType = args.get("viewType") as String
        val logicalWidth = args.get("width") as Double
        val logicalHeight = args.get("height") as Double
        val direction = args.get("direction") as Int

        if (!validateDirection(
                direction
            )
        ) {
            result.error(
                "error",
                "Trying to create a view with unknown direction value: $direction(view id: $id)", null
            )
            return
        }

        if (vdControllers.containsKey(id)) {
            result.error(
                "error",
                "Trying to create an already created platform view, view id: $id", null
            )
            return
        }

        val viewFactory = mRegistry.getFactory(viewType)
        if (viewFactory == null) {
            result.error(
                "error",
                "Trying to create a platform view of unregistered type: $viewType", null
            )
            return
        }

        var createParams: Any? = null
        if (args.containsKey("params")) {
            createParams = viewFactory.createArgsCodec.decodeMessage(ByteBuffer.wrap(args.get("params") as ByteArray))
        }

        val textureEntry = mTextureRegistry!!.createSurfaceTexture()
        val vdController =
            VirtualDisplayController.create(
                mContext,
                viewFactory,
                textureEntry,
                toPhysicalPixels(logicalWidth),
                toPhysicalPixels(logicalHeight),
                id,
                createParams
            )

        if (vdController == null) {
            result.error(
                "error",
                "Failed creating virtual display for a $viewType with id: $id", null
            )
            return
        }

        vdControllers[id] = vdController
        vdController!!.getView().setLayoutDirection(direction)

        // TODO(amirh): copy accessibility nodes to the FlutterView's accessibility tree.

        result.success(textureEntry.id())
    }

    private fun disposePlatformView(call: MethodCall, result: MethodChannel.Result) {
        val id = call.arguments()

        val vdController = vdControllers[id]
        if (vdController == null) {
            result.error(
                "error",
                "Trying to dispose a platform view with unknown id: $id", null
            )
            return
        }

        vdController!!.dispose()
        vdControllers.remove(id)
        result.success(null)
    }

    private fun resizePlatformView(call: MethodCall, result: MethodChannel.Result) {
        val args = call.arguments()
        val id = args.get("id") as Int
        val width = args.get("width") as Double
        val height = args.get("height") as Double

        val vdController = vdControllers[id]
        if (vdController == null) {
            result.error(
                "error",
                "Trying to resize a platform view with unknown id: $id", null
            )
            return
        }
        vdController!!.resize(
            toPhysicalPixels(width),
            toPhysicalPixels(height),
            Runnable { result.success(null) }
        )
    }

    private fun onTouch(call: MethodCall, result: MethodChannel.Result) {
        val args = call.arguments()

        val density = mContext!!.getResources().getDisplayMetrics().density

        val id = args.get(0) as Int
        val downTime = args.get(1) as Number
        val eventTime = args.get(2) as Number
        val action = args.get(3) as Int
        val pointerCount = args.get(4) as Int
        val pointerProperties = parsePointerPropertiesList(
            args.get(5)
        ).toTypedArray()
        val pointerCoords = parsePointerCoordsList(
            args.get(6),
            density
        ).toTypedArray()

        val metaState = args.get(7) as Int
        val buttonState = args.get(8) as Int
        val xPrecision = (args.get(9) as Double).toFloat()
        val yPrecision = (args.get(10) as Double).toFloat()
        val deviceId = args.get(11) as Int
        val edgeFlags = args.get(12) as Int
        val source = args.get(13) as Int
        val flags = args.get(14) as Int

        val view = vdControllers[id].getView()
        if (view == null) {
            result.error(
                "error",
                "Sending touch to an unknown view with id: $id", null
            )
            return
        }

        val event = MotionEvent.obtain(
            downTime.toLong(),
            eventTime.toLong(),
            action,
            pointerCount,
            pointerProperties,
            pointerCoords,
            metaState,
            buttonState,
            xPrecision,
            yPrecision,
            deviceId,
            edgeFlags,
            source,
            flags
        )

        view!!.dispatchTouchEvent(event)
        result.success(null)
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun setDirection(call: MethodCall, result: MethodChannel.Result) {
        val args = call.arguments()
        val id = args.get("id") as Int
        val direction = args.get("direction") as Int

        if (!validateDirection(
                direction
            )
        ) {
            result.error(
                "error",
                "Trying to set unknown direction value: $direction(view id: $id)", null
            )
            return
        }

        val view = vdControllers[id].getView()
        if (view == null) {
            result.error(
                "error",
                "Sending touch to an unknown view with id: $id", null
            )
            return
        }

        view!!.setLayoutDirection(direction)
        result.success(null)
    }

    private fun toPhysicalPixels(logicalPixels: Double): Int {
        val density = mContext!!.getResources().getDisplayMetrics().density
        return Math.round(logicalPixels * density).toInt()
    }

    private fun flushAllViews() {
        for (controller in vdControllers.values()) {
            controller.dispose()
        }
        vdControllers.clear()
    }

    companion object {
        private val TAG = "PlatformViewsController"

        private val CHANNEL_NAME = "flutter/platform_views"

        // API level 20 is required for VirtualDisplay#setSurface which we use when resizing a platform view.
        private val MINIMAL_SDK = Build.VERSION_CODES.KITKAT_WATCH

        private fun validateDirection(direction: Int): Boolean {
            return direction == View.LAYOUT_DIRECTION_LTR || direction == View.LAYOUT_DIRECTION_RTL
        }

        private fun parsePointerPropertiesList(rawPropertiesList: Any): List<PointerProperties> {
            val rawProperties = rawPropertiesList as List<Any>
            val pointerProperties = ArrayList()
            for (o in rawProperties) {
                pointerProperties.add(
                    parsePointerProperties(
                        o
                    )
                )
            }
            return pointerProperties
        }

        private fun parsePointerProperties(rawProperties: Any): PointerProperties {
            val propertiesList = rawProperties as List<Any>
            val properties = MotionEvent.PointerProperties()
            properties.id = propertiesList[0] as Int
            properties.toolType = propertiesList[1] as Int
            return properties
        }

        private fun parsePointerCoordsList(rawCoordsList: Any, density: Float): List<PointerCoords> {
            val rawCoords = rawCoordsList as List<Any>
            val pointerCoords = ArrayList()
            for (o in rawCoords) {
                pointerCoords.add(
                    parsePointerCoords(
                        o,
                        density
                    )
                )
            }
            return pointerCoords
        }

        private fun parsePointerCoords(rawCoords: Any, density: Float): PointerCoords {
            val coordsList = rawCoords as List<Any>
            val coords = MotionEvent.PointerCoords()
            coords.orientation = (coordsList[0] as Double).toFloat()
            coords.pressure = (coordsList[1] as Double).toFloat()
            coords.size = (coordsList[2] as Double).toFloat()
            coords.toolMajor = (coordsList[3] as Double).toFloat() * density
            coords.toolMinor = (coordsList[4] as Double).toFloat() * density
            coords.touchMajor = (coordsList[5] as Double).toFloat() * density
            coords.touchMinor = (coordsList[6] as Double).toFloat() * density
            coords.x = (coordsList[7] as Double).toFloat() * density
            coords.y = (coordsList[8] as Double).toFloat() * density
            return coords
        }
    }
}