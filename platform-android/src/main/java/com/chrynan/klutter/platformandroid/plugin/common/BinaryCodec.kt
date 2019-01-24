package com.chrynan.klutter.platformandroid.plugin.common

import java.nio.ByteBuffer

/**
 * A [MessageCodec] using unencoded binary messages, represented as
 * [ByteBuffer]s.
 *
 *
 * This codec is guaranteed to be compatible with the corresponding
 * [BinaryCodec](https://docs.flutter.io/flutter/services/BinaryCodec-class.html)
 * on the Dart side. These parts of the Flutter SDK are evolved synchronously.
 *
 *
 * On the Dart side, messages are represented using `ByteData`.
 */
class BinaryCodec private constructor() : MessageCodec<ByteBuffer> {

    companion object {

        // This codec must match the Dart codec of the same name in package flutter/services.
        val INSTANCE = BinaryCodec()
    }

    fun encodeMessage(message: ByteBuffer): ByteBuffer {
        return message
    }

    fun decodeMessage(message: ByteBuffer): ByteBuffer {
        return message
    }
}