package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.ServerErrorMessage

/**
 * Exception thrown when a generic database system error occurs.
 *
 * This typically represents internal server errors, disk full errors, out of memory errors,
 * or other administrative failures originating from the database engine itself rather than
 * from user query mistakes.
 *
 * @property errorMessage The primary error message provided by the database system.
 * @param sqlState The SQL state code returned by the database.
 * @param serverErrorMessage The original error message from the database server.
 */
class DatabaseSystemException(
    val errorMessage: String,
    sqlState: String,
    serverErrorMessage: ServerErrorMessage
) : OctaviusException("DATABASE_SYSTEM_EXCEPTION", sqlState = sqlState, serverErrorMessage = serverErrorMessage) {
    override fun getDetailedMessage(): String = errorMessage
}