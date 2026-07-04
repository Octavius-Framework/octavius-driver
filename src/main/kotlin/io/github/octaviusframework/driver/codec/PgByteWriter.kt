package io.github.octaviusframework.driver.codec

/**
 * Optimized buffer for building binary packets for the database.
 * Allows reserving space for size and filling it later without memory copying.
 */
internal class PgByteWriter(initialCapacity: Int = 1024) {
    var data = ByteArray(initialCapacity)
        private set
    var position = 0
        private set

    private fun ensureCapacity(needed: Int) {
        if (position + needed > data.size) {
            var newCap = data.size * 2
            while (position + needed > newCap) newCap *= 2
            data = data.copyOf(newCap)
        }
    }

    fun writeByte(b: Byte) {
        ensureCapacity(1)
        data[position++] = b
    }

    fun writeInt(i: Int) {
        ensureCapacity(4)
        data[position++] = (i shr 24).toByte()
        data[position++] = (i shr 16).toByte()
        data[position++] = (i shr 8).toByte()
        data[position++] = i.toByte()
    }

    fun writeBytes(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        bytes.copyInto(data, position)
        position += bytes.size
    }

    /**
     * Leaves 4 bytes of space for a number (e.g. packet size) and returns the index
     * where this size should be written later.
     */
    fun reserveLengthInt(): Int {
        val marker = position
        writeInt(0) // placeholder
        return marker
    }

    /**
     * Calculates the number of bytes added since reserveLengthInt was called and writes this value at the marker.
     * Does not include the 4 bytes of the marker itself (in accordance with many PG structures).
     */
    fun fillLengthInt(markerIndex: Int) {
        val length = position - markerIndex - 4
        data[markerIndex] = (length shr 24).toByte()
        data[markerIndex + 1] = (length shr 16).toByte()
        data[markerIndex + 2] = (length shr 8).toByte()
        data[markerIndex + 3] = length.toByte()
    }

    fun toByteArray(): ByteArray {
        return data.copyOfRange(0, position)
    }
}
