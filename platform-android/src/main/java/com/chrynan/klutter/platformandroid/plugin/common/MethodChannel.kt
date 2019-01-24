package com.chrynan.klutter.platformandroid.plugin.common

import android.content.ContentValues.TAG
import android.util.Log
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger.BinaryReply
import com.chrynan.klutter.platformandroid.plugin.common.BinaryMessenger.BinaryMessageHandler
import org.jetbrains.annotations.Nullable
import java.nio.ByteBuffer

/**
 * A named channel for communicating with the Flutter application using asynchronous
 * method calls.
 *
 *
 * Incoming method calls are decoded from binary on receipt, and Java results are encoded
 * into binary before being transmitted back to Flutter. The [MethodCodec] used must be
 * compatible with the one used by the Flutter application. This can be achieved
 * by creating a
 * [MethodChannel](https://docs.flutter.io/flutter/services/MethodChannel-class.html)
 * counterpart of this channel on the Dart side. The Java type of method call arguments and results is
 * `Object`, but only values supported by the specified [MethodCodec] can be used.
 *
 *
 * The logical identity of the channel is given by its name. Identically named channels will interfere
 * with each other's communication.
 */
class MethodChannel
/**
 * Creates a new channel associated with the specified [BinaryMessenger] and with the
 * specified name and [MethodCodec].
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

        private const val TAG = "MethodChannel#"
    }

    /**
     * Invokes a method on this channel, optionally expecting a result.
     *
     *
     * Any uncaught exception thrown by the result callback will be caught and logged.
     *
     * @param method the name String of the method.
     * @param arguments the arguments for the invocation, possibly null.
     * @param callback a [Result] callback for the invocation result, or null.
     */
    @JvmOverloads
    fun invokeMethod(method: String, @Nullable arguments: Any, callback: Result? = null) {
        messenger.send(
            name, codec.encodeMethodCall(MethodCall(method, arguments)),
            if (callback == null) null else IncomingResultHandler(callback)
        )
    }

    /**
     * Registers a method call handler on this channel.
     *
     *
     * Overrides any existing handler registration for (the name of) this channel.
     *
     *
     * If no handler has been registered, any incoming method call on this channel will be handled
     * silently by sending a null reply. This results in a
     * [MissingPluginException](https://docs.flutter.io/flutter/services/MissingPluginException-class.html)
     * on the Dart side, unless an
     * [OptionalMethodChannel](https://docs.flutter.io/flutter/services/OptionalMethodChannel-class.html)
     * is used.
     *
     * @param handler a [MethodCallHandler], or null to deregister.
     */
    fun setMethodCallHandler(@Nullable handler: MethodCallHandler?) {
        messenger.setMessageHandler(
            name,
            if (handler == null) null else IncomingMethodCallHandler(handler)
        )
    }

    /**
     * A handler of incoming method calls.
     */
    interface MethodCallHandler {
        /**
         * Handles the specified method call received from Flutter.
         *
         *
         * Handler implementations must submit a result for all incoming calls, by making a single call
         * on the given [Result] callback. Failure to do so will result in lingering Flutter result
         * handlers. The result may be submitted asynchronously. Calls to unknown or unimplemented methods
         * should be handled using [Result.notImplemented].
         *
         *
         * Any uncaught exception thrown by this method will be caught by the channel implementation and
         * logged, and an error result will be sent back to Flutter.
         *
         *
         * The handler is called on the platform thread (Android main thread). For more details see
         * [Threading in the Flutter
 * Engine](https://github.com/flutter/engine/wiki/Threading-in-the-Flutter-Engine).
         *
         * @param call A [MethodCall].
         * @param result A [Result] used for submitting the result of the call.
         */
        fun onMethodCall(call: MethodCall, result: Result)
    }

    /**
     * Method call result callback. Supports dual use: Implementations of methods
     * to be invoked by Flutter act as clients of this interface for sending results
     * back to Flutter. Invokers of Flutter methods provide implementations of this
     * interface for handling results received from Flutter.
     *
     *
     * All methods of this class must be called on the platform thread (Android main thread). For more details see
     * [Threading in the Flutter
 * Engine](https://github.com/flutter/engine/wiki/Threading-in-the-Flutter-Engine).
     */
    interface Result {
        /**
         * Handles a successful result.
         *
         * @param result The result, possibly null.
         */
        fun success(@Nullable result: Any)

        /**
         * Handles an error result.
         *
         * @param errorCode An error code String.
         * @param errorMessage A human-readable error message String, possibly null.
         * @param errorDetails Error details, possibly null
         */
        fun error(errorCode: String, @Nullable errorMessage: String, @Nullable errorDetails: Any)

        /**
         * Handles a call to an unimplemented method.
         */
        fun notImplemented()
    }

    private inner class IncomingResultHandler internal constructor(private val callback: Result) : BinaryReply {

        fun reply(reply: ByteBuffer?) {
            try {
                if (reply == null) {
                    callback.notImplemented()
                } else {
                    try {
                        callback.success(codec.decodeEnvelope(reply))
                    } catch (e: FlutterException) {
                        callback.error(e.code, e.message, e.details)
                    }

                }
            } catch (e: RuntimeException) {
                Log.e(TAG + name, "Failed to handle method call result", e)
            }

        }
    }

    private inner class IncomingMethodCallHandler internal constructor(private val handler: MethodCallHandler) :
        BinaryMessageHandler {

        fun onMessage(message: ByteBuffer, reply: BinaryReply) {
            val call = codec.decodeMethodCall(message)
            try {
                handler.onMethodCall(call, object : Result {
                    override fun success(result: Any) {
                        reply.reply(codec.encodeSuccessEnvelope(result))
                    }

                    override fun error(errorCode: String, errorMessage: String, errorDetails: Any) {
                        reply.reply(codec.encodeErrorEnvelope(errorCode, errorMessage, errorDetails))
                    }

                    override fun notImplemented() {
                        reply.reply(null!!)
                    }
                })
            } catch (e: RuntimeException) {
                Log.e(TAG + name, "Failed to handle method call", e)
                reply.reply(codec.encodeErrorEnvelope("error", e.message, null))
            }

        }
    }
}