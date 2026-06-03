package io.github.octaviusframework.network.messages

import io.github.octaviusframework.io.PgOutputStream
import java.nio.charset.StandardCharsets

/**
 * Pierwsza odpowiedź klienta w mechanizmie SASL.
 */
class SASLInitialResponse(private val mechanism: String, private val clientFirstMessage: String) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val mechBytes = mechanism.toByteArray(StandardCharsets.UTF_8)
        val dataBytes = clientFirstMessage.toByteArray(StandardCharsets.UTF_8)

        // Rozmiar wiadomości = 4 + rozmiar mechanism (z nullem) + 4 (rozmiar data) + rozmiar data
        val length = 4 + mechBytes.size + 1 + 4 + dataBytes.size

        out.writeByte('p'.code.toByte())
        out.writeInt(length)
        out.writeCString(mechanism)
        out.writeInt(dataBytes.size)
        out.writeBytes(dataBytes)
    }
}

/**
 * Kolejna odpowiedź klienta w mechanizmie SASL (zwykle wysyłająca client-final-message).
 */
class SASLResponse(private val clientFinalMessage: String) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val dataBytes = clientFinalMessage.toByteArray(StandardCharsets.UTF_8)
        val length = 4 + dataBytes.size

        out.writeByte('p'.code.toByte())
        out.writeInt(length)
        out.writeBytes(dataBytes)
    }
}
