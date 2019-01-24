package com.chrynan.klutter.platformandroid.plugin.common

import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import org.junit.runner.Request.method
import java.nio.ByteBuffer

/**
 * A [MethodCodec] using UTF-8 encoded JSON method calls and result envelopes.
 *
 *
 * This codec is guaranteed to be compatible with the corresponding
 * [JSONMethodCodec](https://docs.flutter.io/flutter/services/JSONMethodCodec-class.html)
 * on the Dart side. These parts of the Flutter SDK are evolved synchronously.
 *
 *
 * Values supported as methods arguments and result payloads are those supported by
 * [JSONMessageCodec].
 */
class JSONMethodCodec private constructor() : MethodCodec {

    companion object {

        // This codec must match the Dart codec of the same name in package flutter/services.
        val INSTANCE = JSONMethodCodec()
    }

    fun encodeMethodCall(methodCall: MethodCall): ByteBuffer? {
        try {
            val map = JSONObject()
            map.put("method", methodCall.method)
            map.put("args", JSONUtil.wrap(methodCall.arguments))
            return JSONMessageCodec.INSTANCE.encodeMessage(map)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Invalid JSON", e)
        }

    }

    fun decodeMethodCall(message: ByteBuffer): MethodCall {
        try {
            val json = JSONMessageCodec.INSTANCE.decodeMessage(message)
            if (json is JSONObject) {
                val map = json as JSONObject?
                val method = map!!.get("method")
                val arguments = unwrapNull(map.opt("args"))
                if (method is String) {
                    return MethodCall(method, arguments)
                }
            }
            throw IllegalArgumentException("Invalid method call: " + json!!)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Invalid JSON", e)
        }

    }

    fun encodeSuccessEnvelope(result: Any): ByteBuffer? {
        return JSONMessageCodec.INSTANCE
            .encodeMessage(JSONArray().put(JSONUtil.wrap(result)))
    }

    fun encodeErrorEnvelope(
        errorCode: String, errorMessage: String,
        errorDetails: Any
    ): ByteBuffer? {
        return JSONMessageCodec.INSTANCE.encodeMessage(
            JSONArray()
                .put(errorCode)
                .put(JSONUtil.wrap(errorMessage))
                .put(JSONUtil.wrap(errorDetails))
        )
    }

    fun decodeEnvelope(envelope: ByteBuffer): Any? {
        try {
            val json = JSONMessageCodec.INSTANCE.decodeMessage(envelope)
            if (json is JSONArray) {
                val array = json as JSONArray?
                if (array!!.length() == 1) {
                    return unwrapNull(array.opt(0))
                }
                if (array.length() == 3) {
                    val code = array.get(0)
                    val message = unwrapNull(array.opt(1))
                    val details = unwrapNull(array.opt(2))
                    if (code is String && (message == null || message is String)) {
                        throw FlutterException(code, (message as String?)!!, details!!)
                    }
                }
            }
            throw IllegalArgumentException("Invalid envelope: " + json!!)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Invalid JSON", e)
        }

    }

    internal fun unwrapNull(value: Any): Any? {
        return if (value === JSONObject.NULL) null else value
    }
}