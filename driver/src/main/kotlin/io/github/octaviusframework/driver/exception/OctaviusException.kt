package io.github.octaviusframework.driver.exception

/**
 * Base exception for all errors in the Octavius JDBC driver.
 */
open class OctaviusException(
    message: String,
    cause: Throwable? = null,
    val sqlState: String? = null
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