package io.github.octaviusframework.driver.message.frontend

import io.github.octaviusframework.driver.io.PgOutputStream

/**
 * Sends data during a COPY IN operation.
 */
internal class FrontendCopyDataMessage(
    private val data: ByteArray,
    private val offset: Int = 0,
    private val length: Int = data.size
) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        out.writeByte('d'.code.toByte())
        out.writeInt(4 + length)
        out.writeBytes(data, offset, length)
    }
}

/**
 * Indicates completion of a COPY IN operation from the frontend.
 */
internal class FrontendCopyDoneMessage : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        out.writeByte('c'.code.toByte())
        out.writeInt(4)
    }
}

/**
 * Fails a COPY IN operation from the frontend.
 */
internal class FrontendCopyFailMessage(private val errorMessage: String) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        out.writeByte('f'.code.toByte())
        val msgBytes = errorMessage.toByteArray(Charsets.UTF_8)
        out.writeInt(4 + msgBytes.size + 1)
        out.writeBytes(msgBytes)
        out.writeByte(0)
    }
}
