package io.github.octaviusframework.io

fun ByteArray.getShortBE(offset: Int = 0): Short {
    return ((this[offset].toInt() and 0xFF shl 8) or
            (this[offset + 1].toInt() and 0xFF)).toShort()
}

fun ByteArray.getIntBE(offset: Int = 0): Int {
    return (this[offset].toInt() and 0xFF shl 24) or
           (this[offset + 1].toInt() and 0xFF shl 16) or
           (this[offset + 2].toInt() and 0xFF shl 8) or
           (this[offset + 3].toInt() and 0xFF)
}

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

fun ByteArray.getFloatBE(offset: Int = 0): Float {
    return Float.fromBits(this.getIntBE(offset))
}

fun ByteArray.getDoubleBE(offset: Int = 0): Double {
    return Double.fromBits(this.getLongBE(offset))
}

fun ByteArray.getUIntBE(offset: Int = 0): UInt {
    return this.getIntBE(offset).toUInt()
}

fun Short.toByteArrayBE(): ByteArray {
    return byteArrayOf(
        (this.toInt() ushr 8).toByte(),
        this.toByte()
    )
}

fun Int.toByteArrayBE(): ByteArray {
    return byteArrayOf(
        (this ushr 24).toByte(),
        (this ushr 16).toByte(),
        (this ushr 8).toByte(),
        this.toByte()
    )
}

fun Long.toByteArrayBE(): ByteArray {
    return byteArrayOf(
        (this ushr 56).toByte(),
        (this ushr 48).toByte(),
        (this ushr 40).toByte(),
        (this ushr 32).toByte(),
        (this ushr 24).toByte(),
        (this ushr 16).toByte(),
        (this ushr 8).toByte(),
        this.toByte()
    )
}

fun Float.toByteArrayBE(): ByteArray {
    return this.toBits().toByteArrayBE()
}

fun Double.toByteArrayBE(): ByteArray {
    return this.toBits().toByteArrayBE()
}
