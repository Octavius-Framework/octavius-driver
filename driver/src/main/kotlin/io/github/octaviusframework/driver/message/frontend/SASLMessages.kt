package io.github.octaviusframework.driver.message.frontend

import io.github.octaviusframework.driver.io.PgOutputStream
import java.nio.charset.StandardCharsets

/**
 * First client response in the SASL mechanism.
 */
internal class SASLInitialResponse(private val mechanism: String, private val clientFirstMessage: String) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val mechBytes = mechanism.toByteArray(StandardCharsets.UTF_8)
        val dataBytes = clientFirstMessage.toByteArray(StandardCharsets.UTF_8)

        // Message size = 4 + mechanism size (with null) + 4 (data size) + data size
        val length = 4 + mechBytes.size + 1 + 4 + dataBytes.size

        out.writeByte('p'.code.toByte())
        out.writeInt(length)
        out.writeCString(mechanism)
        out.writeInt(dataBytes.size)
        out.writeBytes(dataBytes)
    }
}

/**
 * Next client response in the SASL mechanism (usually sending client-final-message).
 */
internal class SASLResponse(private val clientFinalMessage: String) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val dataBytes = clientFinalMessage.toByteArray(StandardCharsets.UTF_8)
        val length = 4 + dataBytes.size

        out.writeByte('p'.code.toByte())
        out.writeInt(length)
        out.writeBytes(dataBytes)
    }
}

/**
 * The password itself, in the clear, in answer to an `AuthenticationCleartextPassword` request.
 *
 * It shares the `p` tag with the SASL responses above because the tag says only "authentication
 * material"; which of them the server is reading is decided by the request it sent. Nothing here
 * protects the password - that is the connection's job, and the driver only sends this over one
 * that is encrypted.
 */
internal class PasswordMessage(private val password: String) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val length = 4 + passwordBytes.size + 1

        out.writeByte('p'.code.toByte())
        out.writeInt(length)
        out.writeCString(password)
    }
}
