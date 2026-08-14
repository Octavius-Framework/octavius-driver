package io.github.octaviusframework.driver.copy

import io.github.octaviusframework.driver.message.translator.ExceptionTranslator
import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.backend.*
import io.github.octaviusframework.driver.message.frontend.FrontendCopyDataMessage
import io.github.octaviusframework.driver.message.frontend.FrontendCopyDoneMessage
import io.github.octaviusframework.driver.message.frontend.FrontendCopyFailMessage
import io.github.octaviusframework.driver.message.frontend.SimpleQueryMessage
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.withLock

/**
 * Manages COPY IN and COPY OUT operations for a specific connection stream.
 */
class CopyManager internal constructor(private val stream: PgStream) {

    companion object {
        /** Chunk size used by the [InputStream] overload of [copyIn] when none is given. */
        const val DEFAULT_BUFFER_SIZE: Int = 65536
    }

    /**
     * The most recently started operation, kept only so a closing session can abort a transfer
     * the caller left open. Never cleared: [CopyOperation.isActive] is the authority on whether
     * it still refers to anything live.
     */
    private var lastOperation: CopyOperation? = null

    /**
     * Initiates a COPY IN operation allowing manual chunk writing.
     */
    fun copyIn(sql: String): CopyIn {
        stream.lock.lock()
        try {
            stream.checkNotInCopyMode()
            stream.sendMessage(SimpleQueryMessage(sql))
            stream.flush()

            var errorResponse: ErrorResponseMessage? = null
            while (true) {
                val msg = stream.receiveMessage()
                when (msg) {
                    is ErrorResponseMessage -> errorResponse = msg
                    is CopyInResponseMessage -> {
                        return CopyIn(stream).also { lastOperation = it }
                    }
                    is ReadyForQueryMessage -> {
                        if (errorResponse != null) {
                            throw ExceptionTranslator.translate(errorResponse)
                        }
                        throw InvalidOperationException(
                            InvalidOperationExceptionReason.UNEXPECTED_RESULT,
                            "Query did not initiate a COPY IN operation."
                        )
                    }
                    else -> { /* Ignore */ }
                }
            }
        } finally {
            stream.lock.unlock()
        }
    }

    /**
     * Initiates a COPY OUT operation allowing manual chunk reading.
     */
    fun copyOut(sql: String): CopyOut {
        stream.lock.lock()
        try {
            stream.checkNotInCopyMode()
            stream.sendMessage(SimpleQueryMessage(sql))
            stream.flush()

            var errorResponse: ErrorResponseMessage? = null
            while (true) {
                val msg = stream.receiveMessage()
                when (msg) {
                    is ErrorResponseMessage -> errorResponse = msg
                    is CopyOutResponseMessage -> {
                        return CopyOut(stream).also { lastOperation = it }
                    }
                    is ReadyForQueryMessage -> {
                        if (errorResponse != null) {
                            throw ExceptionTranslator.translate(errorResponse)
                        }
                        throw InvalidOperationException(
                            InvalidOperationExceptionReason.UNEXPECTED_RESULT,
                            "Query did not initiate a COPY OUT operation."
                        )
                    }
                    else -> { /* Ignore */ }
                }
            }
        } finally {
            stream.lock.unlock()
        }
    }

    /**
     * Reads all data from the provided InputStream and writes it to the COPY IN operation.
     * Returns the number of updated rows.
     *
     * @param bufferSize Size of the chunks the input is forwarded in. Each chunk is one message
     *   and one flush, so very small values turn a bulk load back into per-chunk round trips.
     */
    fun copyIn(sql: String, inputStream: InputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
        if (bufferSize <= 0) {
            throw InvalidOperationException(
                InvalidOperationExceptionReason.INVALID_ARGUMENT,
                "Copy buffer size must be positive, was $bufferSize."
            )
        }
        val copyIn = copyIn(sql)
        try {
            val buffer = ByteArray(bufferSize)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                copyIn.writeToCopy(buffer, 0, bytesRead)
            }
            return copyIn.endCopy()
        } catch (e: Exception) {
            if (copyIn.isActive) {
                copyIn.cancelCopy()
            }
            throw e
        }
    }

    /**
     * Reads all data from the COPY OUT operation and writes it to the provided OutputStream.
     * Returns the number of bytes read.
     */
    fun copyOut(sql: String, outputStream: OutputStream): Long {
        val copyOut = copyOut(sql)
        var totalBytes = 0L
        try {
            while (true) {
                val chunk = copyOut.readFromCopy() ?: break
                outputStream.write(chunk)
                totalBytes += chunk.size
            }
            return totalBytes // PG doesn't return rows count for COPY OUT in the same way, or at least we return bytes written here
        } catch (e: Exception) {
            if (copyOut.isActive) {
                copyOut.cancelCopy()
            }
            throw e
        }
    }

    /**
     * Aborts a transfer the caller never finished, if there is one.
     *
     * Called when a session closes: a connection left in copy mode would otherwise be handed
     * to the next borrower of a pooled connection in the middle of a transfer.
     */
    internal fun cancelActiveOperation() {
        lastOperation?.takeIf { it.isActive }?.cancelCopy()
    }
}

class CopyIn internal constructor(private val stream: PgStream) : CopyOperation {
    /**
     * Kept in step with [PgStream.copyInProgress] through the setter below, so the rest of the
     * driver can tell that the connection is in copy mode without holding this handle. The
     * initializer bypasses the setter, hence the init block.
     */
    override var isActive: Boolean = true
        private set(value) {
            field = value
            stream.copyInProgress = value
        }

    init {
        stream.copyInProgress = true
    }

    /**
     * Writes a chunk of data to the server.
     */
    fun writeToCopy(data: ByteArray, offset: Int = 0, length: Int = data.size) = stream.lock.withLock {
        if (!isActive) throw InvalidOperationException(InvalidOperationExceptionReason.COPY_NOT_ACTIVE, "Copy operation is no longer active.")
        stream.sendMessage(FrontendCopyDataMessage(data, offset, length))
        stream.flush()
    }

    /**
     * Ends the COPY IN operation and returns the number of rows affected.
     */
    fun endCopy(): Long {
        stream.lock.lock()
        try {
            if (!isActive) throw InvalidOperationException(InvalidOperationExceptionReason.COPY_NOT_ACTIVE, "Copy operation is no longer active.")
            stream.sendMessage(FrontendCopyDoneMessage())
            stream.flush()
            
            var rowsAffected = 0L
            var errorResponse: ErrorResponseMessage? = null
            while (true) {
                val msg = stream.receiveMessage()
                when (msg) {
                    is ErrorResponseMessage -> errorResponse = msg
                    is CommandCompleteMessage -> {
                        val parts = msg.tag.split(" ")
                        if (parts.size >= 2) {
                            rowsAffected = parts.last().toLongOrNull() ?: 0L
                        }
                    }
                    is ReadyForQueryMessage -> {
                        isActive = false
                        if (errorResponse != null) {
                            throw ExceptionTranslator.translate(errorResponse)
                        }
                        return rowsAffected
                    }
                    else -> { /* Ignore */ }
                }
            }
        } finally {
            stream.lock.unlock()
        }
    }

    override fun cancelCopy() {
        stream.lock.lock()
        try {
            if (!isActive) return
            stream.sendMessage(FrontendCopyFailMessage("Copy operation cancelled by user."))
            stream.flush()
            isActive = false
            
            // Discard messages until ReadyForQuery
            while (true) {
                val msg = stream.receiveMessage()
                if (msg is ReadyForQueryMessage) {
                    break
                }
            }
        } finally {
            stream.lock.unlock()
        }
    }
}

class CopyOut internal constructor(private val stream: PgStream) : CopyOperation {
    /** Kept in step with [PgStream.copyInProgress]; see the equivalent property on [CopyIn]. */
    override var isActive: Boolean = true
        private set(value) {
            field = value
            stream.copyInProgress = value
        }

    init {
        stream.copyInProgress = true
    }

    private var errorResponse: ErrorResponseMessage? = null

    /**
     * Reads a chunk of data from the server.
     * Returns null if the copy operation has finished.
     */
    fun readFromCopy(): ByteArray? {
        stream.lock.lock()
        try {
            if (!isActive) return null

            while (true) {
                val msg = stream.receiveMessage()
                when (msg) {
                    is BackendCopyDataMessage -> return msg.data
                    is BackendCopyDoneMessage -> {
                        // Expect CommandComplete and ReadyForQuery
                    }
                    is ErrorResponseMessage -> {
                        errorResponse = msg
                    }
                    is CommandCompleteMessage -> { /* Ignore */ }
                    is ReadyForQueryMessage -> {
                        isActive = false
                        if (errorResponse != null) {
                            throw ExceptionTranslator.translate(errorResponse!!)
                        }
                        return null
                    }
                    else -> { /* Ignore */ }
                }
            }
        } finally {
            stream.lock.unlock()
        }
    }

    override fun cancelCopy() {
        stream.lock.lock()
        try {
            if (!isActive) return
            isActive = false
            // Wait for ReadyForQuery
            while (true) {
                val msg = stream.receiveMessage()
                if (msg is ReadyForQueryMessage) {
                    break
                }
            }
        } finally {
            stream.lock.unlock()
        }
    }
}
