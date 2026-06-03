package io.github.octaviusframework.network.messages

import io.github.octaviusframework.io.PgOutputStream

class StartupMessage(private val parameters: Map<String, String>) : FrontendMessage {

    override fun encode(out: PgOutputStream) {
        // Długość wiadomości = rozmiar własny (4) + rozmiar numeru protokołu (4) + rozmiar wszystkich par k-v + ostateczny bajt null
        var length = 8 
        for ((k, v) in parameters) {
            length += k.toByteArray().size + 1 // +1 za byte 0
            length += v.toByteArray().size + 1 // +1 za byte 0
        }
        length += 1 // finalny byte 0 na sam koniec listy

        // Startup Message nie ma 1-bajtowego tagu na początku!
        out.writeInt(length)
        out.writeInt(196608) // Protocol 3.0 (3 << 16 | 0)
        
        for ((k, v) in parameters) {
            out.writeCString(k)
            out.writeCString(v)
        }
        out.writeByte(0)
    }
}
