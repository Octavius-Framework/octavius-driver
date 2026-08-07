package io.github.octaviusframework.driver.exception

/**
 * Represents the specific reason for an invalid operation.
 */
enum class InvalidOperationExceptionReason {
    /** Transaction operations like commit or savepoint used when auto-commit is enabled. */
    AUTO_COMMIT_VIOLATION,
    /** Provided savepoint is invalid or belongs to another connection. */
    INVALID_SAVEPOINT,
    /** Attempted to operate on a closed object (e.g. Large Object or Statement). */
    OBJECT_CLOSED,
    /** Requested transaction isolation level is not supported. */
    UNSUPPORTED_ISOLATION_LEVEL,
    /** Timeout value is negative. */
    INVALID_TIMEOUT,
    /** JDBC unwrap() failed. */
    UNWRAP_ERROR,
    /** The JDBC feature is not implemented by this driver. */
    FEATURE_NOT_SUPPORTED,
    /** SQL string passed to statement was null. */
    NULL_SQL,
    /** update or execute returned result. */
    UNEXPECTED_RESULT
}

/**
 * Exception thrown when the driver attempts an operation that is not allowed in the current state or context.
 *
 * Examples include trying to commit when auto-commit is enabled, operating on a closed statement,
 * or using unsupported features.
 *
 * @property reason The specific type of invalid operation.
 * @property details Additional details about the failure.
 */
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
        InvalidOperationExceptionReason.OBJECT_CLOSED -> "Operation cannot be performed because the object is closed."
        InvalidOperationExceptionReason.UNSUPPORTED_ISOLATION_LEVEL -> "The requested transaction isolation level is not supported."
        InvalidOperationExceptionReason.INVALID_TIMEOUT -> "Timeout value cannot be negative."
        InvalidOperationExceptionReason.UNWRAP_ERROR -> "Cannot unwrap the connection/statement to the requested interface."
        InvalidOperationExceptionReason.FEATURE_NOT_SUPPORTED -> "This feature is not supported by the Octavius Driver."
        InvalidOperationExceptionReason.NULL_SQL -> "SQL string cannot be null."
        InvalidOperationExceptionReason.UNEXPECTED_RESULT -> "Execution returned a result set (rows) when none were expected. Use query() for DQL statements like SELECT."
    }
