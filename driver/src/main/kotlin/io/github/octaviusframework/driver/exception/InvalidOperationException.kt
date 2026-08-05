package io.github.octaviusframework.driver.exception

enum class InvalidOperationExceptionReason {
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
    val reason: InvalidOperationExceptionReason,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException("INVALID_OPERATION_EXCEPTION:${reason.name}", cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(reason: InvalidOperationExceptionReason): String =
    when (reason) {
        InvalidOperationExceptionReason.AUTO_COMMIT_VIOLATION -> "Operation (like setting a savepoint or commit/rollback) is not allowed when auto-commit is enabled."
        InvalidOperationExceptionReason.INVALID_SAVEPOINT -> "Invalid savepoint operation."
        InvalidOperationExceptionReason.STATEMENT_CLOSED -> "Operation cannot be performed because the statement is closed."
        InvalidOperationExceptionReason.UNSUPPORTED_ISOLATION_LEVEL -> "The requested transaction isolation level is not supported."
        InvalidOperationExceptionReason.INVALID_TIMEOUT -> "Timeout value cannot be negative."
        InvalidOperationExceptionReason.UNWRAP_ERROR -> "Cannot unwrap the connection/statement to the requested interface."
        InvalidOperationExceptionReason.FEATURE_NOT_SUPPORTED -> "This feature is not supported by the Octavius Driver."
        InvalidOperationExceptionReason.NULL_SQL -> "SQL string cannot be null."
        InvalidOperationExceptionReason.UNEXPECTED_RESULT -> "Execution returned a result set (rows) when none were expected. Use query() for DQL statements like SELECT."
    }
