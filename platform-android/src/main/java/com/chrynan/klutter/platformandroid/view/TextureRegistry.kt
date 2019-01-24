package com.chrynan.klutter.platformandroid.view

import android.graphics.SurfaceTexture


/**
 * Registry of backend textures used with a single [FlutterView] instance.
 * Entries may be embedded into the Flutter view using the
 * [Texture](https://docs.flutter.io/flutter/widgets/Texture-class.html)
 * widget.
 */
interface TextureRegistry {

    /**
     * Creates and registers a SurfaceTexture managed by the Flutter engine.
     *
     * @return A SurfaceTextureEntry.
     */
    fun createSurfaceTexture(): SurfaceTextureEntry

    /**
     * A registry entry for a managed SurfaceTexture.
     */
    interface SurfaceTextureEntry {
        /**
         * @return The managed SurfaceTexture.
         */
        fun surfaceTexture(): SurfaceTexture

        /**
         * @return The identity of this SurfaceTexture.
         */
        fun id(): Long

        /**
         * Deregisters and releases this SurfaceTexture.
         */
        fun release()
    }
}