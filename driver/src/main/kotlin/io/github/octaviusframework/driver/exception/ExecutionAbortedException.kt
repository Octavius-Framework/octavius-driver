package io.github.octaviusframework.driver.exception

/**
 * Categorizes the specific reason why a [ExecutionAbortedException] was thrown.
 */
enum class ExecutionAbortedExceptionReason {
    /** The session or transaction timed out (idle_in_transaction_session_timeout or transaction_timeout). */
    TRANSACTION_TIMEOUT,
    /** The query was canceled, either manually or due to statement_timeout. */
    QUERY_CANCELED
}

/**
 * Exception thrown when a query execution is aborted by the database engine (e.g. due to a statement timeout or manual cancel request).
 */
class ExecutionAbortedException(
    val reason: ExecutionAbortedExceptionReason,
    val dbMessage: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException("EXECUTION_ABORTED_EXCEPTION:${reason.name}", cause, sqlState) {
    
    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        if (dbMessage != null) appendLine("DB message: $dbMessage")
    }
}

private fun generateDeveloperMessage(reason: ExecutionAbortedExceptionReason): String =
    when (reason) {
        ExecutionAbortedExceptionReason.TRANSACTION_TIMEOUT -> "The transaction or session timed out."
        ExecutionAbortedExceptionReason.QUERY_CANCELED -> "The query was canceled (manually or via statement_timeout)."
    }
