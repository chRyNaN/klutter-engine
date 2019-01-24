package com.chrynan.klutter.platformandroid.plugin.common

import org.json.JSONObject
import java.lang.reflect.Array.getLength
import org.json.JSONArray

object JSONUtil {

    /**
     * Convert the Json java representation to Java objects. Particularly used for converting
     * JSONArray and JSONObject to Lists and Maps.
     */
    fun unwrap(o: Any?): Any? {
        if (JSONObject.NULL == o || o == null) {
            return null
        }
        if (o is Boolean
            || o is Byte
            || o is Char
            || o is Double
            || o is Float
            || o is Int
            || o is Long
            || o is Short
            || o is String
        ) {
            return o
        }
        try {
            if (o is JSONArray) {
                val list = ArrayList()
                val array = o as JSONArray?
                for (i in 0 until array!!.length()) {
                    list.add(unwrap(array.get(i)))
                }
                return list
            }
            if (o is JSONObject) {
                val map = HashMap()
                val jsonObject = o as JSONObject?
                val keyIterator = jsonObject!!.keys()
                while (keyIterator.hasNext()) {
                    val key = keyIterator.next()
                    map.put(key, unwrap(jsonObject.get(key)))
                }
                return map
            }
        } catch (ignored: Exception) {
        }

        return null
    }

    /**
     * Backport of [JSONObject.wrap] for use on pre-KitKat
     * systems.
     */
    fun wrap(o: Any?): Any? {
        if (o == null) {
            return JSONObject.NULL
        }
        if (o is JSONArray || o is JSONObject) {
            return o
        }
        if (o == JSONObject.NULL) {
            return o
        }
        try {
            if (o is Collection<*>) {
                val result = JSONArray()
                for (e in (o as Collection<*>?)!!)
                    result.put(wrap(e))
                return result
            } else if (o.javaClass.isArray) {
                val result = JSONArray()
                val length = Array.getLength(o)
                for (i in 0 until length)
                    result.put(wrap(Array.get(o, i)))
                return result
            }
            if (o is Map<*, *>) {
                val result = JSONObject()
                for ((key, value) in o)
                    result.put(key as String, wrap(value))
                return result
            }
            if (o is Boolean ||
                o is Byte ||
                o is Char ||
                o is Double ||
                o is Float ||
                o is Int ||
                o is Long ||
                o is Short ||
                o is String
            ) {
                return o
            }
            if (o.javaClass.getPackage().name.startsWith("java.")) {
                return o.toString()
            }
        } catch (ignored: Exception) {
        }

        return null
    }
}