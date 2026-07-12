package io.github.octaviusframework.driver.io

import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class PgOutputStream(outputStream: OutputStream) {
    private val dataOut = DataOutputStream(outputStream)

    fun writeByte(b: Byte) {
        dataOut.writeByte(b.toInt())
    }

    fun writeInt(i: Int) {
        dataOut.writeInt(i)
    }

    fun writeShort(s: Int) {
        require(s in 0..65535) { "Value $s out of bounds for unsigned 16-bit short" }
        dataOut.writeShort(s)
    }

    fun writeBytes(bytes: ByteArray) {
        dataOut.write(bytes)
    }

    /**
     * Writes a null-terminated string.
     */
    fun writeCString(s: String) {
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        dataOut.write(bytes)
        dataOut.writeByte(0)
    }

    fun flush() {
        dataOut.flush()
    }
}
