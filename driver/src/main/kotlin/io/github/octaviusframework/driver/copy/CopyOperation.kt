package io.github.octaviusframework.driver.copy

/**
 * Base interface for COPY operations.
 */
interface CopyOperation : AutoCloseable {
    /**
     * Returns true if the copy operation is still active.
     */
    val isActive: Boolean

    /**
     * Cancels the copy operation.
     */
    fun cancelCopy()

    override fun close() {
        if (isActive) {
            cancelCopy()
        }
    }
}

/**
 * Interface for COPY IN operations (client to server).
 */
interface CopyIn : CopyOperation {
    /**
     * Writes a chunk of data to the server.
     */
    fun writeToCopy(data: ByteArray, offset: Int = 0, length: Int = data.size)

    /**
     * Ends the COPY IN operation and returns the number of rows affected.
     */
    fun endCopy(): Long
}

/**
 * Interface for COPY OUT operations (server to client).
 */
interface CopyOut : CopyOperation {
    /**
     * Reads a chunk of data from the server.
     * Returns null if the copy operation has finished.
     */
    fun readFromCopy(): ByteArray?
}
