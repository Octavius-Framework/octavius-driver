package io.github.octaviusframework.driver.notice

/**
 * Represents a notice or warning message received from the PostgreSQL backend.
 *
 * @property processId Process id of the backend serving this connection, the one that raised the
 *   notice. It is not a field of the `NoticeResponse` message - the driver carries it over from the
 *   `BackendKeyData` received at startup, so a notice arriving before that one reports `-1`. That
 *   window is a single message wide and nothing reaches it at the default verbosity; connecting
 *   with `client_min_messages=debug5` fills it with the backend's own catalog-reading transaction.
 *   What it is for is a handler shared between connections: a Kotlin
 *   `object` [NoticeHandler] sees the notices of every connection in a pool, and this is what tells
 *   them apart. Note that
 *   [PgNotification][io.github.octaviusframework.driver.notification.PgNotification] carries a
 *   process id of its own meaning something else - the *foreign* backend that executed `NOTIFY`,
 *   not this connection's.
 * @property rawFields Every field the server sent, keyed by its protocol field code, exactly as it
 *   arrived.
 */
class PgNotice(val processId: Int, val rawFields: Map<Char, String>) {
    val severity: String get() = rawFields['V'] ?: rawFields['S'] ?: "NOTICE"
    val code: String get() = rawFields['C'] ?: "00000"
    val message: String get() = rawFields['M'] ?: "Unknown message"
    val detail: String? get() = rawFields['D']
    val hint: String? get() = rawFields['H']
    val where: String? get() = rawFields['W']

    override fun toString(): String = buildString {
        append("[PID: $processId] Postgres $severity [$code]: $message")
        if (detail != null) append(" | Detail: $detail")
        if (hint != null) append(" | Hint: $hint")
        if (where != null) append(" | Where: $where")
    }
}