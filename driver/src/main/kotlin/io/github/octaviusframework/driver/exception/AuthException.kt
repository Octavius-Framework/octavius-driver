package io.github.octaviusframework.driver.exception

// ------------------- AUTHENTICATION -------------------

enum class AuthExceptionMessage {
    UNSUPPORTED_MECHANISM,
    PROTOCOL_VIOLATION,
    MISSING_PROTOCOL_PARAMETER,
    SERVER_REJECTED_CREDENTIALS,
    UNSUPPORTED_PASSWORD_ENCRYPTION
}

class AuthException(
    val messageEnum: AuthExceptionMessage,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException(messageEnum.name, cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(messageEnum: AuthExceptionMessage): String =
    when (messageEnum) {
        AuthExceptionMessage.UNSUPPORTED_MECHANISM -> "Server does not support the required authentication mechanism (e.g., SCRAM-SHA-256)."
        AuthExceptionMessage.PROTOCOL_VIOLATION -> "Unexpected message received during authentication protocol."
        AuthExceptionMessage.MISSING_PROTOCOL_PARAMETER -> "Missing expected parameter in the server's authentication message."
        AuthExceptionMessage.SERVER_REJECTED_CREDENTIALS -> "Authentication failed: Invalid username or password."
        AuthExceptionMessage.UNSUPPORTED_PASSWORD_ENCRYPTION -> "Server requested an unsupported password encryption method (like Cleartext or MD5)."
    }
