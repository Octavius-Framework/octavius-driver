package io.github.octaviusframework.driver.io

/**
 * Extension functions for reading Big-Endian primitive types from a [ByteArray].
 * PostgreSQL network protocol encodes data in Network Byte Order (Big-Endian).
 */

/**
 * Reads a 16-bit short integer from the byte array starting at [offset].
 */
fun ByteArray.getShortBE(offset: Int = 0): Short {
    return ((this[offset].toInt() and 0xFF shl 8) or
            (this[offset + 1].toInt() and 0xFF)).toShort()
}

/**
 * Reads a 32-bit integer from the byte array starting at [offset].
 */
fun ByteArray.getIntBE(offset: Int = 0): Int {
    return (this[offset].toInt() and 0xFF shl 24) or
           (this[offset + 1].toInt() and 0xFF shl 16) or
           (this[offset + 2].toInt() and 0xFF shl 8) or
           (this[offset + 3].toInt() and 0xFF)
}

/**
 * Reads a 64-bit integer from the byte array starting at [offset].
 */
fun ByteArray.getLongBE(offset: Int = 0): Long {
    return (this[offset].toLong() and 0xFF shl 56) or
           (this[offset + 1].toLong() and 0xFF shl 48) or
           (this[offset + 2].toLong() and 0xFF shl 40) or
           (this[offset + 3].toLong() and 0xFF shl 32) or
           (this[offset + 4].toLong() and 0xFF shl 24) or
           (this[offset + 5].toLong() and 0xFF shl 16) or
           (this[offset + 6].toLong() and 0xFF shl 8) or
           (this[offset + 7].toLong() and 0xFF)
}

/**
 * Reads a `float4` from the byte array starting at [offset], its four bytes being the IEEE 754 bit pattern
 * PostgreSQL's binary format sends.
 */
fun ByteArray.getFloatBE(offset: Int = 0): Float {
    return Float.fromBits(this.getIntBE(offset))
}

/**
 * Reads a `float8` from the byte array starting at [offset], as the IEEE 754 bit pattern it is sent as.
 */
fun ByteArray.getDoubleBE(offset: Int = 0): Double {
    return Double.fromBits(this.getLongBE(offset))
}
