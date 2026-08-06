package io.github.octaviusframework.driver.exception

/**
 * Categorizes the specific reason why a [TransactionException] was thrown.
 */
enum class TransactionExceptionReason {
    /** Transaction or statement timed out. */
    TIMEOUT,
    /** A required lock could not be obtained. */
    LOCK_NOT_AVAILABLE,
    /** A database deadlock was detected. */
    DEADLOCK_DETECTED,
    /** The transaction failed due to a serialization conflict. */
    SERIALIZATION_FAILURE,
    /** An unknown or unspecified transaction error. */
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
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
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
