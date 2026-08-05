package io.github.octaviusframework.driver.exception

enum class TransactionExceptionReason {
    TIMEOUT,
    LOCK_NOT_AVAILABLE,
    DEADLOCK_DETECTED,
    SERIALIZATION_FAILURE,
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
        TransactionExceptionReason.LOCK_NOT_AVAILABLE -> "A required lock could not be obtained."
        TransactionExceptionReason.DEADLOCK_DETECTED -> "A deadlock was detected in the database."
        TransactionExceptionReason.SERIALIZATION_FAILURE -> "The transaction failed due to a serialization failure."
        TransactionExceptionReason.UNKNOWN -> "An unknown transaction exception occurred."
    }
