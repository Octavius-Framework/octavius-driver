package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.ServerErrorMessage
import io.github.octaviusframework.driver.query.QueryContext

/**
 * Base exception for all errors in the Octavius JDBC driver.
 */
abstract class OctaviusException(
    message: String,
    val sqlState: String? = null,
    val serverErrorMessage: ServerErrorMessage? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    
    var queryContext: QueryContext? = null

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