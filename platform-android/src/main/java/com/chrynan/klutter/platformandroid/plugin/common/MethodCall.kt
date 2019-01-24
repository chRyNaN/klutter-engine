package com.chrynan.klutter.platformandroid.plugin.common

import org.json.JSONObject


/**
 * Command object representing a method call on a [MethodChannel].
 *
 * Creates a [MethodCall] with the specified method name and arguments.
 *
 * @param method the method name String, not null.
 * @param arguments the arguments, a value supported by the channel's message codec.
 */
class MethodCall(
    val method: String,
    val arguments: Any?
) {

    /**
     * Returns the arguments of this method call with a static type determined by the call-site.
     *
     * @param <T> the intended type of the arguments.
     * @return the arguments with static type T
    </T> */
    fun <T> arguments(): T? {
        return arguments as T?
    }

    /**
     * Returns a String-keyed argument of this method call, assuming [.arguments] is a
     * [Map] or a [JSONObject]. The static type of the returned result is determined
     * by the call-site.
     *
     * @param <T> the intended type of the argument.
     * @param key the String key.
     * @return the argument value at the specified key, with static type T, or `null`, if
     * such an entry is not present.
     * @throws ClassCastException if [.arguments] can be cast to neither [Map] nor
     * [JSONObject].
    </T> */
    fun <T> argument(key: String): T? {
        return if (arguments == null) {
            null
        } else if (arguments is Map<*, *>) {
            arguments[key] as T
        } else if (arguments is JSONObject) {
            arguments.opt(key) as T
        } else {
            throw ClassCastException()
        }
    }

    /**
     * Returns whether this method call involves a mapping for the given argument key,
     * assuming [.arguments] is a [Map] or a [JSONObject]. The value associated
     * with the key, as returned by [.argument], is not considered, and may be
     * `null`.
     *
     * @param key the String key.
     * @return `true`, if [.arguments] is a [Map] containing key, or a
     * [JSONObject] with a mapping for key.
     * @throws ClassCastException if [.arguments] can be cast to neither [Map] nor
     * [JSONObject].
     */
    fun hasArgument(key: String): Boolean {
        return if (arguments == null) {
            false
        } else (arguments as? Map<*, *>)?.containsKey(key) ?: ((arguments as? JSONObject)?.has(key)
            ?: throw ClassCastException())
    }
}