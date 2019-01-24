package com.chrynan.klutter.platformandroid.plugin.common

import java.lang.reflect.Array.getDouble
import java.lang.reflect.Array.getChar
import java.nio.ByteOrder.LITTLE_ENDIAN
import android.R.attr.order
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteBuffer.allocateDirect
import java.nio.ByteOrder
import java.nio.charset.Charset
import kotlin.experimental.and

/**
 * MessageCodec using the Flutter standard binary encoding.
 *
 *
 * This codec is guaranteed to be compatible with the corresponding
 * [StandardMessageCodec](https://docs.flutter.io/flutter/services/StandardMessageCodec-class.html)
 * on the Dart side. These parts of the Flutter SDK are evolved synchronously.
 *
 *
 * Supported messages are acyclic values of these forms:
 *
 *
 *  * null
 *  * Booleans
 *  * Bytes, Shorts, Integers, Longs
 *  * BigIntegers (see below)
 *  * Floats, Doubles
 *  * Strings
 *  * byte[], int[], long[], double[]
 *  * Lists of supported values
 *  * Maps with supported keys and values
 *
 *
 *
 * On the Dart side, these values are represented as follows:
 *
 *
 *  * null: null
 *  * Boolean: bool
 *  * Byte, Short, Integer, Long: int
 *  * Float, Double: double
 *  * String: String
 *  * byte[]: Uint8List
 *  * int[]: Int32List
 *  * long[]: Int64List
 *  * double[]: Float64List
 *  * List: List
 *  * Map: Map
 *
 *
 *
 * BigIntegers are represented in Dart as strings with the
 * hexadecimal representation of the integer's value.
 *
 *
 * To extend the codec, overwrite the writeValue and readValueOfType methods.
 */
class StandardMessageCodec : MessageCodec<Any> {

    override fun encodeMessage(message: Any): ByteBuffer {
        if (message == null) {
            return null
        }
        val stream = ExposedByteArrayOutputStream()
        writeValue(stream, message)
        val buffer = ByteBuffer.allocateDirect(stream.size())
        buffer.put(stream.buffer(), 0, stream.size())
        return buffer
    }

    fun decodeMessage(message: ByteBuffer?): Any? {
        if (message == null) {
            return null
        }
        message!!.order(ByteOrder.nativeOrder())
        val value = readValue(message!!)
        if (message!!.hasRemaining()) {
            throw IllegalArgumentException("Message corrupted")
        }
        return value
    }

    /**
     * Writes a type discriminator byte and then a byte serialization of the
     * specified value to the specified stream.
     *
     *
     * Subclasses can extend the codec by overriding this method, calling
     * super for values that the extension does not handle.
     */
    protected fun writeValue(stream: ByteArrayOutputStream, value: Any?) {
        if (value == null) {
            stream.write(NULL)
        } else if (value === java.lang.Boolean.TRUE) {
            stream.write(TRUE)
        } else if (value === java.lang.Boolean.FALSE) {
            stream.write(FALSE)
        } else if (value is Number) {
            if (value is Int || value is Short || value is Byte) {
                stream.write(INT)
                writeInt(stream, value.toInt())
            } else if (value is Long) {
                stream.write(LONG)
                writeLong(stream, value)
            } else if (value is Float || value is Double) {
                stream.write(DOUBLE)
                writeAlignment(stream, 8)
                writeDouble(stream, value.toDouble())
            } else if (value is BigInteger) {
                stream.write(BIGINT)
                writeBytes(
                    stream,
                    (value as BigInteger).toString(16).getBytes(UTF8)
                )
            } else {
                throw IllegalArgumentException("Unsupported Number type: " + value.javaClass)
            }
        } else if (value is String) {
            stream.write(STRING)
            writeBytes(stream, value.getBytes(UTF8))
        } else if (value is ByteArray) {
            stream.write(BYTE_ARRAY)
            writeBytes(stream, (value as ByteArray?)!!)
        } else if (value is IntArray) {
            stream.write(INT_ARRAY)
            val array = value as IntArray?
            writeSize(stream, array!!.size)
            writeAlignment(stream, 4)
            for (n in array) {
                writeInt(stream, n)
            }
        } else if (value is LongArray) {
            stream.write(LONG_ARRAY)
            val array = value as LongArray?
            writeSize(stream, array!!.size)
            writeAlignment(stream, 8)
            for (n in array) {
                writeLong(stream, n)
            }
        } else if (value is DoubleArray) {
            stream.write(DOUBLE_ARRAY)
            val array = value as DoubleArray?
            writeSize(stream, array!!.size)
            writeAlignment(stream, 8)
            for (d in array) {
                writeDouble(stream, d)
            }
        } else if (value is List<*>) {
            stream.write(LIST)
            val list = value as List<*>?
            writeSize(stream, list!!.size)
            for (o in list) {
                writeValue(stream, o)
            }
        } else if (value is Map<*, *>) {
            stream.write(MAP)
            val map = value as Map<*, *>?
            writeSize(stream, map!!.size)
            for ((key, value1) in map) {
                writeValue(stream, key)
                writeValue(stream, value1)
            }
        } else {
            throw IllegalArgumentException("Unsupported value: $value")
        }
    }

    /**
     * Reads a value as written by writeValue.
     */
    protected fun readValue(buffer: ByteBuffer): Any? {
        if (!buffer.hasRemaining()) {
            throw IllegalArgumentException("Message corrupted")
        }
        val type = buffer.get()
        return readValueOfType(type, buffer)
    }

    /**
     * Reads a value of the specified type.
     *
     *
     * Subclasses may extend the codec by overriding this method, calling
     * super for types that the extension does not handle.
     */
    protected fun readValueOfType(type: Byte, buffer: ByteBuffer): Any? {
        val result: Any?
        when (type) {
            NULL -> result = null
            TRUE -> result = true
            FALSE -> result = false
            INT -> result = buffer.getInt()
            LONG -> result = buffer.getLong()
            BIGINT -> {
                val hex = readBytes(buffer)
                result = BigInteger(String(hex, UTF8), 16)
            }
            DOUBLE -> {
                readAlignment(buffer, 8)
                result = buffer.getDouble()
            }
            STRING -> {
                val bytes = readBytes(buffer)
                result = String(bytes, UTF8)
            }
            BYTE_ARRAY -> {
                result = readBytes(buffer)
            }
            INT_ARRAY -> {
                val length = readSize(buffer)
                val array = IntArray(length)
                readAlignment(buffer, 4)
                buffer.asIntBuffer().get(array)
                result = array
                buffer.position(buffer.position() + 4 * length)
            }
            LONG_ARRAY -> {
                val length = readSize(buffer)
                val array = LongArray(length)
                readAlignment(buffer, 8)
                buffer.asLongBuffer().get(array)
                result = array
                buffer.position(buffer.position() + 8 * length)
            }
            DOUBLE_ARRAY -> {
                val length = readSize(buffer)
                val array = DoubleArray(length)
                readAlignment(buffer, 8)
                buffer.asDoubleBuffer().get(array)
                result = array
                buffer.position(buffer.position() + 8 * length)
            }
            LIST -> {
                val size = readSize(buffer)
                val list = ArrayList(size)
                for (i in 0 until size) {
                    list.add(readValue(buffer))
                }
                result = list
            }
            MAP -> {
                val size = readSize(buffer)
                val map = HashMap()
                for (i in 0 until size) {
                    map.put(readValue(buffer), readValue(buffer))
                }
                result = map
            }
            else -> throw IllegalArgumentException("Message corrupted")
        }
        return result
    }

    internal class ExposedByteArrayOutputStream : ByteArrayOutputStream() {
        fun buffer(): ByteArray {
            return buf
        }
    }

    companion object {
        val INSTANCE = StandardMessageCodec()

        private val LITTLE_ENDIAN = ByteOrder.nativeOrder() === ByteOrder.LITTLE_ENDIAN
        private val UTF8 = Charset.forName("UTF8")
        private val NULL: Byte = 0
        private val TRUE: Byte = 1
        private val FALSE: Byte = 2
        private val INT: Byte = 3
        private val LONG: Byte = 4
        private val BIGINT: Byte = 5
        private val DOUBLE: Byte = 6
        private val STRING: Byte = 7
        private val BYTE_ARRAY: Byte = 8
        private val INT_ARRAY: Byte = 9
        private val LONG_ARRAY: Byte = 10
        private val DOUBLE_ARRAY: Byte = 11
        private val LIST: Byte = 12
        private val MAP: Byte = 13

        /**
         * Writes an int representing a size to the specified stream.
         * Uses an expanding code of 1 to 5 bytes to optimize for small values.
         */
        protected fun writeSize(stream: ByteArrayOutputStream, value: Int) {
            assert(0 <= value)
            if (value < 254) {
                stream.write(value)
            } else if (value <= 0xffff) {
                stream.write(254)
                writeChar(stream, value)
            } else {
                stream.write(255)
                writeInt(stream, value)
            }
        }

        /**
         * Writes the least significant two bytes of the specified int to the
         * specified stream.
         */
        protected fun writeChar(stream: ByteArrayOutputStream, value: Int) {
            if (LITTLE_ENDIAN) {
                stream.write(value)
                stream.write(value.ushr(8))
            } else {
                stream.write(value.ushr(8))
                stream.write(value)
            }
        }

        /**
         * Writes the specified int as 4 bytes to the specified stream.
         */
        protected fun writeInt(stream: ByteArrayOutputStream, value: Int) {
            if (LITTLE_ENDIAN) {
                stream.write(value)
                stream.write(value.ushr(8))
                stream.write(value.ushr(16))
                stream.write(value.ushr(24))
            } else {
                stream.write(value.ushr(24))
                stream.write(value.ushr(16))
                stream.write(value.ushr(8))
                stream.write(value)
            }
        }

        /**
         * Writes the specified long as 8 bytes to the specified stream.
         */
        protected fun writeLong(stream: ByteArrayOutputStream, value: Long) {
            if (LITTLE_ENDIAN) {
                stream.write(value.toByte())
                stream.write(value.ushr(8).toByte())
                stream.write(value.ushr(16).toByte())
                stream.write(value.ushr(24).toByte())
                stream.write(value.ushr(32).toByte())
                stream.write(value.ushr(40).toByte())
                stream.write(value.ushr(48).toByte())
                stream.write(value.ushr(56).toByte())
            } else {
                stream.write(value.ushr(56).toByte())
                stream.write(value.ushr(48).toByte())
                stream.write(value.ushr(40).toByte())
                stream.write(value.ushr(32).toByte())
                stream.write(value.ushr(24).toByte())
                stream.write(value.ushr(16).toByte())
                stream.write(value.ushr(8).toByte())
                stream.write(value.toByte())
            }
        }

        /**
         * Writes the specified double as 8 bytes to the specified stream.
         */
        protected fun writeDouble(stream: ByteArrayOutputStream, value: Double) {
            writeLong(stream, java.lang.Double.doubleToLongBits(value))
        }

        /**
         * Writes the length and then the actual bytes of the specified array to
         * the specified stream.
         */
        protected fun writeBytes(stream: ByteArrayOutputStream, bytes: ByteArray) {
            writeSize(stream, bytes.size)
            stream.write(bytes, 0, bytes.size)
        }

        /**
         * Writes a number of padding bytes to the specified stream to ensure that
         * the next value is aligned to a whole multiple of the specified alignment.
         * An example usage with alignment = 8 is to ensure doubles are word-aligned
         * in the stream.
         */
        protected fun writeAlignment(stream: ByteArrayOutputStream, alignment: Int) {
            val mod = stream.size() % alignment
            if (mod != 0) {
                for (i in 0 until alignment - mod) {
                    stream.write(0)
                }
            }
        }

        /**
         * Reads an int representing a size as written by writeSize.
         */
        protected fun readSize(buffer: ByteBuffer): Int {
            if (!buffer.hasRemaining()) {
                throw IllegalArgumentException("Message corrupted")
            }
            val value = buffer.get() and 0xff
            return if (value < 254) {
                value
            } else if (value == 254) {
                buffer.getChar()
            } else {
                buffer.getInt()
            }
        }

        /**
         * Reads a byte array as written by writeBytes.
         */
        protected fun readBytes(buffer: ByteBuffer): ByteArray {
            val length = readSize(buffer)
            val bytes = ByteArray(length)
            buffer.get(bytes)
            return bytes
        }

        /**
         * Reads alignment padding bytes as written by writeAlignment.
         */
        protected fun readAlignment(buffer: ByteBuffer, alignment: Int) {
            val mod = buffer.position() % alignment
            if (mod != 0) {
                buffer.position(buffer.position() + alignment - mod)
            }
        }
    }
}