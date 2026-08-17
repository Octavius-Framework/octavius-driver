package io.github.octaviusframework.driver.lo

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.session.OctaviusSessionImpl
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream
import java.io.OutputStream

private val logger = KotlinLogging.logger {}


/**
 * Defines the reference point for seek operations on a Large Object.
 */
object SeekWhence {
    /** Seek from the beginning of the Large Object. */
    const val SET = 0
    /** Seek from the current position. */
    const val CUR = 1
    /** Seek from the end of the Large Object. */
    const val END = 2
}

/**
 * Represents an open descriptor to a PostgreSQL Large Object.
 * Implements [AutoCloseable] for convenient usage within `.use { }` blocks.
 */
class LargeObject internal constructor(
    private val session: OctaviusSessionImpl,
    /** The Object ID (OID) of this Large Object. */
    val oid: Int,
    /** The File Descriptor (FD) assigned by PostgreSQL for this open object. */
    val fd: Int
) : AutoCloseable {

    private var closed = false

    private fun checkClosed() {
        if (closed || session.octaviusConnection.isClosed) throw InvalidOperationException(
            InvalidOperationExceptionReason.OBJECT_CLOSED,
            "Large Object is already closed"
        )
    }

    /**
     * Reads up to [length] bytes from the Large Object.
     *
     * @param length The maximum number of bytes to read.
     * @return A [ByteArray] containing the read data, or an empty array if EOF is reached.
     */
    fun read(length: Int): ByteArray {
        checkClosed()
        if (length == 0) return ByteArray(0)

        return session.createNativeQuery("SELECT loread($1, $2)")
            .fetchFieldStrict<ByteArray>(fd, length)
    }

    /**
     * Reads up to [length] bytes from the Large Object into the given [buffer].
     *
     * @param buffer The byte array to store the read data.
     * @param offset The starting offset in the [buffer] where data will be written. Defaults to 0.
     * @param length The maximum number of bytes to read. Defaults to the remaining space in the buffer.
     * @return The total number of bytes read into the buffer, or -1 if there is no more data because the end of the object has been reached.
     */
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int {
        val bytes = read(length)
        if (bytes.isEmpty()) {
            return -1 // EOF
        }

        System.arraycopy(bytes, 0, buffer, offset, bytes.size)
        return bytes.size
    }

    /**
     * Writes [length] bytes from the specified [data] byte array starting at [offset] to this Large Object.
     *
     * @param data The byte array containing the data to be written.
     * @param offset The start offset in the data array. Defaults to 0.
     * @param length The number of bytes to write. Defaults to the remaining bytes in the array.
     */
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        checkClosed()
        if (length == 0) return

        val dataToWrite = if (offset == 0 && length == data.size) {
            data
        } else {
            data.copyOfRange(offset, offset + length)
        }

        session.createNativeQuery("SELECT lowrite($1, $2)")
            .fetchFieldStrict<Int>(fd, dataToWrite)
    }

    /**
     * Returns an [InputStream] for reading from this Large Object.
     * Closing the stream will close this Large Object.
     *
     * @return An [InputStream] that reads from the current position.
     */
    fun inputStream(): InputStream = LargeObjectInputStream(this)

    /**
     * Returns an [OutputStream] for writing to this Large Object.
     * Closing the stream will close this Large Object.
     *
     * @return An [OutputStream] that writes from the current position.
     */
    fun outputStream(): OutputStream = LargeObjectOutputStream(this)

    /**
     * Sets the current position of the Large Object.
     *
     * @param position The offset to seek to.
     * @param ref The reference point for the [position] (e.g., [SeekWhence.SET], [SeekWhence.CUR], [SeekWhence.END]). Defaults to [SeekWhence.SET].
     * @return The new current position.
     */
    fun seek(position: Long, ref: Int = SeekWhence.SET): Long {
        checkClosed()
        return session.createNativeQuery("SELECT lo_lseek64($1, $2, $3)")
            .fetchFieldStrict<Long>(fd, position, ref)
    }

    /**
     * Retrieves the current position of the Large Object.
     *
     * @return The current position offset from the beginning of the object.
     */
    fun tell(): Long {
        checkClosed()
        return session.createNativeQuery("SELECT lo_tell64($1)")
            .fetchFieldStrict<Long>(fd)
    }

    /**
     * Truncates the Large Object to the given [length].
     * If the object was previously larger, the extra data is lost.
     * If the object was previously shorter, it is extended with null bytes.
     *
     * @param length The new length of the Large Object.
     */
    fun truncate(length: Long) {
        checkClosed()
        session.createNativeQuery("SELECT lo_truncate64($1, $2)")
            .fetchFieldStrict<Int>(fd, length)
    }

    /**
     * Closes the Large Object descriptor, freeing server-side resources.
     * It is safe to call this method multiple times; subsequent calls are ignored.
     */
    override fun close() {
        if (!closed) {
            closed = true
            try {
                session.createNativeQuery("SELECT lo_close($1)")
                    .fetchFieldStrict<Int>(fd)
            } catch (e: Exception) {
                // Ignore close errors if connection is broken. The descriptor then stays open
                // on the server until the transaction ends, which is only visible from here.
                logger.debug(e) { "Failed to close large object descriptor $fd (oid $oid)" }
            }
        }
    }
}
