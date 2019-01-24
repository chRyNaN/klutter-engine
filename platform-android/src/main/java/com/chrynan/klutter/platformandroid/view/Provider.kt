package com.chrynan.klutter.platformandroid.view

/**
 * Interface for those objects that maintain and expose a reference to a
 * `FlutterView` (such as a full-screen Flutter activity).
 *
 *
 *
 * This indirection is provided to support applications that use an activity
 * other than [io.flutter.app.FlutterActivity] (e.g. Android v4 support
 * library's `FragmentActivity`). It allows Flutter plugins to deal in
 * this interface and not require that the activity be a subclass of
 * `FlutterActivity`.
 *
 */
interface Provider {

    /**
     * Returns a reference to the Flutter view maintained by this object. This may
     * be `null`.
     */
    val flutterView: FlutterView
}