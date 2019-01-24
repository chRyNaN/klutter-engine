package com.chrynan.klutter.platformandroid.embedding.engine.renderer

import java.nio.ByteBuffer

/**
 * Delegate used in conjunction with a [FlutterRenderer] to create an interactive Flutter
 * UI.
 *
 * A `RenderSurface` is responsible for carrying out behaviors that are needed by a
 * corresponding [FlutterRenderer], e.g., [.updateSemantics].
 *
 * A `RenderSurface` also receives callbacks for important events, e.g.,
 * [.onFirstFrameRendered].
 */
interface RenderSurface {

    // TODO(mattcarroll): describe what this callback is intended to do
    fun updateCustomAccessibilityActions(buffer: ByteBuffer, strings: Array<String>)

    // TODO(mattcarroll): describe what this callback is intended to do
    fun updateSemantics(buffer: ByteBuffer, strings: Array<String>)

    /**
     * The [FlutterRenderer] corresponding to this `RenderSurface` has painted its
     * first frame since being initialized.
     *
     * "Initialized" refers to Flutter engine initialization, not the first frame after attaching
     * to the [FlutterRenderer]. Therefore, the first frame may have already rendered by
     * the time a `RenderSurface` has called [.attachToRenderSurface]
     * on a [FlutterRenderer]. In such a situation, `#onFirstFrameRendered()` will
     * never be called.
     */
    fun onFirstFrameRendered()
}