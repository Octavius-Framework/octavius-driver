package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.ServerErrorMessage

/**
 * Categorizes the specific reason why a [ConcurrencyException] was thrown.
 */
enum class ConcurrencyExceptionReason {
    /** A required lock could not be obtained. */
    LOCK_NOT_AVAILABLE,
    /** A database deadlock was detected. */
    DEADLOCK_DETECTED,
    /** The transaction failed due to a serialization conflict. */
    SERIALIZATION_FAILURE,
    /** An unknown or unspecified concurrency error. */
    UNKNOWN
}

/**
 * Exception thrown when a transaction fails due to concurrency issues like deadlocks, lock unavailability, or serialization failures.
 *
 * @property reason The specific type of concurrency issue.
 * @param sqlState The SQL state code returned by the database.
 * @param serverErrorMessage The original error message from the database server.
 */
class ConcurrencyException(
    val reason: ConcurrencyExceptionReason,
    sqlState: String,
    serverErrorMessage: ServerErrorMessage
) : OctaviusException("CONCURRENCY_EXCEPTION:${reason.name}", sqlState, serverErrorMessage) {

    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        if (serverErrorMessage?.message != null) appendLine("DB message: ${serverErrorMessage.message}")
    }
}

private fun generateDeveloperMessage(reason: ConcurrencyExceptionReason): String =
    when (reason) {
        ConcurrencyExceptionReason.LOCK_NOT_AVAILABLE -> "A required lock could not be obtained."
        ConcurrencyExceptionReason.DEADLOCK_DETECTED -> "A deadlock was detected in the database."
        ConcurrencyExceptionReason.SERIALIZATION_FAILURE -> "The transaction failed due to a serialization failure."
        ConcurrencyExceptionReason.UNKNOWN -> "An unknown concurrency exception occurred."
    }
