package io.github.octaviusframework.driver.notification

/**
 * Represents an asynchronous notification from the PostgreSQL database (LISTEN/NOTIFY).
 *
 * @property processId Process id of the backend that executed the `NOTIFY`, as the server reported
 *   it. That is usually another session entirely, not the connection this notification arrived on -
 *   unlike the process id on [PgNotice][io.github.octaviusframework.driver.notice.PgNotice], which
 *   always identifies the connection's own backend.
 * @property channel The channel the notification was sent on.
 * @property payload The payload sent with it, empty when `NOTIFY` carried none.
 */
data class PgNotification(
    val processId: Int,
    val channel: String,
    val payload: String
)