package com.chrynan.klutter.platformandroid.plugin.common

import android.R.attr.order
import jdk.nashorn.internal.objects.ArrayBufferView.buffer
import java.nio.ByteBuffer
import java.nio.ByteBuffer.allocateDirect
import java.nio.ByteOrder

/**
 * A [MethodCodec] using the Flutter standard binary encoding.
 *
 *
 * This codec is guaranteed to be compatible with the corresponding
 * [StandardMethodCodec](https://docs.flutter.io/flutter/services/StandardMethodCodec-class.html)
 * on the Dart side. These parts of the Flutter SDK are evolved synchronously.
 *
 *
 * Values supported as method arguments and result payloads are those supported by
 * [StandardMessageCodec].
 */
class StandardMethodCodec(private val messageCodec: StandardMessageCodec) : MethodCodec {

    companion object {

        val INSTANCE = StandardMethodCodec(StandardMessageCodec.INSTANCE)
    }

    override fun encodeMethodCall(methodCall: MethodCall): ByteBuffer {
        val stream = StandardMessageCodec.ExposedByteArrayOutputStream()
        messageCodec.writeValue(stream, methodCall.method)
        messageCodec.writeValue(stream, methodCall.arguments)
        val buffer = ByteBuffer.allocateDirect(stream.size())
        buffer.put(stream.buffer(), 0, stream.size())
        return buffer
    }

    fun decodeMethodCall(methodCall: ByteBuffer): MethodCall {
        methodCall.order(ByteOrder.nativeOrder())
        val method = messageCodec.readValue(methodCall)
        val arguments = messageCodec.readValue(methodCall)
        if (method is String && !methodCall.hasRemaining()) {
            return MethodCall((method as String?)!!, arguments)
        }
        throw IllegalArgumentException("Method call corrupted")
    }

    override fun encodeSuccessEnvelope(result: Any): ByteBuffer {
        val stream = ExposedByteArrayOutputStream()
        stream.write(0)
        messageCodec.writeValue(stream, result)
        val buffer = ByteBuffer.allocateDirect(stream.size())
        buffer.put(stream.buffer(), 0, stream.size())
        return buffer
    }

    override fun encodeErrorEnvelope(
        errorCode: String, errorMessage: String,
        errorDetails: Any
    ): ByteBuffer {
        val stream = ExposedByteArrayOutputStream()
        stream.write(1)
        messageCodec.writeValue(stream, errorCode)
        messageCodec.writeValue(stream, errorMessage)
        messageCodec.writeValue(stream, errorDetails)
        val buffer = ByteBuffer.allocateDirect(stream.size())
        buffer.put(stream.buffer(), 0, stream.size())
        return buffer
    }

    fun decodeEnvelope(envelope: ByteBuffer): Any? {
        envelope.order(ByteOrder.nativeOrder())
        val flag = envelope.get()
        when (flag) {
            0 -> {
                run {
                    val result = messageCodec.readValue(envelope)
                    if (!envelope.hasRemaining()) {
                        return result
                    }
                }
                run {
                    val code = messageCodec.readValue(envelope)
                    val message = messageCodec.readValue(envelope)
                    val details = messageCodec.readValue(envelope)
                    if (code is String
                        && (message == null || message is String)
                        && !envelope.hasRemaining()
                    ) {
                        throw FlutterException((code as String?)!!, (message as String?)!!, details!!)
                    }
                }
            }
            // Falls through intentionally.
            1 -> {
                val code = messageCodec.readValue(envelope)
                val message = messageCodec.readValue(envelope)
                val details = messageCodec.readValue(envelope)
                if (code is String && (message == null || message is String) && !envelope.hasRemaining()) {
                    throw FlutterException((code as String?)!!, (message as String?)!!, details!!)
                }
            }
        }
        throw IllegalArgumentException("Envelope corrupted")
    }
}