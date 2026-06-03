package io.github.octaviusframework.network

import io.github.octaviusframework.io.PgInputStream
import io.github.octaviusframework.io.PgOutputStream
import io.github.octaviusframework.network.messages.AuthenticationMessage
import io.github.octaviusframework.network.messages.BackendMessage
import io.github.octaviusframework.network.messages.ErrorResponseMessage
import io.github.octaviusframework.network.messages.FrontendMessage
import java.net.InetSocketAddress
import java.net.Socket

class PgStream(host: String, port: Int) : AutoCloseable {
    private val socket: Socket = Socket()
    val inputStream: PgInputStream
    val outputStream: PgOutputStream

    init {
        socket.connect(InetSocketAddress(host, port), 10000)
        inputStream = PgInputStream(socket.getInputStream())
        outputStream = PgOutputStream(socket.getOutputStream())
    }

    val parameters = mutableMapOf<String, String>()
    
    private val _notifications = kotlinx.coroutines.flow.MutableSharedFlow<io.github.octaviusframework.network.messages.NotificationResponseMessage>(extraBufferCapacity = 64)
    val notifications: kotlinx.coroutines.flow.SharedFlow<io.github.octaviusframework.network.messages.NotificationResponseMessage> = _notifications

    fun sendMessage(msg: FrontendMessage) {
        msg.encode(outputStream)
        outputStream.flush()
    }

    /**
     * Główna pętla odczytu przechwytująca 'szum' asynchroniczny (S, N, A).
     */
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
                    val notice = io.github.octaviusframework.network.messages.NoticeResponseMessage(fields)
                    // TODO: ewentualnie system logowania
                }
                'A' -> {
                    val pid = inputStream.readInt()
                    val channel = inputStream.readCString()
                    val payload = inputStream.readCString()
                    _notifications.tryEmit(io.github.octaviusframework.network.messages.NotificationResponseMessage(pid, channel, payload))
                }
                'R' -> return parseAuthentication(payloadLength)
                'E' -> return parseErrorResponse(payloadLength)
                'K' -> {
                    val pid = inputStream.readInt()
                    val key = inputStream.readInt()
                    return io.github.octaviusframework.network.messages.BackendKeyDataMessage(pid, key)
                }
                'Z' -> {
                    val status = inputStream.readByte().toInt().toChar()
                    return io.github.octaviusframework.network.messages.ReadyForQueryMessage(status)
                }
                else -> {
                    val unparsed = inputStream.readBytes(payloadLength)
                    println("IGNORUJE: Nieobsługiwany typ wiadomości synchronicznej: $tag")
                }
            }
        }
    }

    private fun parseAuthentication(payloadLength: Int): BackendMessage {
        val type = inputStream.readInt()
        return when (type) {
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
