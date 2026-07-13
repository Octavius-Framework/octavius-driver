package io.github.octaviusframework.driver.io

import io.github.octaviusframework.driver.exception.AuthExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusAuthException
import io.github.octaviusframework.driver.message.backend.*
import io.github.octaviusframework.driver.message.frontend.FrontendMessage
import io.github.octaviusframework.driver.message.frontend.TerminateMessage
import io.github.octaviusframework.driver.notification.PgNotification
import io.github.octaviusframework.driver.ssl.PgSslUpgrader
import io.github.octaviusframework.driver.ssl.SslConfiguration
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.SocketTimeoutException

private val logger = KotlinLogging.logger {}

class PgStream(val host: String, val port: Int, loginTimeoutSecs: Int = 10) : AutoCloseable {
    private var socket: Socket = Socket()
    var inputStream: PgInputStream
    var outputStream: PgOutputStream
    var processId: Int = -1
    var secretKey: ByteArray = ByteArray(0)
    var isBroken: Boolean = false

    init {
        val connectTimeoutMs = if (loginTimeoutSecs > 0) loginTimeoutSecs * 1000 else 10000
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket.soTimeout = connectTimeoutMs
        inputStream = PgInputStream(socket.getInputStream().buffered(8192))
        outputStream = PgOutputStream(socket.getOutputStream().buffered(8192))
    }

    fun upgradeToSSL(host: String, port: Int, config: SslConfiguration) {
        val sslSocket = PgSslUpgrader.upgrade(socket, host, port, config)
        socket = sslSocket
        inputStream = PgInputStream(socket.getInputStream().buffered(8192))
        outputStream = PgOutputStream(socket.getOutputStream().buffered(8192))
    }


    val parameters = mutableMapOf<String, String>()

    var networkTimeout: Int
        get() = socket.soTimeout
        set(value) {
            socket.soTimeout = value
        }
    private val _notifications = MutableSharedFlow<PgNotification>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val notifications: SharedFlow<PgNotification> = _notifications

    internal fun sendMessage(msg: FrontendMessage) {
        try {
            msg.encode(outputStream)
        } catch (e: IOException) {
            isBroken = true
            throw e
        }
    }

    fun flush() {
        try {
            outputStream.flush()
        } catch (e: IOException) {
            isBroken = true
            throw e
        }
    }

    internal fun receiveMessage(isPolling: Boolean = false): BackendMessage {
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
                        // TODO: eventually a logging system
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
                    'I' -> return EmptyQueryResponseMessage
                    'C' -> {
                        val commandTag = inputStream.readCString()
                        return CommandCompleteMessage(commandTag)
                    }
                    'T' -> {
                        val numFields = inputStream.readShort().toInt()
                        val fields = mutableListOf<RowDescriptionMessage.FieldDescription>()
                        for (i in 0 until numFields) {
                            val fieldName = inputStream.readCString()
                            val tableOid = inputStream.readInt()
                            val columnAttr = inputStream.readShort()
                            val dataTypeOid = inputStream.readInt()
                            val dataTypeSize = inputStream.readShort()
                            val typeModifier = inputStream.readInt()
                            val formatCode = inputStream.readShort()
                            fields.add(
                                RowDescriptionMessage.FieldDescription(
                                    fieldName, tableOid, columnAttr, dataTypeOid, dataTypeSize, typeModifier, formatCode
                                )
                            )
                        }
                        return RowDescriptionMessage(fields)
                    }
                    'D' -> {
                        val numColumns = inputStream.readShort().toInt()
                        val rawRowData = inputStream.readBytes(payloadLength - 2)

                        val columnOffsets = IntArray(numColumns)
                        val columnLengths = IntArray(numColumns)
                        var offset = 0
                        for (i in 0 until numColumns) {
                            val colLength = rawRowData.getIntBE(offset)
                            offset += 4
                            columnLengths[i] = colLength
                            if (colLength == -1) {
                                columnOffsets[i] = -1
                            } else {
                                columnOffsets[i] = offset
                                offset += colLength
                            }
                        }
                        return DataRowMessage(rawRowData, columnOffsets, columnLengths)
                    }
                    else -> {
                        val unparsed = inputStream.readBytes(payloadLength)
                        logger.trace { "IGNORING: Unsupported synchronous message type: $tag" }
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            if (isPolling && readingTag) {
                throw e
            }
            isBroken = true
            throw e
        } catch (e: IOException) {
            isBroken = true
            throw e
        }
    }

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
            else -> throw OctaviusAuthException(
                AuthExceptionMessage.UNSUPPORTED_MECHANISM,
                details = "Unknown authentication type: $type"
            )
        }
    }

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

    override fun close() {
        if (!socket.isClosed) {
            try {
                sendMessage(TerminateMessage())
                flush()
            } catch (e: Exception) {
                // Ignoring errors during close
            }
            try {
                socket.close()
            } catch (ignore: Exception) {
            }
        }
    }
}

