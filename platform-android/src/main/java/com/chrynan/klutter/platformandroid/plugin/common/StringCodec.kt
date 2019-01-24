package com.chrynan.klutter.platformandroid.plugin.common

import android.R.array
import java.nio.ByteBuffer
import java.nio.ByteBuffer.allocateDirect
import java.nio.charset.Charset

/**
 * A [MessageCodec] using UTF-8 encoded String messages.
 *
 *
 * This codec is guaranteed to be compatible with the corresponding
 * [StringCodec](https://docs.flutter.io/flutter/services/StringCodec-class.html)
 * on the Dart side. These parts of the Flutter SDK are evolved synchronously.
 */
class StringCodec private constructor() : MessageCodec<String> {

    companion object {

        private val UTF8 = Charset.forName("UTF8")
        val INSTANCE = StringCodec()
    }

    override fun encodeMessage(message: String): ByteBuffer {
        // TODO(mravn): Avoid the extra copy below.
        val bytes = message.getBytes(UTF8)
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes)
        return buffer
    }

    fun decodeMessage(message: ByteBuffer?): String? {
        if (message == null) {
            return null
        }
        val bytes: ByteArray
        val offset: Int
        val length = message.remaining()
        if (message.hasArray()) {
            bytes = message.array()
            offset = message.arrayOffset()
        } else {
            // TODO(mravn): Avoid the extra copy below.
            bytes = ByteArray(length)
            message.get(bytes)
            offset = 0
        }
        return String(bytes, offset, length, UTF8)
    }
}