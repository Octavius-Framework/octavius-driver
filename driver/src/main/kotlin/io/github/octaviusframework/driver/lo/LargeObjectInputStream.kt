package io.github.octaviusframework.driver.lo

import java.io.InputStream

/**
 * An [InputStream] that reads data from a PostgreSQL [LargeObject].
 * Closing this stream also closes the underlying LargeObject.
 */
class LargeObjectInputStream(private val obj: LargeObject) : InputStream() {

    override fun read(): Int {
        val bytes = obj.read(1)
        if (bytes.isEmpty()) {
            return -1 // EOF
        }
        return bytes[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) {
            return 0
        }
        return obj.read(b, off, len)
    }

    override fun close() {
        obj.close()
    }
}
