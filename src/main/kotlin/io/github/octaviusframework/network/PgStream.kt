package io.github.octaviusframework.network

import io.github.octaviusframework.io.PgInputStream
import io.github.octaviusframework.io.PgOutputStream
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

    override fun close() {
        if (!socket.isClosed) {
            socket.close()
        }
    }
}
