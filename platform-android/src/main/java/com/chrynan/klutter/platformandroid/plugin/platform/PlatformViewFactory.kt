package com.chrynan.klutter.platformandroid.plugin.platform

import android.content.Context

/**
 *
 * @param createArgsCodec the codec used to decode the args parameter of [.create].
 * Returns the codec to be used for decoding the args parameter of [.create].
 */
abstract class PlatformViewFactory(val createArgsCodec: MessageCodec<Any>) {

    /**
     * Creates a new Android view to be embedded in the Flutter hierarchy.
     *
     * @param context the context to be used when creating the view, this is different than FlutterView's context.
     * @param viewId unique identifier for the created instance, this value is known on the Dart side.
     * @param args arguments sent from the Flutter app. The bytes for this value are decoded using the createArgsCodec
     * argument passed to the constructor. This is null if createArgsCodec was null, or no arguments were
     * sent from the Flutter app.
     */
    abstract fun create(context: Context, viewId: Int, args: Any): PlatformView
}