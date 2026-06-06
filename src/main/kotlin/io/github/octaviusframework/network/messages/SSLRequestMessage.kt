package io.github.octaviusframework.network.messages

import io.github.octaviusframework.io.PgOutputStream

class SSLRequestMessage : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        out.writeInt(8)
        out.writeInt(80877103)
    }
}
