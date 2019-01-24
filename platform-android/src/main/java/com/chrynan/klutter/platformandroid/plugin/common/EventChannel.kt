package com.chrynan.klutter.platformandroid.plugin.common

import android.content.ContentValues.TAG
import android.util.Log
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger.BinaryReply
import org.junit.runner.Request.method
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger.BinaryMessageHandler
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A named channel for communicating with the Flutter application using asynchronous
 * event streams.
 *
 *
 * Incoming requests for event stream setup are decoded from binary on receipt, and
 * Java responses and events are encoded into binary before being transmitted back
 * to Flutter. The [MethodCodec] used must be compatible with the one used by
 * the Flutter application. This can be achieved by creating an
 * [EventChannel](https://docs.flutter.io/flutter/services/EventChannel-class.html)
 * counterpart of this channel on the Dart side. The Java type of stream configuration arguments,
 * events, and error details is `Object`, but only values supported by the specified
 * [MethodCodec] can be used.
 *
 *
 * The logical identity of the channel is given by its name. Identically named channels will interfere
 * with each other's communication.
 */
class EventChannel
/**
 * Creates a new channel associated with the specified [BinaryMessenger]
 * and with the specified name and [MethodCodec].
 *
 * @param messenger a [BinaryMessenger].
 * @param name a channel name String.
 * @param codec a [MessageCodec].
 */
@JvmOverloads constructor(
    private val messenger: BinaryMessenger,
    private val name: String,
    private val codec: MethodCodec = StandardMethodCodec.INSTANCE
) {

    companion object {

        private const val TAG = "EventChannel#"
    }

    /**
     * Registers a stream handler on this channel.
     *
     *
     * Overrides any existing handler registration for (the name of) this channel.
     *
     *
     * If no handler has been registered, any incoming stream setup requests will be handled
     * silently by providing an empty stream.
     *
     * @param handler a [StreamHandler], or null to deregister.
     */
    fun setStreamHandler(handler: StreamHandler?) {
        messenger.setMessageHandler(name, if (handler == null) null else IncomingStreamRequestHandler(handler))
    }

    /**
     * Handler of stream setup and tear-down requests.
     *
     *
     * Implementations must be prepared to accept sequences of alternating calls to
     * [.onListen] and [.onCancel]. Implementations
     * should ideally consume no resources when the last such call is not `onListen`.
     * In typical situations, this means that the implementation should register itself
     * with platform-specific event sources `onListen` and deregister again
     * `onCancel`.
     */
    interface StreamHandler {
        /**
         * Handles a request to set up an event stream.
         *
         *
         * Any uncaught exception thrown by this method will be caught by the channel
         * implementation and logged. An error result message will be sent back to Flutter.
         *
         * @param arguments stream configuration arguments, possibly null.
         * @param events an [EventSink] for emitting events to the Flutter receiver.
         */
        fun onListen(arguments: Any, events: EventSink)

        /**
         * Handles a request to tear down the most recently created event stream.
         *
         *
         * Any uncaught exception thrown by this method will be caught by the channel
         * implementation and logged. An error result message will be sent back to Flutter.
         *
         *
         * The channel implementation may call this method with null arguments
         * to separate a pair of two consecutive set up requests. Such request pairs
         * may occur during Flutter hot restart. Any uncaught exception thrown
         * in this situation will be logged without notifying Flutter.
         *
         * @param arguments stream configuration arguments, possibly null.
         */
        fun onCancel(arguments: Any?)
    }

    /**
     * Event callback. Supports dual use: Producers of events to be sent to Flutter
     * act as clients of this interface for sending events. Consumers of events sent
     * from Flutter implement this interface for handling received events (the latter
     * facility has not been implemented yet).
     */
    interface EventSink {
        /**
         * Consumes a successful event.
         *
         * @param event the event, possibly null.
         */
        fun success(event: Any)

        /**
         * Consumes an error event.
         *
         * @param errorCode an error code String.
         * @param errorMessage a human-readable error message String, possibly null.
         * @param errorDetails error details, possibly null
         */
        fun error(errorCode: String, errorMessage: String, errorDetails: Any)

        /**
         * Consumes end of stream. Ensuing calls to [.success] or
         * [.error], if any, are ignored.
         */
        fun endOfStream()
    }

    private inner class IncomingStreamRequestHandler internal constructor(private val handler: StreamHandler) :
        BinaryMessageHandler {
        private val activeSink = AtomicReference(null)

        fun onMessage(message: ByteBuffer, reply: BinaryReply) {
            val call = codec.decodeMethodCall(message)
            if (call.method.equals("listen")) {
                onListen(call.arguments, reply)
            } else if (call.method.equals("cancel")) {
                onCancel(call.arguments, reply)
            } else {
                reply.reply(null!!)
            }
        }

        private fun onListen(arguments: Any, callback: BinaryReply) {
            val eventSink = EventSinkImplementation()
            val oldSink = activeSink.getAndSet(eventSink)
            if (oldSink != null) {
                // Repeated calls to onListen may happen during hot restart.
                // We separate them with a call to onCancel.
                try {
                    handler.onCancel(null)
                } catch (e: RuntimeException) {
                    Log.e(TAG + name, "Failed to close existing event stream", e)
                }

            }
            try {
                handler.onListen(arguments, eventSink)
                callback.reply(codec.encodeSuccessEnvelope(null))
            } catch (e: RuntimeException) {
                activeSink.set(null)
                Log.e(TAG + name, "Failed to open event stream", e)
                callback.reply(codec.encodeErrorEnvelope("error", e.message, null))
            }

        }

        private fun onCancel(arguments: Any, callback: BinaryReply) {
            val oldSink = activeSink.getAndSet(null)
            if (oldSink != null) {
                try {
                    handler.onCancel(arguments)
                    callback.reply(codec.encodeSuccessEnvelope(null))
                } catch (e: RuntimeException) {
                    Log.e(TAG + name, "Failed to close event stream", e)
                    callback.reply(codec.encodeErrorEnvelope("error", e.message, null))
                }

            } else {
                callback.reply(codec.encodeErrorEnvelope("error", "No active stream to cancel", null))
            }
        }

        private inner class EventSinkImplementation : EventSink {
            internal val hasEnded = AtomicBoolean(false)

            override fun success(event: Any) {
                if (hasEnded.get() || activeSink.get() !== this) {
                    return
                }
                this@EventChannel.messenger.send(name, codec.encodeSuccessEnvelope(event))
            }

            override fun error(errorCode: String, errorMessage: String, errorDetails: Any) {
                if (hasEnded.get() || activeSink.get() !== this) {
                    return
                }
                this@EventChannel.messenger.send(
                    name,
                    codec.encodeErrorEnvelope(errorCode, errorMessage, errorDetails)
                )
            }

            override fun endOfStream() {
                if (hasEnded.getAndSet(true) || activeSink.get() !== this) {
                    return
                }
                this@EventChannel.messenger.send(name, null!!)
            }
        }
    }
}