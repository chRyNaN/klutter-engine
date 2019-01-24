package com.chrynan.klutter.platformandroid.plugin.common

import java.nio.ByteBuffer

/**
 * A codec for method calls and enveloped results.
 *
 * Method calls are encoded as binary messages with enough structure that the codec can
 * extract a method name String and an arguments Object. These data items are used to populate a
 * [MethodCall].
 *
 * All operations throw [IllegalArgumentException], if conversion fails.
 */
interface MethodCodec {

    /**
     * Encodes a message call into binary.
     *
     * @param methodCall a [MethodCall].
     * @return a [ByteBuffer] containing the encoding between position 0 and
     * the current position.
     */
    fun encodeMethodCall(methodCall: MethodCall): ByteBuffer

    /**
     * Decodes a message call from binary.
     *
     * @param methodCall the binary encoding of the method call as a [ByteBuffer].
     * @return a [MethodCall] representation of the bytes between the given buffer's current
     * position and its limit.
     */
    fun decodeMethodCall(methodCall: ByteBuffer): MethodCall

    /**
     * Encodes a successful result into a binary envelope message.
     *
     * @param result The result value, possibly null.
     * @return a [ByteBuffer] containing the encoding between position 0 and
     * the current position.
     */
    fun encodeSuccessEnvelope(result: Any): ByteBuffer

    /**
     * Encodes an error result into a binary envelope message.
     *
     * @param errorCode An error code String.
     * @param errorMessage An error message String, possibly null.
     * @param errorDetails Error details, possibly null.
     * @return a [ByteBuffer] containing the encoding between position 0 and
     * the current position.
     */
    fun encodeErrorEnvelope(errorCode: String, errorMessage: String, errorDetails: Any): ByteBuffer

    /**
     * Decodes a result envelope from binary.
     *
     * @param envelope the binary encoding of a result envelope as a [ByteBuffer].
     * @return the enveloped result Object.
     * @throws FlutterException if the envelope was an error envelope.
     */
    fun decodeEnvelope(envelope: ByteBuffer): Any
}