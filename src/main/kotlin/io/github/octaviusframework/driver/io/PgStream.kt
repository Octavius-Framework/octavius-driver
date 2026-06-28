package io.github.octaviusframework.driver.io

import io.github.octaviusframework.driver.exception.AuthExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusAuthException
import io.github.octaviusframework.driver.message.backend.*
import io.github.octaviusframework.driver.message.frontend.FrontendMessage
import io.github.octaviusframework.driver.message.frontend.TerminateMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class PgStream(host: String, port: Int) : AutoCloseable {
    private var socket: Socket = Socket()
    var inputStream: PgInputStream
    var outputStream: PgOutputStream

    init {
        socket.connect(InetSocketAddress(host, port), 10000)
        inputStream = PgInputStream(socket.getInputStream().buffered(8192))
        outputStream = PgOutputStream(socket.getOutputStream().buffered(8192))
    }

    fun upgradeToSSL(host: String, port: Int) {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val sslSocket = factory.createSocket(socket, host, port, true) as SSLSocket
        sslSocket.startHandshake()
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
    private val _notifications = MutableSharedFlow<NotificationResponseMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val notifications: SharedFlow<NotificationResponseMessage> = _notifications

    fun sendMessage(msg: FrontendMessage) {
        msg.encode(outputStream)
    }

    fun flush() {
        outputStream.flush()
    }

    fun receiveMessage(): BackendMessage {
        while (true) {
            val tag = inputStream.readByte().toInt().toChar()
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
                    _notifications.tryEmit(NotificationResponseMessage(pid, channel, payload))
                }
                'R' -> return parseAuthentication(payloadLength)
                'E' -> return parseErrorResponse(payloadLength)
                'K' -> {
                    val pid = inputStream.readInt()
                    val key = inputStream.readInt()
                    return BackendKeyDataMessage(pid, key)
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
                        val tableOid = inputStream.readInt().toUInt()
                        val columnAttr = inputStream.readShort()
                        val dataTypeOid = inputStream.readInt().toUInt()
                        val dataTypeSize = inputStream.readShort()
                        val typeModifier = inputStream.readInt()
                        val formatCode = inputStream.readShort()
                        fields.add(
                            RowDescriptionMessage.FieldDescription(
                            fieldName, tableOid, columnAttr, dataTypeOid, dataTypeSize, typeModifier, formatCode
                        ))
                    }
                    return RowDescriptionMessage(fields)
                }
                'D' -> {
                    val numColumns = inputStream.readShort().toInt()
                    val rawRowData = inputStream.readBytes(payloadLength - 2)
                    val rowWindow = ByteArrayWindow(rawRowData, 0, rawRowData.size)

                    val columns = mutableListOf<ByteArrayWindow?>()
                    var offset = 0
                    for (i in 0 until numColumns) {
                        val colLength = rowWindow.getIntBE(offset)
                        offset += 4
                        if (colLength == -1) {
                            columns.add(null)
                        } else {
                            columns.add(rowWindow.slice(offset, colLength))
                            offset += colLength
                        }
                    }
                    return DataRowMessage(columns)
                }
                else -> {
                    val unparsed = inputStream.readBytes(payloadLength)
                    println("IGNORING: Unsupported synchronous message type: $tag")
                }
            }
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
            else -> throw OctaviusAuthException(AuthExceptionMessage.UNSUPPORTED_MECHANISM, details = "Unknown authentication type: $type")
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
            socket.close()
        }
    }
}