package io.github.octaviusframework.io

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class PgInputStream(inputStream: InputStream) {
    private val dataIn = DataInputStream(inputStream)
    
    // Używamy tego bufora by zredukować alokacje przy odczytywaniu stringów C-style
    private var stringBuffer = ByteArray(256)

    fun readByte(): Byte = dataIn.readByte()
    
    fun readInt(): Int = dataIn.readInt()
    
    fun readShort(): Short = dataIn.readShort()

    fun readFully(bytes: ByteArray) {
        dataIn.readFully(bytes)
    }

    /**
     * Zwraca dokładnie zadaną liczbę bajtów.
     */
    fun readBytes(length: Int): ByteArray {
        val array = ByteArray(length)
        dataIn.readFully(array)
        return array
    }

    /**
     * Odczytuje łańcuch znaków zakończony bajtem 0 (null-terminated).
     */
    fun readCString(): String {
        var length = 0
        while (true) {
            val b = dataIn.readByte()
            if (b == 0.toByte()) {
                break
            }
            if (length >= stringBuffer.size) {
                // Powiększamy bufor jeśli napis jest bardzo długi
                val newBuffer = ByteArray(stringBuffer.size * 2)
                System.arraycopy(stringBuffer, 0, newBuffer, 0, stringBuffer.size)
                stringBuffer = newBuffer
            }
            stringBuffer[length++] = b
        }
        return String(stringBuffer, 0, length, StandardCharsets.UTF_8)
    }
}
