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

    override fun toString(): String {
        val detailedMsg = getDetailedMessage()?.let { "DETAILS: $it\n" } ?: ""
        val nestedError = cause?.toString() ?: "No cause available"
        val sqlStateSection = sqlState?.let { "SQLSTATE: $it\n" } ?: ""
        val contextSection = queryContext?.let { "$it\n" } ?: ""
        val causeSection = """
CAUSE:
------------------------------------------------------------
$nestedError
------------------------------------------------------------
"""

        return """
------------------------------------------------------------
ERROR: ${this::class.simpleName}
$sqlStateSection
MESSAGE: $message
${detailedMsg}$contextSection------------------------------------------------------------
$causeSection
"""
    }
}
