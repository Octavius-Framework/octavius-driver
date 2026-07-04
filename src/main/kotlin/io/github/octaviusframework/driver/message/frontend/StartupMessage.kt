package io.github.octaviusframework.driver.message.frontend

import io.github.octaviusframework.driver.io.PgOutputStream

internal class StartupMessage(private val parameters: Map<String, String>) : FrontendMessage {

    override fun encode(out: PgOutputStream) {
        // Message length = self size (4) + protocol number size (4) + size of all k-v pairs + final null byte
        var length = 8
        for ((k, v) in parameters) {
            length += k.toByteArray().size + 1 // +1 za byte 0
            length += v.toByteArray().size + 1 // +1 za byte 0
        }
        length += 1 // finalny byte 0 na sam koniec listy

        // Startup Message does not have a 1-byte tag at the beginning!
        out.writeInt(length)
        out.writeInt(196608) // Protocol 3.0 (3 << 16 | 0)

        for ((k, v) in parameters) {
            out.writeCString(k)
            out.writeCString(v)
        }
        out.writeByte(0)
    }
}