package io.github.octaviusframework.driver.notice

/**
 * Represents a notice or warning message received from the PostgreSQL backend.
 */
class PgNotice(val rawFields: Map<Char, String>) {
    val severity: String get() = rawFields['V'] ?: rawFields['S'] ?: "NOTICE"
    val code: String get() = rawFields['C'] ?: "00000"
    val message: String get() = rawFields['M'] ?: "Unknown message"
    val detail: String? get() = rawFields['D']
    val hint: String? get() = rawFields['H']
    val where: String? get() = rawFields['W']

    override fun toString(): String = buildString {
        append("Postgres $severity [$code]: $message")
        if (detail != null) append(" | Detail: $detail")
        if (hint != null) append(" | Hint: $hint")
        if (where != null) append(" | Where: $where")
    }
}