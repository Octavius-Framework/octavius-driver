package io.github.octaviusframework.driver.message.frontend

import io.github.octaviusframework.driver.io.PgOutputStream

/**
 * Message sent by the frontend to request an SSL encrypted connection.
 */
internal class SSLRequestMessage : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        out.writeInt(8)
        out.writeInt(80877103)
    }
}