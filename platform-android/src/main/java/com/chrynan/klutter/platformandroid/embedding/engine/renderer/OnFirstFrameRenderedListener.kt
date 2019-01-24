package com.chrynan.klutter.platformandroid.embedding.engine.renderer

/**
 * Listener invoked after Flutter paints its first frame since being initialized.
 *
 * WARNING: THIS CLASS IS EXPERIMENTAL. DO NOT SHIP A DEPENDENCY ON THIS CODE.
 * IF YOU USE IT, WE WILL BREAK YOU.
 */
interface OnFirstFrameRenderedListener {

    /**
     * A [FlutterRenderer] has painted its first frame since being initialized.
     *
     * This method will not be invoked if this listener is added after the first frame is rendered.
     */
    fun onFirstFrameRendered()
}