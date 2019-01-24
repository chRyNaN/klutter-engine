package com.chrynan.klutter.platformandroid.view

import android.util.Log
import com.chrynan.klutter.platformandroid.embedding.engine.FlutterJNI
import com.chrynan.klutter.platformandroid.embedding.engine.dart.PlatformMessageHandler
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class PlatformMessageHandlerImpl(
    private val tag: String,
    private val assertAttached: () -> Unit,
    private val mMessageHandlers: Map<String, BinaryMessenger.BinaryMessageHandler>,
    private val pendingRepliesRemover: (Int) -> BinaryMessenger.BinaryReply?,
    private val flutterJNI: FlutterJNI
) : PlatformMessageHandler {

    private val isAttached: Boolean
        get() = flutterJNI.isAttached

    // Called by native to send us a platform message.
    override fun handlePlatformMessage(channel: String, message: ByteArray, replyId: Int) {
        assertAttached()
        val handler = mMessageHandlers[channel]
        if (handler != null) {
            try {
                val buffer = ByteBuffer.wrap(message)

                handler.onMessage(buffer!!, object : BinaryMessenger.BinaryReply {
                    private val done = AtomicBoolean(false)

                    override fun reply(reply: ByteBuffer?) {
                        if (!isAttached) {
                            Log.d(tag, "handlePlatformMessage replying ot a detached view, channel=$channel")
                            return
                        }

                        if (done.getAndSet(true)) throw IllegalStateException("Reply already submitted")

                        if (reply == null) {
                            flutterJNI.invokePlatformMessageEmptyResponseCallback(replyId)
                        } else {
                            flutterJNI.invokePlatformMessageResponseCallback(replyId, reply, reply.position())
                        }
                    }
                })
            } catch (exception: Exception) {
                Log.e(tag, "Uncaught exception in binary message listener", exception)
                flutterJNI.invokePlatformMessageEmptyResponseCallback(replyId)
            }

            return
        }

        flutterJNI.invokePlatformMessageEmptyResponseCallback(replyId)
    }

    // Called by native to respond to a platform message that we sent.
    override fun handlePlatformMessageResponse(replyId: Int, reply: ByteArray) {
        val callback = pendingRepliesRemover(replyId)

        if (callback != null) {
            try {
                callback.reply(ByteBuffer.wrap(reply))
            } catch (ex: Exception) {
                Log.e(tag, "Uncaught exception in binary message reply handler", ex)
            }
        }
    }
}