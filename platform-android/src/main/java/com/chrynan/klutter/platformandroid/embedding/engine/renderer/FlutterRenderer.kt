package com.chrynan.klutter.platformandroid.embedding.engine.renderer

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import com.chrynan.klutter.platformandroid.embedding.engine.FlutterJNI
import com.chrynan.klutter.platformandroid.view.TextureRegistry
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * WARNING: THIS CLASS IS EXPERIMENTAL. DO NOT SHIP A DEPENDENCY ON THIS CODE.
 * IF YOU USE IT, WE WILL BREAK YOU.
 *
 * `FlutterRenderer` works in tandem with a provided [RenderSurface] to create an
 * interactive Flutter UI.
 *
 * `FlutterRenderer` manages textures for rendering, and forwards some Java calls to native Flutter
 * code via JNI. The corresponding [RenderSurface] is used as a delegate to carry out
 * certain actions on behalf of this `FlutterRenderer` within an Android view hierarchy.
 *
 * [FlutterView] is an implementation of a [RenderSurface].
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
class FlutterRenderer(private val flutterJNI: FlutterJNI) : TextureRegistry {

    private val nextTextureId = AtomicLong(0L)
    private var renderSurface: RenderSurface? = null

    // TODO(mattcarroll): describe the native behavior that this invokes
    val bitmap: Bitmap
        get() = flutterJNI.bitmap

    // TODO(mattcarroll): describe the native behavior that this invokes
    val isSoftwareRenderingEnabled: Boolean
        get() = FlutterJNI.nativeGetIsSoftwareRenderingEnabled()

    fun attachToRenderSurface(renderSurface: RenderSurface) {
        // TODO(mattcarroll): determine desired behavior when attaching to an already attached renderer
        if (this.renderSurface != null) {
            detachFromRenderSurface()
        }

        this.renderSurface = renderSurface
        this.flutterJNI.setRenderSurface(renderSurface)
    }

    fun detachFromRenderSurface() {
        // TODO(mattcarroll): determine desired behavior if we're asked to detach without first being attached
        if (this.renderSurface != null) {
            surfaceDestroyed()
            this.renderSurface = null
            this.flutterJNI.setRenderSurface(null)
        }
    }

    fun addOnFirstFrameRenderedListener(listener: OnFirstFrameRenderedListener) {
        flutterJNI.addOnFirstFrameRenderedListener(listener)
    }

    fun removeOnFirstFrameRenderedListener(listener: OnFirstFrameRenderedListener) {
        flutterJNI.removeOnFirstFrameRenderedListener(listener)
    }

    //------ START TextureRegistry IMPLEMENTATION -----
    // TODO(mattcarroll): detachFromGLContext requires API 16. Create solution for earlier APIs.
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    override fun createSurfaceTexture(): TextureRegistry.SurfaceTextureEntry {
        val surfaceTexture = SurfaceTexture(0).apply {
            detachFromGLContext()
        }

        val entry = SurfaceTextureRegistryEntry(
            nextTextureId.getAndIncrement(),
            surfaceTexture,
            ::markTextureFrameAvailable,
            ::unregisterTexture
        )

        registerTexture(entry.id(), surfaceTexture)

        return entry
    }

    // TODO(mattcarroll): describe the native behavior that this invokes
    fun surfaceCreated(surface: Surface) {
        flutterJNI.onSurfaceCreated(surface)
    }

    // TODO(mattcarroll): describe the native behavior that this invokes
    fun surfaceChanged(width: Int, height: Int) {
        flutterJNI.onSurfaceChanged(width, height)
    }

    // TODO(mattcarroll): describe the native behavior that this invokes
    private fun surfaceDestroyed() {
        flutterJNI.onSurfaceDestroyed()
    }

    // TODO(mattcarroll): describe the native behavior that this invokes
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
        flutterJNI.setViewportMetrics(
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

    // TODO(mattcarroll): describe the native behavior that this invokes
    fun dispatchPointerDataPacket(buffer: ByteBuffer, position: Int) {
        flutterJNI.dispatchPointerDataPacket(buffer, position)
    }

    // TODO(mattcarroll): describe the native behavior that this invokes
    private fun registerTexture(textureId: Long, surfaceTexture: SurfaceTexture) {
        flutterJNI.registerTexture(textureId, surfaceTexture)
    }

    // TODO(mattcarroll): describe the native behavior that this invokes
    private fun markTextureFrameAvailable(textureId: Long) {
        flutterJNI.markTextureFrameAvailable(textureId)
    }

    // TODO(mattcarroll): describe the native behavior that this invokes
    private fun unregisterTexture(textureId: Long) {
        flutterJNI.unregisterTexture(textureId)
    }

    // TODO(mattcarroll): describe the native behavior that this invokes
    fun setAccessibilityFeatures(flags: Int) {
        flutterJNI.setAccessibilityFeatures(flags)
    }

    // TODO(mattcarroll): describe the native behavior that this invokes
    fun setSemanticsEnabled(enabled: Boolean) {
        flutterJNI.setSemanticsEnabled(enabled)
    }

    // TODO(mattcarroll): describe the native behavior that this invokes
    fun dispatchSemanticsAction(
        id: Int,
        action: Int,
        args: ByteBuffer,
        argsPosition: Int
    ) {
        flutterJNI.dispatchSemanticsAction(
            id,
            action,
            args,
            argsPosition
        )
    }
}