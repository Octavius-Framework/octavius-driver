package io.github.octaviusframework.driver.message.backend

/**
 * Authentication response sent by the server (Tag 'R').
 */
internal sealed interface AuthenticationMessage : BackendMessage {
    /**
     * Indicates that the authentication was successful.
     */
    object Ok : AuthenticationMessage

    /**
     * Indicates that the server requires a clear-text password.
     */
    object CleartextPassword : AuthenticationMessage

    /**
     * Indicates that the server requires an MD5-encrypted password.
     *
     * @property salt The salt to use when encrypting the password.
     */
    class MD5Password(val salt: ByteArray) : AuthenticationMessage

    /**
     * Indicates that the server requires SASL authentication.
     *
     * @property mechanisms List of SASL mechanisms supported by the server.
     */
    class SASL(val mechanisms: List<String>) : AuthenticationMessage

    /**
     * Contains the next step of SASL authentication data from the server.
     *
     * @property data The SASL challenge data.
     */
    class SASLContinue(val data: ByteArray) : AuthenticationMessage

    /**
     * Contains the final step of SASL authentication data from the server.
     *
     * @property data The final SASL outcome data.
     */
    class SASLFinal(val data: ByteArray) : AuthenticationMessage

    // Currently we support the above, the rest may throw exceptions in the parsing block.
}