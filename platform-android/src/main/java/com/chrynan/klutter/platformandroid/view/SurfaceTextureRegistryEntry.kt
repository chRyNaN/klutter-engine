package com.chrynan.klutter.platformandroid.view

import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler

class SurfaceTextureRegistryEntry(
    private val id: Long,
    private val surfaceTexture: SurfaceTexture,
    private val flutterNativeView: FlutterNativeView?
) :
    TextureRegistry.SurfaceTextureEntry {

    private var released: Boolean = false

    private val onFrameListener = SurfaceTexture.OnFrameAvailableListener {
        if (released) {
            // Even though we make sure to unregister the callback before releasing, as of Android O
            // SurfaceTexture has a data race when accessing the callback, so the callback may
            // still be called by a stale reference after released==true and mNativeView==null.
            return@OnFrameAvailableListener
        }

        flutterNativeView?.flutterJNI?.markTextureFrameAvailable(this@SurfaceTextureRegistryEntry.id)
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // The callback relies on being executed on the UI thread (unsynchronised read of mNativeView
            // and also the engine code check for platform thread in Shell::OnPlatformViewMarkTextureFrameAvailable),
            // so we explicitly pass a Handler for the current thread.
            this.surfaceTexture.setOnFrameAvailableListener(onFrameListener, Handler())
        } else {
            // Android documentation states that the listener can be called on an arbitrary thread.
            // But in practice, versions of Android that predate the newer API will call the listener
            // on the thread where the SurfaceTexture was constructed.
            this.surfaceTexture.setOnFrameAvailableListener(onFrameListener)
        }
    }

    override fun surfaceTexture() = surfaceTexture

    override fun id() = id

    override fun release() {
        if (released) return

        released = true

        flutterNativeView?.flutterJNI?.unregisterTexture(id)

        // Otherwise onFrameAvailableListener might be called after mNativeView==null
        // (https://github.com/flutter/flutter/issues/20951). See also the check in onFrameAvailable.
        surfaceTexture.setOnFrameAvailableListener(null)
        surfaceTexture.release()
    }
}