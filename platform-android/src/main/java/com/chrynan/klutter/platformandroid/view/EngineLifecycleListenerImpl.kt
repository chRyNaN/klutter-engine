package com.chrynan.klutter.platformandroid.view

import com.chrynan.klutter.platformandroid.app.FlutterPluginRegistry
import com.chrynan.klutter.platformandroid.embedding.engine.FlutterEngine

class EngineLifecycleListenerImpl(
    private val flutterView: FlutterView?,
    private val pluginRegistry: FlutterPluginRegistry?
) : FlutterEngine.EngineLifecycleListener {

    // Called by native to notify when the engine is restarted (cold reload).
    override fun onPreEngineRestart() {
        flutterView?.resetAccessibilityTree()
        pluginRegistry?.onPreEngineRestart()
    }
}