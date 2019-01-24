package com.chrynan.klutter.platformandroid.plugin.platform

import com.sun.jmx.remote.util.EnvHelp.getCause
import android.content.Context.WINDOW_SERVICE
import android.content.ContextWrapper
import android.widget.FrameLayout
import android.os.Bundle
import netscape.javascript.JSObject.getWindow
import android.app.Presentation
import android.os.Build
import android.annotation.TargetApi
import android.content.Context
import android.util.Log
import android.view.*
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method


/*
 * A presentation used for hosting a single Android view in a virtual display.
 *
 * This presentation overrides the WindowManager's addView/removeView/updateViewLayout methods, such that views added
 * directly to the WindowManager are added as part of the presentation's view hierarchy (to mFakeWindowRootView).
 *
 * The view hierarchy for the presentation is as following:
 *
 *          mRootView
 *         /         \
 *        /           \
 *       /             \
 *   mContainer       mState.mFakeWindowRootView
 *      |
 *   EmbeddedView
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
internal class SingleViewPresentation : Presentation {

    companion object {

        private val TAG = "PlatformViewsController"
    }

    private val mViewFactory: PlatformViewFactory?

    // This is the view id assigned by the Flutter framework to the embedded view, we keep it here
    // so when we create the platform view we can tell it its view id.
    private val mViewId: Int

    // This is the creation parameters for the platform view, we keep it here
    // so when we create the platform view we can tell it its view id.
    private val mCreateParams: Any

    // The root view for the presentation, it has 2 childs: mContainer which contains the embedded view, and
    // mFakeWindowRootView which contains views that were added directly to the presentation's window manager.
    private var mRootView: FrameLayout? = null

    // Contains the embedded platform view (mView.getView()) when it is attached to the presentation.
    private var mContainer: FrameLayout? = null

    private var mState: PresentationState? = null

    val view: PlatformView?
        get() = if (mState!!.mView == null) null else mState!!.mView

    /*
     * When an embedded view is resized in Flutterverse we move the Android view to a new virtual display
     * that has the new size. This class keeps the presentation state that moves with the view to the presentation of
     * the new virtual display.
     */
    internal class PresentationState {
        // The Android view we are embedding in the Flutter app.
        private val mView: PlatformView? = null

        // The InvocationHandler for a WindowManager proxy. This is essentially the custom window manager for the
        // presentation.
        private val mWindowManagerHandler: WindowManagerHandler? = null

        // Contains views that were added directly to the window manager (e.g android.widget.PopupWindow).
        private val mFakeWindowRootView: FakeWindowViewGroup? = null
    }

    /**
     * Creates a presentation that will use the view factory to create a new
     * platform view in the presentation's onCreate, and attach it.
     */
    constructor(
        outerContext: Context,
        display: Display,
        viewFactory: PlatformViewFactory,
        viewId: Int,
        createParams: Any
    ) : super(outerContext, display) {
        mViewFactory = viewFactory
        mViewId = viewId
        mCreateParams = createParams
        mState = PresentationState()
        window!!.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }

    /**
     * Creates a presentation that will attach an already existing view as
     * its root view.
     *
     *
     * The display's density must match the density of the context used
     * when the view was created.
     */
    constructor(outerContext: Context, display: Display, state: PresentationState) : super(outerContext, display) {
        mViewFactory = null
        mState = state
        window!!.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }

    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        if (mState!!.mFakeWindowRootView == null) {
            mState!!.mFakeWindowRootView =
                    FakeWindowViewGroup(
                        context
                    )
        }
        if (mState!!.mWindowManagerHandler == null) {
            val windowManagerDelegate = context.getSystemService(WINDOW_SERVICE) as WindowManager
            mState!!.mWindowManagerHandler =
                    WindowManagerHandler(
                        windowManagerDelegate,
                        mState!!.mFakeWindowRootView
                    )
        }

        mContainer = FrameLayout(context)
        val context = PresentationContext(
            context,
            mState!!.mWindowManagerHandler
        )

        if (mState!!.mView == null) {
            mState!!.mView = mViewFactory!!.create(context, mViewId, mCreateParams)
        }

        mContainer!!.addView(mState!!.mView!!.view)
        mRootView = FrameLayout(getContext())
        mRootView!!.addView(mContainer)
        mRootView!!.addView(mState!!.mFakeWindowRootView)
        setContentView(mRootView!!)
    }

    fun detachState(): PresentationState? {
        mContainer!!.removeAllViews()
        mRootView!!.removeAllViews()
        return mState
    }

    /*
     * A view group that implements the same layout protocol that exist between the WindowManager and its direct
     * children.
     *
     * Currently only a subset of the protocol is supported (gravity, x, and y).
     */
    internal class FakeWindowViewGroup(context: Context) : ViewGroup(context) {
        // Used in onLayout to keep the bounds of the current view.
        // We keep it as a member to avoid object allocations during onLayout which are discouraged.
        private val mViewBounds: Rect

        // Used in onLayout to keep the bounds of the child views.
        // We keep it as a member to avoid object allocations during onLayout which are discouraged.
        private val mChildRect: Rect

        init {
            mViewBounds = Rect()
            mChildRect = Rect()
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val params = child.layoutParams as WindowManager.LayoutParams
                mViewBounds.set(l, t, r, b)
                Gravity.apply(
                    params.gravity, child.measuredWidth, child.measuredHeight, mViewBounds, params.x,
                    params.y, mChildRect
                )
                child.layout(mChildRect.left, mChildRect.top, mChildRect.right, mChildRect.bottom)
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.measure(atMost(widthMeasureSpec), atMost(heightMeasureSpec))
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        private fun atMost(measureSpec: Int): Int {
            return MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(measureSpec), MeasureSpec.AT_MOST)
        }
    }

    /**
     * Proxies a Context replacing the WindowManager with our custom instance.
     */
    internal class PresentationContext(base: Context, private val mWindowManagerHandler: WindowManagerHandler) :
        ContextWrapper(base) {
        private var mWindowManager: WindowManager? = null

        private val windowManager: WindowManager
            get() {
                if (mWindowManager == null) {
                    mWindowManager = mWindowManagerHandler.windowManager
                }
                return mWindowManager
            }

        override fun getSystemService(name: String): Any? {
            return if (WINDOW_SERVICE == name) {
                windowManager
            } else super.getSystemService(name)
        }
    }

    /*
     * A dynamic proxy handler for a WindowManager with custom overrides.
     *
     * The presentation's window manager delegates all calls to the default window manager.
     * WindowManager#addView calls triggered by views that are attached to the virtual display are crashing
     * (see: https://github.com/flutter/flutter/issues/20714). This was triggered when selecting text in an embedded
     * WebView (as the selection handles are implemented as popup windows).
     *
     * This dynamic proxy overrides the addView, removeView, removeViewImmediate, and updateViewLayout methods
     * to prevent these crashes.
     *
     * This will be more efficient as a static proxy that's not using reflection, but as the engine is currently
     * not being built against the latest Android SDK we cannot override all relevant method.
     * Tracking issue for upgrading the engine's Android sdk: https://github.com/flutter/flutter/issues/20717
     */
    internal class WindowManagerHandler(
        private val mDelegate: WindowManager,
        var mFakeWindowRootView: FakeWindowViewGroup?
    ) : InvocationHandler {

        val windowManager: WindowManager
            get() = Proxy.newProxyInstance(
                WindowManager::class.java.classLoader,
                arrayOf<Class<*>>(WindowManager::class.java),
                this
            )

        @Throws(Throwable::class)
        operator fun invoke(proxy: Any, method: Method, args: Array<Any>): Any? {
            when (method.getName()) {
                "addView" -> {
                    addView(args)
                    return null
                }
                "removeView" -> {
                    removeView(args)
                    return null
                }
                "removeViewImmediate" -> {
                    removeViewImmediate(args)
                    return null
                }
                "updateViewLayout" -> {
                    updateViewLayout(args)
                    return null
                }
            }
            try {
                return method.invoke(mDelegate, args)
            } catch (e: InvocationTargetException) {
                throw e.getCause()
            }

        }

        private fun addView(args: Array<Any>) {
            if (mFakeWindowRootView == null) {
                Log.w(TAG, "Embedded view called addView while detached from presentation")
                return
            }
            val view = args[0] as View
            val layoutParams = args[1] as WindowManager.LayoutParams
            mFakeWindowRootView!!.addView(view, layoutParams)
        }

        private fun removeView(args: Array<Any>) {
            if (mFakeWindowRootView == null) {
                Log.w(TAG, "Embedded view called removeView while detached from presentation")
                return
            }
            val view = args[0] as View
            mFakeWindowRootView!!.removeView(view)
        }

        private fun removeViewImmediate(args: Array<Any>) {
            if (mFakeWindowRootView == null) {
                Log.w(TAG, "Embedded view called removeViewImmediate while detached from presentation")
                return
            }
            val view = args[0] as View
            view.clearAnimation()
            mFakeWindowRootView!!.removeView(view)
        }

        private fun updateViewLayout(args: Array<Any>) {
            if (mFakeWindowRootView == null) {
                Log.w(TAG, "Embedded view called updateViewLayout while detached from presentation")
                return
            }
            val view = args[0] as View
            val layoutParams = args[1] as WindowManager.LayoutParams
            mFakeWindowRootView!!.updateViewLayout(view, layoutParams)
        }
    }
}