package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.ServerErrorMessage

/**
 * Exception thrown for generic or unknown database errors that cannot be mapped to a specific [OctaviusException] subclass.
 *
 * @property details Additional, human-readable details about the unknown error.
 * @param sqlState The SQL state code returned by the database.
 * @param serverErrorMessage The original error message from the database server.
 */
class UncategorizedDatabaseException(
    val details: String,
    sqlState: String? = null,
    serverErrorMessage: ServerErrorMessage? = null
) : OctaviusException("UNCATEGORIZED_DATABASE_EXCEPTION", sqlState, serverErrorMessage) {
    override fun getDetailedMessage(): String = details
}
