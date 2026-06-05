package io.github.octaviusframework.network

import io.github.octaviusframework.io.ByteArrayWindow
import io.github.octaviusframework.io.PgInputStream
import io.github.octaviusframework.io.PgOutputStream
import io.github.octaviusframework.io.getIntBE
import io.github.octaviusframework.network.messages.AuthenticationMessage
import io.github.octaviusframework.network.messages.BackendKeyDataMessage
import io.github.octaviusframework.network.messages.BackendMessage
import io.github.octaviusframework.network.messages.BindCompleteMessage
import io.github.octaviusframework.network.messages.CommandCompleteMessage
import io.github.octaviusframework.network.messages.DataRowMessage
import io.github.octaviusframework.network.messages.ErrorResponseMessage
import io.github.octaviusframework.network.messages.FrontendMessage
import io.github.octaviusframework.network.messages.NoDataMessage
import io.github.octaviusframework.network.messages.NoticeResponseMessage
import io.github.octaviusframework.network.messages.NotificationResponseMessage
import io.github.octaviusframework.network.messages.ParseCompleteMessage
import io.github.octaviusframework.network.messages.ReadyForQueryMessage
import io.github.octaviusframework.network.messages.RowDescriptionMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.net.InetSocketAddress
import java.net.Socket

class PgStream(host: String, port: Int) : AutoCloseable {
    private val socket: Socket = Socket()
    val inputStream: PgInputStream
    val outputStream: PgOutputStream

    init {
        socket.connect(InetSocketAddress(host, port), 10000)
        inputStream = PgInputStream(socket.getInputStream().buffered(8192))
        outputStream = PgOutputStream(socket.getOutputStream().buffered(8192))
    }

    val parameters = mutableMapOf<String, String>()
    
    private val _notifications = MutableSharedFlow<NotificationResponseMessage>(extraBufferCapacity = 64)
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
                }
                'N' -> {
                    val fields = mutableMapOf<Char, String>()
                    while (true) {
                        val token = inputStream.readByte().toInt().toChar()
                        if (token == '\u0000') break
                        fields[token] = inputStream.readCString()
                    }
                    val notice = NoticeResponseMessage(fields)
                    // TODO: ewentualnie system logowania
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
                    println("IGNORUJE: Nieobsługiwany typ wiadomości synchronicznej: $tag")
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
            else -> throw IllegalStateException("Nieznany typ autentykacji: $type")
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
            socket.close()
        }
    }
}
