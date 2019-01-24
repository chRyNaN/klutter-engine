package com.chrynan.klutter.platformandroid.plugin.common

import android.content.ContentValues.TAG
import android.util.Log
import java.nio.ByteBuffer

/**
 * A named channel for communicating with the Flutter application using basic, asynchronous message passing.
 *
 *
 * Messages are encoded into binary before being sent, and binary messages received are decoded
 * into Java objects. The [MessageCodec] used must be compatible with the
 * one used by the Flutter application. This can be achieved by creating a
 * [BasicMessageChannel](https://docs.flutter.io/flutter/services/BasicMessageChannel-class.html)
 * counterpart of this channel on the Dart side. The static Java type of messages sent and received
 * is `Object`, but only values supported by the specified [MessageCodec] can be used.
 *
 *
 * The logical identity of the channel is given by its name. Identically named channels will interfere
 * with each other's communication.
 *
 * Creates a new channel associated with the specified [BinaryMessenger]
 * and with the specified name and [MessageCodec].
 *
 * @param messenger a [BinaryMessenger].
 * @param name a channel name String.
 * @param codec a [MessageCodec].
 */
class BasicMessageChannel<T>(
    private val messenger: BinaryMessenger,
    private val name: String,
    private val codec: MessageCodec<T>
) {

    companion object {

        private val TAG = "BasicMessageChannel#"
    }

    /**
     * Sends the specified message to the Flutter application, optionally expecting a reply.
     *
     *
     * Any uncaught exception thrown by the reply callback will be caught and logged.
     *
     * @param message the message, possibly null.
     * @param callback a [Reply] callback, possibly null.
     */
    @JvmOverloads
    fun send(message: T, callback: Reply<T>? = null) {
        messenger.send(
            name, codec.encodeMessage(message),
            if (callback == null) null else IncomingReplyHandler(callback)
        )
    }

    /**
     * Registers a message handler on this channel for receiving messages sent from the Flutter
     * application.
     *
     *
     * Overrides any existing handler registration for (the name of) this channel.
     *
     *
     * If no handler has been registered, any incoming message on this channel will be handled silently
     * by sending a null reply.
     *
     * @param handler a [MessageHandler], or null to deregister.
     */
    fun setMessageHandler(handler: MessageHandler<T>?) {
        messenger.setMessageHandler(
            name,
            if (handler == null) null else IncomingMessageHandler(handler)
        )
    }

    /**
     * A handler of incoming messages.
     */
    interface MessageHandler<T> {

        /**
         * Handles the specified message received from Flutter.
         *
         *
         * Handler implementations must reply to all incoming messages, by submitting a single reply
         * message to the given [Reply]. Failure to do so will result in lingering Flutter reply
         * handlers. The reply may be submitted asynchronously.
         *
         *
         * Any uncaught exception thrown by this method, or the preceding message decoding, will be
         * caught by the channel implementation and logged, and a null reply message will be sent back
         * to Flutter.
         *
         *
         * Any uncaught exception thrown during encoding a reply message submitted to the [Reply]
         * is treated similarly: the exception is logged, and a null reply is sent to Flutter.
         *
         * @param message the message, possibly null.
         * @param reply a [Reply] for sending a single message reply back to Flutter.
         */
        fun onMessage(message: T, reply: Reply<T>)
    }

    /**
     * Message reply callback. Used to submit a reply to an incoming
     * message from Flutter. Also used in the dual capacity to handle a reply
     * received from Flutter after sending a message.
     */
    interface Reply<T> {
        /**
         * Handles the specified message reply.
         *
         * @param reply the reply, possibly null.
         */
        fun reply(reply: T)
    }

    private inner class IncomingReplyHandler private constructor(private val callback: Reply<T>) : BinaryReply {

        fun reply(reply: ByteBuffer) {
            try {
                callback.reply(codec.decodeMessage(reply))
            } catch (e: RuntimeException) {
                Log.e(TAG + name, "Failed to handle message reply", e)
            }

        }
    }

    private inner class IncomingMessageHandler private constructor(private val handler: MessageHandler<T>) :
        BinaryMessageHandler {

        fun onMessage(message: ByteBuffer, callback: BinaryReply) {
            try {
                handler.onMessage(codec.decodeMessage(message), object : Reply<T> {
                    override fun reply(reply: T) {
                        callback.reply(codec.encodeMessage(reply))
                    }
                })
            } catch (e: RuntimeException) {
                Log.e(TAG + name, "Failed to handle message", e)
                callback.reply(null)
            }

        }
    }
}