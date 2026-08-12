package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.ServerErrorMessage
import io.github.octaviusframework.driver.query.QueryContext

/**
 * Base exception for all errors in the Octavius JDBC driver.
 *
 * @param message The exception identifier, formatted as `EXCEPTION_NAME[:REASON_ENUM]` for programmatic filtering.
 * @property sqlState The SQL state code associated with the error, if applicable.
 * @property serverErrorMessage The raw error message received from the database, if applicable.
 * @param cause The underlying exception that caused this failure, if any.
 */
abstract class OctaviusException(
    message: String,
    val sqlState: String? = null,
    val serverErrorMessage: ServerErrorMessage? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    
    /**
     * The context of the query that resulted in this exception, if applicable.
     */
    var queryContext: QueryContext? = null

    /**
     * Provides a detailed, human-readable description of the error.
     *
     * @return A detailed string message describing the exception, or null if no detailed message is available.
     */
    open fun getDetailedMessage(): String? = null

    override fun toString(): String = buildString {
        appendLine(line)
        appendLine("MESSAGE: $message")

        if (sqlState != null) {
            appendLine("SQLSTATE: $sqlState")
        }


        val detailedMsg = getDetailedMessage()
        if (detailedMsg != null) {
            appendLine("EXCEPTION DETAILS: \n$detailedMsg")
        }
        
        if (queryContext != null) {
            appendLine(queryContext.toString())
        }
        
        append(line)
        
        if (cause != null) {
            appendLine()
            appendLine("CAUSE:")
            appendLine(line)
            appendLine(cause.toString())
            append(line)
        }
    }
}

const val line = "--------------------------------------------------------------------------------"