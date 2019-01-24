package com.chrynan.klutter.platformandroid.plugin.platform

import android.view.ViewTreeObserver
import android.os.Build
import android.annotation.TargetApi
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Surface
import android.view.View


@TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
internal class VirtualDisplayController private constructor(
    private val mContext: Context,
    private var mVirtualDisplay: VirtualDisplay?,
    viewFactory: PlatformViewFactory,
    private val mSurface: Surface,
    private val mTextureEntry: TextureRegistry.SurfaceTextureEntry,
    viewId: Int,
    createParams: Any
) {

    companion object {

        fun create(
            context: Context,
            viewFactory: PlatformViewFactory,
            textureEntry: TextureRegistry.SurfaceTextureEntry,
            width: Int,
            height: Int,
            viewId: Int,
            createParams: Any
        ): VirtualDisplayController? {
            textureEntry.surfaceTexture().setDefaultBufferSize(width, height)
            val surface = Surface(textureEntry.surfaceTexture())
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

            val densityDpi = context.getResources().getDisplayMetrics().densityDpi
            val virtualDisplay = displayManager.createVirtualDisplay(
                "flutter-vd",
                width,
                height,
                densityDpi,
                surface,
                0
            ) ?: return null

            return VirtualDisplayController(
                context, virtualDisplay, viewFactory, surface, textureEntry, viewId, createParams
            )
        }
    }

    private val mDensityDpi: Int
    private var mPresentation: SingleViewPresentation? = null

    val view: View?
        get() {
            if (mPresentation == null)
                return null
            val platformView = mPresentation!!.view
            return platformView!!.view
        }


    init {
        mDensityDpi = mContext.getResources().getDisplayMetrics().densityDpi
        mPresentation = SingleViewPresentation(
            mContext, mVirtualDisplay!!.display, viewFactory, viewId, createParams
        )
        mPresentation!!.show()
    }

    fun resize(width: Int, height: Int, onNewSizeFrameAvailable: Runnable) {
        val presentationState = mPresentation!!.detachState()
        // We detach the surface to prevent it being destroyed when releasing the vd.
        //
        // setSurface is only available starting API 20. We could support API 19 by re-creating a new
        // SurfaceTexture here. This will require refactoring the TextureRegistry to allow recycling texture
        // entry IDs.
        mVirtualDisplay!!.surface = null
        mVirtualDisplay!!.release()

        mTextureEntry.surfaceTexture().setDefaultBufferSize(width, height)
        val displayManager = mContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        mVirtualDisplay = displayManager.createVirtualDisplay(
            "flutter-vd",
            width,
            height,
            mDensityDpi,
            mSurface,
            0
        )

        val embeddedView = view
        // There's a bug in Android version older than O where view tree observer onDrawListeners don't get properly
        // merged when attaching to window, as a workaround we register the on draw listener after the view is attached.
        embeddedView!!.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener() {
            fun onViewAttachedToWindow(v: View) {
                OneTimeOnDrawListener.schedule(
                    embeddedView,
                    Runnable {
                        // We need some delay here until the frame propagates through the vd surface to to the texture,
                        // 128ms was picked pretty arbitrarily based on trial and error.
                        // As long as we invoke the runnable after a new frame is available we avoid the scaling jank
                        // described in: https://github.com/flutter/flutter/issues/19572
                        // We should ideally run onNewSizeFrameAvailable ASAP to make the embedded view more responsive
                        // following a resize.
                        embeddedView!!.postDelayed(onNewSizeFrameAvailable, 128)
                    })
                embeddedView!!.removeOnAttachStateChangeListener(this)
            }

            fun onViewDetachedFromWindow(v: View) {}
        })

        mPresentation = SingleViewPresentation(
            mContext,
            mVirtualDisplay!!.display,
            presentationState!!
        )
        mPresentation!!.show()
    }

    fun dispose() {
        val view = mPresentation!!.view
        mPresentation!!.detachState()
        view!!.dispose()
        mVirtualDisplay!!.release()
        mTextureEntry.release()
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    internal class OneTimeOnDrawListener(val mView: View, var mOnDrawRunnable: Runnable?) :
        ViewTreeObserver.OnDrawListener {

        override fun onDraw() {
            if (mOnDrawRunnable == null) {
                return
            }
            mOnDrawRunnable!!.run()
            mOnDrawRunnable = null
            mView.post(Runnable { mView.getViewTreeObserver().removeOnDrawListener(this@OneTimeOnDrawListener) })
        }

        companion object {
            fun schedule(view: View?, runnable: Runnable) {
                val listener =
                    OneTimeOnDrawListener(
                        view,
                        runnable
                    )
                view!!.getViewTreeObserver().addOnDrawListener(listener)
            }
        }
    }
}