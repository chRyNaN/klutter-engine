package com.chrynan.klutter.platformandroid.view

import com.chrynan.klutter.platformandroid.embedding.engine.renderer.FlutterRenderer
import java.nio.ByteBuffer

class RenderSurfaceImpl(private val flutterView: FlutterView?) : FlutterRenderer.RenderSurface {

    // Called by native to update the semantics/accessibility tree.
    override fun updateSemantics(buffer: ByteBuffer, strings: Array<String>) {
        flutterView?.updateSemantics(buffer, strings)
    }

    // Called by native to update the custom accessibility actions.
    override fun updateCustomAccessibilityActions(buffer: ByteBuffer, strings: Array<String>) {
        flutterView?.updateCustomAccessibilityActions(buffer, strings)
    }

    // Called by native to notify first Flutter frame rendered.
    override fun onFirstFrameRendered() {
        flutterView?.onFirstFrame()
    }
}