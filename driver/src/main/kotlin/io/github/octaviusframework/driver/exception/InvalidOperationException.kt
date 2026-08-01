package io.github.octaviusframework.driver.exception

enum class InvalidOperationExceptionMessage {
    AUTO_COMMIT_VIOLATION,
    INVALID_SAVEPOINT,
    STATEMENT_CLOSED,
    UNSUPPORTED_ISOLATION_LEVEL,
    INVALID_TIMEOUT,
    UNWRAP_ERROR,
    FEATURE_NOT_SUPPORTED,
    NULL_SQL,
    UNEXPECTED_RESULT
}

class InvalidOperationException(
    val messageEnum: InvalidOperationExceptionMessage,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException(messageEnum.name, cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(messageEnum: InvalidOperationExceptionMessage): String =
    when (messageEnum) {
        InvalidOperationExceptionMessage.AUTO_COMMIT_VIOLATION -> "Operation (like setting a savepoint or commit/rollback) is not allowed when auto-commit is enabled."
        InvalidOperationExceptionMessage.INVALID_SAVEPOINT -> "Invalid savepoint operation."
        InvalidOperationExceptionMessage.STATEMENT_CLOSED -> "Operation cannot be performed because the statement is closed."
        InvalidOperationExceptionMessage.UNSUPPORTED_ISOLATION_LEVEL -> "The requested transaction isolation level is not supported."
        InvalidOperationExceptionMessage.INVALID_TIMEOUT -> "Timeout value cannot be negative."
        InvalidOperationExceptionMessage.UNWRAP_ERROR -> "Cannot unwrap the connection/statement to the requested interface."
        InvalidOperationExceptionMessage.FEATURE_NOT_SUPPORTED -> "This feature is not supported by the Octavius Driver."
        InvalidOperationExceptionMessage.NULL_SQL -> "SQL string cannot be null."
        InvalidOperationExceptionMessage.UNEXPECTED_RESULT -> "Execution returned a result set (rows) when none were expected. Use query() for DQL statements like SELECT."
    }
