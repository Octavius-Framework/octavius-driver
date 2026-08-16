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
     *
     * The connection stays in copy mode until [CopyIn.endCopy] or [CopyIn.cancelCopy] is called, and no
     * ordinary query can run on it meanwhile. [CopyIn] is [AutoCloseable] and cancels on close, so
     * `use { }` is the safe way to hold one.
     *
     * @param sql A `COPY … FROM STDIN` statement.
     * @return The handle to write chunks through.
     * @throws InvalidOperationException `UNEXPECTED_RESULT` if [sql] did not start a COPY IN.
     */
    fun copyIn(sql: String): CopyIn {
        stream.lock.lock()
        try {
            stream.checkAvailable()
            stream.sendMessage(SimpleQueryMessage(sql))
            stream.flush()

            var errorResponse: ErrorOrNoticeMessage? = null
            while (true) {
                val msg = stream.receiveMessage()
                when (msg) {
                    is ErrorOrNoticeMessage -> errorResponse = msg
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
     *
     * The connection stays in copy mode until the stream is drained or [CopyOut.cancelCopy] is called,
     * and no ordinary query can run on it meanwhile. [CopyOut] is [AutoCloseable] and cancels on close,
     * so `use { }` is the safe way to hold one.
     *
     * @param sql A `COPY … TO STDOUT` statement.
     * @return The handle to read chunks through.
     * @throws InvalidOperationException `UNEXPECTED_RESULT` if [sql] did not start a COPY OUT.
     */
    fun copyOut(sql: String): CopyOut {
        stream.lock.lock()
        try {
            stream.checkAvailable()
            stream.sendMessage(SimpleQueryMessage(sql))
            stream.flush()

            var errorResponse: ErrorOrNoticeMessage? = null
            while (true) {
                val msg = stream.receiveMessage()
                when (msg) {
                    is ErrorOrNoticeMessage -> errorResponse = msg
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
     *
     * The transfer is aborted if anything goes wrong, so the connection is never left in copy mode.
     * [inputStream] is not closed — that stays with the caller.
     *
     * @param sql A `COPY … FROM STDIN` statement.
     * @param inputStream The source of the data, read to exhaustion.
     * @param bufferSize Size of the chunks the input is forwarded in. Each chunk is one message
     *   and one flush, so very small values turn a bulk load back into per-chunk round trips.
     * @return The number of rows the server accepted.
     * @throws InvalidOperationException `INVALID_ARGUMENT` if [bufferSize] is not positive,
     *   `UNEXPECTED_RESULT` if [sql] did not start a COPY IN.
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
     *
     * The transfer is aborted if anything goes wrong, so the connection is never left in copy mode.
     * [outputStream] is neither flushed nor closed — that stays with the caller.
     *
     * @param sql A `COPY … TO STDOUT` statement.
     * @param outputStream Where the data is written.
     * @return The number of **bytes** written. PostgreSQL reports no row count for COPY OUT, so this is
     *   not the counterpart of what [copyIn] returns.
     * @throws InvalidOperationException `UNEXPECTED_RESULT` if [sql] did not start a COPY OUT.
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
     *
     * Each call is one protocol message and one flush, so chunks want to be substantial rather than
     * row-sized. Nothing is validated against the target table here; the server checks the data when it
     * parses it, and a malformed chunk surfaces from [endCopy] rather than from this call.
     *
     * @param data The bytes to send.
     * @param offset Where to start in [data].
     * @param length How many bytes to send.
     * @throws InvalidOperationException `COPY_NOT_ACTIVE` if the operation has already finished.
     */
    fun writeToCopy(data: ByteArray, offset: Int = 0, length: Int = data.size) = stream.lock.withLock {
        if (!isActive) throw InvalidOperationException(InvalidOperationExceptionReason.COPY_NOT_ACTIVE, "Copy operation is no longer active.")
        stream.sendMessage(FrontendCopyDataMessage(data, offset, length))
        stream.flush()
    }

    /**
     * Ends the COPY IN operation and returns the number of rows affected.
     *
     * This is where the server reports what it made of the data, so a malformed row anywhere in the
     * transfer surfaces here rather than from the [writeToCopy] that sent it. The connection leaves copy
     * mode either way.
     *
     * @return The number of rows the server accepted.
     * @throws InvalidOperationException `COPY_NOT_ACTIVE` if the operation has already finished.
     */
    fun endCopy(): Long {
        stream.lock.lock()
        try {
            if (!isActive) throw InvalidOperationException(InvalidOperationExceptionReason.COPY_NOT_ACTIVE, "Copy operation is no longer active.")
            stream.sendMessage(FrontendCopyDoneMessage())
            stream.flush()
            
            var rowsAffected = 0L
            var errorResponse: ErrorOrNoticeMessage? = null
            while (true) {
                val msg = stream.receiveMessage()
                when (msg) {
                    is ErrorOrNoticeMessage -> errorResponse = msg
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

    private var errorResponse: ErrorOrNoticeMessage? = null

    /**
     * Reads a chunk of data from the server.
     *
     * Chunk boundaries are the server's, not yours: a chunk is one protocol message and may hold part of
     * a row, several rows, or both. Keep calling until this returns `null`, at which point the
     * connection has left copy mode.
     *
     * @return The next chunk, or `null` once the transfer is over.
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
                    is ErrorOrNoticeMessage -> {
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
