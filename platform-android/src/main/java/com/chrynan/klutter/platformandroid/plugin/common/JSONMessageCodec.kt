package com.chrynan.klutter.platformandroid.plugin.common

import org.json.JSONException
import org.json.JSONTokener
import org.json.JSONObject
import java.nio.ByteBuffer

/**
 * A [MessageCodec] using UTF-8 encoded JSON messages.
 *
 *
 * This codec is guaranteed to be compatible with the corresponding
 * [JSONMessageCodec](https://docs.flutter.io/flutter/services/JSONMessageCodec-class.html)
 * on the Dart side. These parts of the Flutter SDK are evolved synchronously.
 *
 *
 * Supports the same Java values as [JSONObject.wrap].
 *
 *
 * On the Dart side, JSON messages are handled by the JSON facilities of the
 * [dart:convert](https://api.dartlang.org/stable/dart-convert/JSON-constant.html)
 * package.
 */
class JSONMessageCodec private constructor() : MessageCodec<Any> {

    companion object {

        // This codec must match the Dart codec of the same name in package flutter/services.
        val INSTANCE = JSONMessageCodec()
    }

    fun encodeMessage(message: Any?): ByteBuffer? {
        if (message == null) {
            return null
        }
        val wrapped = JSONUtil.wrap(message)
        return if (wrapped is String) {
            StringCodec.INSTANCE.encodeMessage(JSONObject.quote(wrapped as String))
        } else {
            StringCodec.INSTANCE.encodeMessage(wrapped.toString())
        }
    }

    fun decodeMessage(message: ByteBuffer?): Any? {
        if (message == null) {
            return null
        }
        try {
            val json = StringCodec.INSTANCE.decodeMessage(message)
            val tokener = JSONTokener(json)
            val value = tokener.nextValue()
            if (tokener.more()) {
                throw IllegalArgumentException("Invalid JSON")
            }
            return value
        } catch (e: JSONException) {
            throw IllegalArgumentException("Invalid JSON", e)
        }

    }
}