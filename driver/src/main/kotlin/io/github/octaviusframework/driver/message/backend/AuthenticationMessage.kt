package io.github.octaviusframework.driver.message.backend

/**
 * Authentication response sent by the server (Tag 'R').
 */
internal sealed interface AuthenticationMessage : BackendMessage {
    object Ok : AuthenticationMessage
    object CleartextPassword : AuthenticationMessage
    class MD5Password(val salt: ByteArray) : AuthenticationMessage
    class SASL(val mechanisms: List<String>) : AuthenticationMessage
    class SASLContinue(val data: ByteArray) : AuthenticationMessage
    class SASLFinal(val data: ByteArray) : AuthenticationMessage

    // Currently we support the above, the rest may throw exceptions in the parsing block.
}