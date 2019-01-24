package com.chrynan.klutter.platformandroid.embedding.engine

/**
 * A single Flutter execution environment.
 *
 * WARNING: THIS CLASS IS EXPERIMENTAL. DO NOT SHIP A DEPENDENCY ON THIS CODE.
 * IF YOU USE IT, WE WILL BREAK YOU.
 *
 * A `FlutterEngine` can execute in the background, or it can be rendered to the screen by
 * using the accompanying [FlutterRenderer].  Rendering can be started and stopped, thus
 * allowing a `FlutterEngine` to move from UI interaction to data-only processing and then
 * back to UI interaction.
 *
 * Multiple `FlutterEngine`s may exist, execute Dart code, and render UIs within a single
 * Android app.
 *
 * To start running Flutter within this `FlutterEngine`, get a reference to this engine's
 * [DartExecutor] and then use [DartExecutor.runFromBundle].
 * The [DartExecutor.runFromBundle] method must not be invoked twice on the same
 * `FlutterEngine`.
 *
 * To start rendering Flutter content to the screen, use [.getRenderer] to obtain a
 * [FlutterRenderer] and then attach a [FlutterRenderer.RenderSurface].  Consider using
 * a [io.flutter.embedding.android.FlutterView] as a [FlutterRenderer.RenderSurface].
 */
class FlutterEngine {
    // TODO(mattcarroll): bring in FlutterEngine implementation in future PR

    /**
     * Lifecycle callbacks for Flutter engine lifecycle events.
     */
    interface EngineLifecycleListener {
        /**
         * Lifecycle callback invoked before a hot restart of the Flutter engine.
         */
        fun onPreEngineRestart()
    }
}