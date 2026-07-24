package io.github.octaviusframework.driver.io

import java.io.OutputStream
import java.nio.charset.StandardCharsets

class PgOutputStream(private var outputStream: OutputStream) {
    private val buffer = ByteArray(8192)
    private var position = 0

    fun changeStream(newStream: OutputStream) {
        flushBuffer()
        this.outputStream = newStream
    }

    private fun ensureSpace(needed: Int) {
        if (position + needed > buffer.size) {
            flushBuffer()
        }
    }

    private fun flushBuffer() {
        if (position > 0) {
            outputStream.write(buffer, 0, position)
            position = 0
        }
    }

    fun writeByte(b: Byte) {
        if (position >= buffer.size) {
            flushBuffer()
        }
        buffer[position++] = b
    }

    fun writeInt(i: Int) {
        ensureSpace(4)
        buffer[position++] = (i ushr 24).toByte()
        buffer[position++] = (i ushr 16).toByte()
        buffer[position++] = (i ushr 8).toByte()
        buffer[position++] = i.toByte()
    }

    fun writeShort(s: Int) {
        require(s in 0..65535) { "Value $s out of bounds for unsigned 16-bit short" }
        ensureSpace(2)
        buffer[position++] = (s ushr 8).toByte()
        buffer[position++] = s.toByte()
    }

    fun writeBytes(bytes: ByteArray) {
        var offset = 0
        var length = bytes.size
        while (length > 0) {
            val space = buffer.size - position
            if (space == 0) {
                flushBuffer()
                continue
            }
            val toCopy = minOf(space, length)
            System.arraycopy(bytes, offset, buffer, position, toCopy)
            position += toCopy
            offset += toCopy
            length -= toCopy
        }
    }

    /**
     * Writes a null-terminated string.
     */
    fun writeCString(s: String) {
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        writeBytes(bytes)
        writeByte(0)
    }

    fun flush() {
        flushBuffer()
        outputStream.flush()
    }
}
