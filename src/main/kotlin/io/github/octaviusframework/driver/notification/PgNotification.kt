package io.github.octaviusframework.driver.notification

/**
 * Represents an asynchronous notification from the PostgreSQL database (LISTEN/NOTIFY).
 */
data class PgNotification(
    val processId: Int,
    val channel: String,
    val payload: String
)