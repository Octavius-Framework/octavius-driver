package io.github.octaviusframework.driver.exception

// ------------------- INITIALIZATION -------------------

/**
 * Represents the specific reason for an initialization failure during database connection or authentication.
 */
enum class InitializationExceptionMessage {
    UNSUPPORTED_MECHANISM,
    PROTOCOL_VIOLATION,
    MISSING_PROTOCOL_PARAMETER,
    SERVER_REJECTED_CREDENTIALS,
    UNSUPPORTED_PASSWORD_ENCRYPTION,
    SSL_ERROR,
    UNSUPPORTED_SERVER_VERSION,
    CONNECTION_ERROR
}

/**
 * Exception thrown when the driver fails to establish a connection or authenticate with the database server.
 *
 * This typically occurs during the initial connection handshake and can be caused by network issues,
 * invalid credentials, unsupported protocols, or incompatible server versions.
 *
 * @property messageEnum The specific type of initialization failure.
 * @property details Additional, human-readable details about the failure.
 */
class InitializationException(
    val messageEnum: InitializationExceptionMessage,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException("INITIALIZATION_EXCEPTION:${messageEnum.name}", cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(messageEnum: InitializationExceptionMessage): String =
    when (messageEnum) {
        InitializationExceptionMessage.UNSUPPORTED_MECHANISM -> "Server does not support the required authentication mechanism (e.g., SCRAM-SHA-256)."
        InitializationExceptionMessage.PROTOCOL_VIOLATION -> "Unexpected message received during authentication protocol."
        InitializationExceptionMessage.MISSING_PROTOCOL_PARAMETER -> "Missing expected parameter in the server's authentication message."
        InitializationExceptionMessage.SERVER_REJECTED_CREDENTIALS -> "Authentication failed: Invalid username or password."
        InitializationExceptionMessage.UNSUPPORTED_PASSWORD_ENCRYPTION -> "Server requested an unsupported password encryption method (like Cleartext or MD5)."
        InitializationExceptionMessage.SSL_ERROR -> "SSL negotiation failed or is not supported by the server."
        InitializationExceptionMessage.UNSUPPORTED_SERVER_VERSION -> "Unsupported PostgreSQL server version. Octavius requires version 18 or higher."
        InitializationExceptionMessage.CONNECTION_ERROR -> "Could not connect to the database."
    }
