package io.github.octaviusframework.driver.copy

import io.github.octaviusframework.driver.exception.ExceptionTranslator
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

    /**
     * Initiates a COPY IN operation allowing manual chunk writing.
     */
    fun copyIn(sql: String): CopyIn {
        stream.lock.lock()
        try {
            stream.sendMessage(SimpleQueryMessage(sql))
            stream.flush()

            var errorResponse: ErrorResponseMessage? = null
            while (true) {
                val msg = stream.receiveMessage()
                when (msg) {
                    is ErrorResponseMessage -> errorResponse = msg
                    is CopyInResponseMessage -> {
                        return CopyIn(stream)
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
            stream.sendMessage(SimpleQueryMessage(sql))
            stream.flush()

            var errorResponse: ErrorResponseMessage? = null
            while (true) {
                val msg = stream.receiveMessage()
                when (msg) {
                    is ErrorResponseMessage -> errorResponse = msg
                    is CopyOutResponseMessage -> {
                        return CopyOut(stream)
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
     */
    fun copyIn(sql: String, inputStream: InputStream): Long {
        val copyIn = copyIn(sql)
        try {
            val buffer = ByteArray(65536)
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
}

class CopyIn internal constructor(private val stream: PgStream) : CopyOperation {
    override var isActive: Boolean = true
        private set

    /**
     * Writes a chunk of data to the server.
     */
    fun writeToCopy(data: ByteArray, offset: Int = 0, length: Int = data.size) = stream.lock.withLock {
        if (!isActive) throw InvalidOperationException(InvalidOperationExceptionReason.UNEXPECTED_RESULT, "Copy operation is no longer active.")
        stream.sendMessage(FrontendCopyDataMessage(data, offset, length))
        stream.flush()
    }

    /**
     * Ends the COPY IN operation and returns the number of rows affected.
     */
    fun endCopy(): Long {
        stream.lock.lock()
        try {
            if (!isActive) throw InvalidOperationException(InvalidOperationExceptionReason.UNEXPECTED_RESULT, "Copy operation is no longer active.")
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
    override var isActive: Boolean = true
        private set
    
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
