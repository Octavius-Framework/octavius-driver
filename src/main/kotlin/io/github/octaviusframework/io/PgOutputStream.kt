package io.github.octaviusframework.io

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

    fun writeShort(s: Short) {
        dataOut.writeShort(s.toInt())
    }

    fun writeBytes(bytes: ByteArray) {
        dataOut.write(bytes)
    }

    /**
     * Zapisuje łańcuch znaków zakończony zerem.
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
