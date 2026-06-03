package io.github.octaviusframework.types

import java.nio.ByteBuffer

interface PgDecoder<T> {
    fun decodeBinary(bytes: ByteArray): T
}

object IntDecoder : PgDecoder<Int> {
    override fun decodeBinary(bytes: ByteArray): Int {
        require(bytes.size == 4) { "Int4 must be exactly 4 bytes" }
        return ByteBuffer.wrap(bytes).int
    }
}

object StringDecoder : PgDecoder<String> {
    override fun decodeBinary(bytes: ByteArray): String {
        return String(bytes, Charsets.UTF_8)
    }
}
