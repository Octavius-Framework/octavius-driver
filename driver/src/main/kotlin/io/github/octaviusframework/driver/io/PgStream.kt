package io.github.octaviusframework.driver.io

import io.github.octaviusframework.driver.exception.InitializationException
import io.github.octaviusframework.driver.exception.InitializationExceptionReason
import io.github.octaviusframework.driver.exception.NetworkException
import io.github.octaviusframework.driver.exception.NetworkExceptionReason
import io.github.octaviusframework.driver.message.backend.*
import io.github.octaviusframework.driver.message.frontend.FrontendMessage
import io.github.octaviusframework.driver.message.frontend.TerminateMessage
import io.github.octaviusframework.driver.notification.PgNotification
import io.github.octaviusframework.driver.row.FieldDescription
import io.github.octaviusframework.driver.ssl.PgSslUpgrader
import io.github.octaviusframework.driver.ssl.SslConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.locks.ReentrantLock

/**
 * Represents a connection stream to a PostgreSQL database.
 * Handles reading and writing of PostgreSQL wire protocol messages.
 *
 * @property host The hostname or IP address of the PostgreSQL server.
 * @property port The port number of the PostgreSQL server.
 * @param loginTimeoutSecs Timeout in seconds for the initial connection and login process.
 * @param notificationBufferCapacity Capacity of the buffer for asynchronous notifications.
 */
internal class PgStream(val host: String, val port: Int, loginTimeoutSecs: Int = 10, notificationBufferCapacity: Int = 256) : AutoCloseable {
    companion object {
        private val logger = KotlinLogging.logger {}
        // A specific logger name allows users to filter just notices in logback.xml
        private val noticeLogger = KotlinLogging.logger("io.github.octaviusframework.driver.Notice")
    }
    val lock = ReentrantLock()
    private var socket: Socket = Socket()
    var inputStream: PgInputStream
    var outputStream: PgOutputStream
    var processId: Int = -1
    var secretKey: ByteArray = ByteArray(0)
    var isBroken: Boolean = false
    var maxCachedRowSize: Int = 65536

    /**
     * Shared buffers used to reduce memory allocations during 'DataRow' deserialization.
     * Since 'Row' eagerly parses all values into basic JVM types during initialization,
     * the underlying byte arrays can be immediately reused for the next row.
     */
    private var sharedRowData = ByteArray(1024)
    private var sharedColumnOffsets = IntArray(16)
    private var sharedColumnLengths = IntArray(16)

    init {
        val connectTimeoutMs = if (loginTimeoutSecs > 0) loginTimeoutSecs * 1000 else 10000
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket.soTimeout = connectTimeoutMs
        inputStream = PgInputStream(socket.getInputStream())
        outputStream = PgOutputStream(socket.getOutputStream())
    }

    /**
     * Upgrades the current connection to use SSL/TLS.
     *
     * @param host The hostname to verify against the SSL certificate.
     * @param port The port number.
     * @param config The SSL configuration to use.
     */
    fun upgradeToSSL(host: String, port: Int, config: SslConfiguration) {
        val sslSocket = PgSslUpgrader.upgrade(socket, host, port, config)
        socket = sslSocket
        inputStream.changeStream(socket.getInputStream())
        outputStream.changeStream(socket.getOutputStream())
    }


    val parameters = mutableMapOf<String, String>()

    var networkTimeout: Int
        get() = socket.soTimeout
        set(value) {
            socket.soTimeout = value
        }
    private val _notifications = MutableSharedFlow<PgNotification>(
        extraBufferCapacity = notificationBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val notifications: SharedFlow<PgNotification> = _notifications

    /**
     * Sends a frontend message to the PostgreSQL server.
     *
     * @param msg The frontend message to send.
     * @throws NetworkException if a network error occurs during transmission.
     */
    fun sendMessage(msg: FrontendMessage) {
        try {
            msg.encode(outputStream)
        } catch (e: IOException) {
            isBroken = true
            throw NetworkException(NetworkExceptionReason.CONNECTION_ERROR, cause = e)
        }
    }

    /**
     * Flushes the underlying output stream, forcing any buffered bytes to be written out.
     *
     * @throws NetworkException if a network error occurs during flush.
     */
    fun flush() {
        try {
            outputStream.flush()
        } catch (e: IOException) {
            isBroken = true
            throw NetworkException(NetworkExceptionReason.CONNECTION_ERROR, cause = e)
        }
    }

    /**
     * Receives a single backend message synchronously, blocking until data is available.
     *
     * @return The parsed backend message.
     * @throws NetworkException if a network error occurs.
     */
    fun receiveMessage(): BackendMessage {
        return receiveMessageInternal(isPolling = false)!!
    }

    /**
     * Attempts to receive a backend message if one is immediately available or within the timeout.
     * Returns null if no message is available (e.g. timeout reached while polling).
     *
     * @return The parsed backend message, or null if a timeout occurs before receiving the tag.
     */
    fun pollMessage(): BackendMessage? {
        return receiveMessageInternal(isPolling = true)
    }

    /**
     * Internal function to receive a backend message, optionally handling timeouts gracefully if polling.
     *
     * @param isPolling If true, a read timeout when reading the initial message tag will return null instead of throwing an exception.
     * @return The parsed backend message, or null if polling and a timeout occurs.
     */
    private fun receiveMessageInternal(isPolling: Boolean = false): BackendMessage? {
        var readingTag = true
        try {
            while (true) {
                readingTag = true
                val tag = inputStream.readByte().toInt().toChar()
                readingTag = false
                val length = inputStream.readInt()
                val payloadLength = length - 4

                when (tag) {
                    'S' -> {
                        val name = inputStream.readCString()
                        val value = inputStream.readCString()
                        parameters[name] = value
                        return ParameterStatusMessage(name, value)
                    }
                    'N' -> {
                        val fields = mutableMapOf<Char, String>()
                        while (true) {
                            val token = inputStream.readByte().toInt().toChar()
                            if (token == '\u0000') break
                            fields[token] = inputStream.readCString()
                        }
                        val notice = NoticeResponseMessage(fields)
                        val logMsg = "[PID: $processId] $notice"

                        when (notice.severity) {
                            "WARNING" -> noticeLogger.warn { logMsg }
                            "NOTICE", "INFO", "LOG" -> noticeLogger.info { logMsg }
                            "DEBUG" -> noticeLogger.debug { logMsg }
                            else -> noticeLogger.info { logMsg }
                        }
                    }
                    'A' -> {
                        val pid = inputStream.readInt()
                        val channel = inputStream.readCString()
                        val payload = inputStream.readCString()
                        _notifications.tryEmit(PgNotification(pid, channel, payload))
                    }
                    'R' -> return parseAuthentication(payloadLength)
                    'E' -> return parseErrorResponse(payloadLength)
                    'K' -> {
                        val pid = inputStream.readInt()
                        val keyBytes = inputStream.readBytes(payloadLength - 4)
                        return BackendKeyDataMessage(pid, keyBytes)
                    }
                    'Z' -> {
                        val status = inputStream.readByte().toInt().toChar()
                        return ReadyForQueryMessage(status)
                    }
                    '1' -> return ParseCompleteMessage
                    '2' -> return BindCompleteMessage
                    'n' -> return NoDataMessage
                    's' -> return PortalSuspendedMessage
                    'I' -> return EmptyQueryResponseMessage
                    'C' -> {
                        val commandTag = inputStream.readCString()
                        return CommandCompleteMessage(commandTag)
                    }
                    'T' -> {
                        val numFields = inputStream.readShort().toInt()
                        val fields = mutableListOf<FieldDescription>()
                        for (i in 0 until numFields) {
                            val fieldName = inputStream.readCString()
                            val tableOid = inputStream.readInt()
                            val columnAttr = inputStream.readShort()
                            val dataTypeOid = inputStream.readInt()
                            val dataTypeSize = inputStream.readShort()
                            val typeModifier = inputStream.readInt()
                            val formatCode = inputStream.readShort()
                            fields.add(
                                FieldDescription(
                                    fieldName, tableOid, columnAttr, dataTypeOid, dataTypeSize, typeModifier, formatCode
                                )
                            )
                        }
                        return RowDescriptionMessage(fields)
                    }
                    'D' -> {
                        val numColumns = inputStream.readShort().toInt()
                        val rowDataSize = payloadLength - 2
                        
                        val rowDataBuffer: ByteArray
                        if (rowDataSize > maxCachedRowSize) { // Don't cache arrays larger than maxCachedRowSize to avoid memory leaks on huge values
                            rowDataBuffer = ByteArray(rowDataSize)
                        } else {
                            if (sharedRowData.size < rowDataSize) {
                                var newSize = sharedRowData.size * 2
                                while (newSize < rowDataSize) newSize *= 2
                                sharedRowData = ByteArray(newSize)
                            }
                            rowDataBuffer = sharedRowData
                        }

                        if (sharedColumnOffsets.size < numColumns) {
                            var newSize = sharedColumnOffsets.size * 2
                            while (newSize < numColumns) newSize *= 2
                            sharedColumnOffsets = IntArray(newSize)
                            sharedColumnLengths = IntArray(newSize)
                        }

                        inputStream.readFully(rowDataBuffer, rowDataSize)

                        var offset = 0
                        for (i in 0 until numColumns) {
                            val colLength = rowDataBuffer.getIntBE(offset)
                            offset += 4
                            sharedColumnLengths[i] = colLength
                            if (colLength == -1) {
                                sharedColumnOffsets[i] = -1
                            } else {
                                sharedColumnOffsets[i] = offset
                                offset += colLength
                            }
                        }
                        return DataRowMessage(rowDataBuffer, sharedColumnOffsets, sharedColumnLengths)
                    }
                    else -> {
                        val unparsed = inputStream.readBytes(payloadLength)
                        logger.trace { "IGNORING: Unsupported synchronous message type: $tag" }
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            if (isPolling && readingTag) {
                return null
            }
            isBroken = true
            throw NetworkException(NetworkExceptionReason.CONNECTION_TIMEOUT, cause = e)
        } catch (e: EOFException) {
            isBroken = true
            throw NetworkException(NetworkExceptionReason.CONNECTION_CLOSED_BY_PEER, cause = e)
        } catch (e: IOException) {
            isBroken = true
            throw NetworkException(NetworkExceptionReason.CONNECTION_ERROR, cause = e)
        }
    }

    /**
     * Parses an authentication request message from the backend.
     *
     * @param payloadLength The length of the message payload.
     * @return The parsed AuthenticationMessage.
     * @throws InitializationException if the authentication mechanism is unsupported.
     */
    private fun parseAuthentication(payloadLength: Int): BackendMessage {
        return when (val type = inputStream.readInt()) {
            0 -> AuthenticationMessage.Ok
            3 -> AuthenticationMessage.CleartextPassword
            5 -> {
                val salt = inputStream.readBytes(4)
                AuthenticationMessage.MD5Password(salt)
            }
            10 -> {
                val mechanisms = mutableListOf<String>()
                while (true) {
                    val mech = inputStream.readCString()
                    if (mech.isEmpty()) break
                    mechanisms.add(mech)
                }
                AuthenticationMessage.SASL(mechanisms)
            }
            11 -> {
                val data = inputStream.readBytes(payloadLength - 4)
                AuthenticationMessage.SASLContinue(data)
            }
            12 -> {
                val data = inputStream.readBytes(payloadLength - 4)
                AuthenticationMessage.SASLFinal(data)
            }
            else -> throw InitializationException(
                InitializationExceptionReason.UNSUPPORTED_MECHANISM,
                details = "Unknown authentication type: $type"
            )
        }
    }

    /**
     * Parses an error response message from the backend.
     *
     * @param payloadLength The length of the message payload.
     * @return The parsed ErrorResponseMessage.
     */
    private fun parseErrorResponse(payloadLength: Int): BackendMessage {
        val fields = mutableMapOf<Char, String>()
        while (true) {
            val token = inputStream.readByte().toInt().toChar()
            if (token == '\u0000') break
            val value = inputStream.readCString()
            fields[token] = value
        }
        return ErrorResponseMessage(fields)
    }

    /**
     * Closes the connection stream, sending a Terminate message if the socket is still open.
     */
    override fun close() {
        if (!socket.isClosed) {
            try {
                sendMessage(TerminateMessage())
                flush()
            } catch (_: Exception) {
                // Ignoring errors during close
            }
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }
}

