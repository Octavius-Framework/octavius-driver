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
    /**
     * An argument supplied by the caller is not acceptable - out of range, null, or unsupported.
     * Covers negative timeouts, a null SQL string, an unknown isolation level, and the like.
     * The offending value is named in `details`; there is nothing to branch on here, since no
     * caller can react to its own bad argument at runtime.
     */
    INVALID_ARGUMENT,
    /** JDBC unwrap() failed. */
    UNWRAP_ERROR,
    /** The JDBC feature is not implemented by this driver. */
    FEATURE_NOT_SUPPORTED,
    /** update or execute returned result. */
    UNEXPECTED_RESULT,
    /** The connection is in copy mode and cannot be used for anything else until the COPY ends. */
    COPY_IN_PROGRESS,
    /** A statement is already executing on this connection - typically a query issued from a callback. */
    EXECUTION_IN_PROGRESS,
    /** The COPY handle has already been ended or cancelled and cannot be used again. */
    COPY_NOT_ACTIVE
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
) : OctaviusException("INVALID_OPERATION_EXCEPTION:${reason.name}") {
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
        InvalidOperationExceptionReason.INVALID_ARGUMENT -> "An argument passed to this operation is not acceptable. See the details for the value the driver rejected."
        InvalidOperationExceptionReason.UNWRAP_ERROR -> "Cannot unwrap the connection/statement to the requested interface."
        InvalidOperationExceptionReason.FEATURE_NOT_SUPPORTED -> "This feature is not supported by the Octavius Driver."
        InvalidOperationExceptionReason.UNEXPECTED_RESULT -> "Execution returned a result set (rows) when none were expected. Use query() for DQL statements like SELECT."
        InvalidOperationExceptionReason.COPY_IN_PROGRESS -> "A COPY operation is still in progress on this connection. Finish it (endCopy/cancelCopy, or read the export to its end) before using the session for anything else."
        InvalidOperationExceptionReason.EXECUTION_IN_PROGRESS -> "A statement is already executing on this connection. This usually means a query was issued from inside a forEach block or a ResultConverter, while the driver was still reading the result - such code needs a separate session."
        InvalidOperationExceptionReason.COPY_NOT_ACTIVE -> "This COPY operation has already finished. Handles are single-use - start a new one through the CopyManager."
    }
