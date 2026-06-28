package io.github.octaviusframework.driver.io

import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

class PgInputStream(inputStream: InputStream) {
    private val dataIn = DataInputStream(inputStream)
    
    // We use this buffer to reduce allocations when reading C-style strings
    private var stringBuffer = ByteArray(256)

    fun readByte(): Byte = dataIn.readByte()
    
    fun readInt(): Int = dataIn.readInt()
    
    fun readShort(): Short = dataIn.readShort()

    fun readFully(bytes: ByteArray) {
        dataIn.readFully(bytes)
    }

    /**
     * Returns exactly the specified number of bytes.
     */
    fun readBytes(length: Int): ByteArray {
        val array = ByteArray(length)
        dataIn.readFully(array)
        return array
    }

    /**
     * Reads a null-terminated string.
     */
    fun readCString(): String {
        var length = 0
        while (true) {
            val b = dataIn.readByte()
            if (b == 0.toByte()) {
                break
            }
            if (length >= stringBuffer.size) {
                // Enlarge buffer if the string is very long
                val newBuffer = ByteArray(stringBuffer.size * 2)
                System.arraycopy(stringBuffer, 0, newBuffer, 0, stringBuffer.size)
                stringBuffer = newBuffer
            }
            stringBuffer[length++] = b
        }
        return String(stringBuffer, 0, length, StandardCharsets.UTF_8)
    }
}
