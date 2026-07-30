package io.github.octaviusframework.driver.exception

/**
 * Exception thrown when a generic database system error occurs.
 *
 * This typically represents internal server errors, disk full errors, out of memory errors,
 * or other administrative failures originating from the database engine itself rather than
 * from user query mistakes.
 */
class DatabaseSystemException(
    message: String,
    sqlState: String? = null
) : OctaviusException(message, sqlState = sqlState)