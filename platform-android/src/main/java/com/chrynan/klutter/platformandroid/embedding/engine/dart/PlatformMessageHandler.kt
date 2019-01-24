package com.chrynan.klutter.platformandroid.embedding.engine.dart

/**
 * WARNING: THIS CLASS IS EXPERIMENTAL. DO NOT SHIP A DEPENDENCY ON THIS CODE.
 * IF YOU USE IT, WE WILL BREAK YOU.
 */
interface PlatformMessageHandler {

    fun handlePlatformMessage(channel: String, message: ByteArray, replyId: Int)

    fun handlePlatformMessageResponse(replyId: Int, reply: ByteArray)
}