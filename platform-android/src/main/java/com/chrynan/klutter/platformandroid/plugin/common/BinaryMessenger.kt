package com.chrynan.klutter.platformandroid.plugin.common

import java.nio.ByteBuffer

/**
 * Facility for communicating with Flutter using asynchronous message passing with binary messages.
 * The Flutter Dart code should use
 * [BinaryMessages](https://docs.flutter.io/flutter/services/BinaryMessages-class.html)
 * to participate.
 *
 * @see BasicMessageChannel , which supports message passing with Strings and semi-structured messages.
 *
 * @see MethodChannel , which supports communication using asynchronous method invocation.
 *
 * @see EventChannel , which supports communication using event streams.
 */
interface BinaryMessenger {
    /**
     * Sends a binary message to the Flutter application.
     *
     * @param channel the name [String] of the logical channel used for the message.
     * @param message the message payload, a direct-allocated [ByteBuffer] with the message bytes
     * between position zero and current position, or null.
     */
    fun send(channel: String, message: ByteBuffer)

    /**
     * Sends a binary message to the Flutter application, optionally expecting a reply.
     *
     *
     * Any uncaught exception thrown by the reply callback will be caught and logged.
     *
     * @param channel the name [String] of the logical channel used for the message.
     * @param message the message payload, a direct-allocated [ByteBuffer] with the message bytes
     * between position zero and current position, or null.
     * @param callback a [BinaryReply] callback invoked when the Flutter application responds to the
     * message, possibly null.
     */
    fun send(channel: String, message: ByteBuffer?, callback: BinaryReply?)

    /**
     * Registers a handler to be invoked when the Flutter application sends a message
     * to its host platform.
     *
     *
     * Registration overwrites any previous registration for the same channel name.
     * Use a null handler to deregister.
     *
     *
     * If no handler has been registered for a particular channel, any incoming message on
     * that channel will be handled silently by sending a null reply.
     *
     * @param channel the name [String] of the channel.
     * @param handler a [BinaryMessageHandler] to be invoked on incoming messages, or null.
     */
    fun setMessageHandler(channel: String, handler: BinaryMessageHandler?)

    /**
     * Handler for incoming binary messages from Flutter.
     */
    interface BinaryMessageHandler {
        /**
         * Handles the specified message.
         *
         *
         * Handler implementations must reply to all incoming messages, by submitting a single reply
         * message to the given [BinaryReply]. Failure to do so will result in lingering Flutter reply
         * handlers. The reply may be submitted asynchronously.
         *
         *
         * Any uncaught exception thrown by this method will be caught by the messenger implementation and
         * logged, and a null reply message will be sent back to Flutter.
         *
         * @param message the message [ByteBuffer] payload, possibly null.
         * @param reply A [BinaryReply] used for submitting a reply back to Flutter.
         */
        fun onMessage(message: ByteBuffer, reply: BinaryReply)
    }

    /**
     * Binary message reply callback. Used to submit a reply to an incoming
     * message from Flutter. Also used in the dual capacity to handle a reply
     * received from Flutter after sending a message.
     */
    interface BinaryReply {

        /**
         * Handles the specified reply.
         *
         * @param reply the reply payload, a direct-allocated [ByteBuffer] or null. Senders of
         * outgoing replies must place the reply bytes between position zero and current position.
         * Reply receivers can read from the buffer directly.
         */
        fun reply(reply: ByteBuffer?)
    }
}