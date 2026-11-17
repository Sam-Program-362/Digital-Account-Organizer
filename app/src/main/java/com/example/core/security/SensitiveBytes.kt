package com.example.core.security

import java.util.Arrays

/**
 * Wraps a mutable byte array containing key material and provides best-effort zeroization.
 * Never logs or exposes contents in [toString].
 */
class SensitiveBytes(private val bytes: ByteArray) : AutoCloseable {

    val size: Int
        get() = bytes.size

    var isCleared: Boolean = false
        private set

    fun copyBytes(): ByteArray {
        check(!isCleared) { "SensitiveBytes has already been cleared." }
        return bytes.copyOf()
    }

    fun clear() {
        Arrays.fill(bytes, 0.toByte())
        isCleared = true
    }

    override fun close() {
        clear()
    }

    override fun toString(): String = "SensitiveBytes(REDACTED)"
    override fun equals(other: Any?): Boolean = false
    override fun hashCode(): Int = 0
}

fun CharArray.clearChars() {
    Arrays.fill(this, '\u0000')
}

fun ByteArray.clearBytes() {
    Arrays.fill(this, 0.toByte())
}
