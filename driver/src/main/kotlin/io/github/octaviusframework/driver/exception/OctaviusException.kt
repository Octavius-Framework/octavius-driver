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

/**
 * How far down a cause chain [findOctaviusCause] is willing to look. Bounded so a chain that
 * loops back on itself ends rather than spinning; nothing legitimate nests this deep.
 */
private const val MAX_CAUSE_DEPTH = 16

/**
 * Finds the Octavius failure behind this throwable, if there is one.
 *
 * A driver failure that crosses a JDBC boundary rarely arrives as itself. A connection pool
 * restates it as an `SQLException` of its own, a framework restates that one in turn, and what
 * the caller finally catches names only the layer it came from - HikariCP reporting a borrow that
 * timed out says nothing about the server refusing the connection underneath it. The chain still
 * carries the original, either as a [SQLExceptionWrapper] put there by the driver's JDBC surface
 * or as a bare [OctaviusException] recorded by a pool that was trying to open a connection, so
 * anything restating a foreign failure looks for the real one here before inventing its own.
 *
 * The receiver itself is examined first, so a driver exception passed in directly is returned
 * as it stands.
 *
 * @return The first Octavius exception found on the way down the cause chain, or `null` if the
 * failure did not originate in this driver.
 */
fun Throwable.findOctaviusCause(): OctaviusException? {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        when (current) {
            is SQLExceptionWrapper -> return current.wrappedException
            is OctaviusException -> return current
        }
        current = current.cause
        depth++
    }
    return null
}
