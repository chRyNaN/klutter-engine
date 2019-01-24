package com.chrynan.klutter.platformandroid.plugin.common

import java.nio.ByteBuffer

/**
 * A message encoding/decoding mechanism.
 *
 * Both operations throw [IllegalArgumentException], if conversion fails.
 */
interface MessageCodec<T> {

    /**
     * Encodes the specified message into binary.
     *
     * @param message the T message, possibly null.
     * @return a ByteBuffer containing the encoding between position 0 and
     * the current position, or null, if message is null.
     */
    fun encodeMessage(message: T): ByteBuffer

    /**
     * Decodes the specified message from binary.
     *
     * @param message the [ByteBuffer] message, possibly null.
     * @return a T value representation of the bytes between the given buffer's current
     * position and its limit, or null, if message is null.
     */
    fun decodeMessage(message: ByteBuffer): T
}