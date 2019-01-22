package com.chrynan.klutter.ui

import kotlinx.coroutines.Deferred

expect class Codec {

    val frameCount: Int

    val repetitionCount: Int

    suspend fun getNextFrame(): Deferred<FrameInfo>

    fun dispose()
}