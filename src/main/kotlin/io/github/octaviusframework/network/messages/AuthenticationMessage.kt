package io.github.octaviusframework.network.messages

/**
 * Odpowiedź uwierzytelniająca wysyłana przez serwer (Tag 'R').
 */
sealed interface AuthenticationMessage : BackendMessage {
    object Ok : AuthenticationMessage
    object CleartextPassword : AuthenticationMessage
    class MD5Password(val salt: ByteArray) : AuthenticationMessage
    class SASL(val mechanisms: List<String>) : AuthenticationMessage
    class SASLContinue(val data: ByteArray) : AuthenticationMessage
    class SASLFinal(val data: ByteArray) : AuthenticationMessage
    
    // Na ten moment wspieramy te powyżej, reszta może rzucać wyjątki w bloku parsującym.
}
