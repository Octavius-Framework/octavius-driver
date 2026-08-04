package io.github.octaviusframework.driver.exception

enum class TransactionExceptionReason {
    TIMEOUT,
    ROLLBACK,
    LOCK_NOT_AVAILABLE,
    INVALID_TRANSACTION_STATE,
    UNKNOWN
}

/**
 * Exception thrown when a transaction fails due to a timeout, rollback, lock unavailability, or invalid state.
 */
class TransactionException(
    val reason: TransactionExceptionReason,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException("TRANSACTION_EXCEPTION:${reason.name}", cause, sqlState) {
    
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(reason)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(reason: TransactionExceptionReason): String =
    when (reason) {
        TransactionExceptionReason.TIMEOUT -> "The transaction or statement timed out."
        TransactionExceptionReason.ROLLBACK -> "The transaction was rolled back."
        TransactionExceptionReason.LOCK_NOT_AVAILABLE -> "A required lock could not be obtained."
        TransactionExceptionReason.INVALID_TRANSACTION_STATE -> "The transaction is in an invalid state."
        TransactionExceptionReason.UNKNOWN -> "An unknown transaction exception occurred."
    }
