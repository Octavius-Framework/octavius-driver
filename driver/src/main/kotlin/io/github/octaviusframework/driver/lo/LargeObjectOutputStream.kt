package io.github.octaviusframework.driver.lo

import java.io.OutputStream

/**
 * An [OutputStream] that writes data to a PostgreSQL [LargeObject].
 * Closing this stream also closes the underlying LargeObject.
 */
class LargeObjectOutputStream(private val obj: LargeObject) : OutputStream() {

    override fun write(b: Int) {
        val byteArray = byteArrayOf(b.toByte())
        obj.write(byteArray, 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len == 0) return
        obj.write(b, off, len)
    }

    override fun close() {
        obj.close()
    }
}
