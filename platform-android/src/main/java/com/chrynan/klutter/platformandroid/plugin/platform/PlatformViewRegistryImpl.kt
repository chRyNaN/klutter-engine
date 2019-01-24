package com.chrynan.klutter.platformandroid.plugin.platform

internal class PlatformViewRegistryImpl : PlatformViewRegistry {

    // Maps a platform view type id to its factory.
    private val viewFactories: MutableMap<String, PlatformViewFactory> = HashMap()

    override fun registerViewFactory(viewTypeId: String, factory: PlatformViewFactory): Boolean {
        if (viewFactories.containsKey(viewTypeId)) return false

        viewFactories[viewTypeId] = factory

        return true
    }

    fun getFactory(viewTypeId: String): PlatformViewFactory = viewFactories[viewTypeId] ?: throw NullPointerException()
}