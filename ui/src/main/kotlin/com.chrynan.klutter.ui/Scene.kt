package com.chrynan.klutter.ui

import kotlinx.coroutines.Deferred

expect class Scene {

    suspend fun toImage(width: Int, height: Int): Deferred<Image>

    fun dispose()
}