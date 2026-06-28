package io.github.octaviusframework.driver.message.frontend

import io.github.octaviusframework.driver.io.PgOutputStream

/**
 * Connection termination message (Tag 'X').
 */
class TerminateMessage : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        out.writeByte('X'.code.toByte())
        out.writeInt(4)
    }
}
