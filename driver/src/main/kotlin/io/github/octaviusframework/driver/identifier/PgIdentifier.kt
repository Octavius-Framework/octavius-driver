package io.github.octaviusframework.driver.identifier

import io.github.octaviusframework.driver.exception.BadStatementException
import io.github.octaviusframework.driver.exception.BadStatementExceptionMessage

/**
 * Escapes a PostgreSQL identifier (e.g. table name, type name, channel name) by wrapping it in double quotes
 * and escaping any internal double quotes if it contains special characters.
 *
 * Also performs validation to ensure the identifier does not contain the NUL (\0) character,
 * which is invalid in PostgreSQL identifiers and will throw a [BadStatementException] if found.
 */
fun String.quoteAsPgIdentifier(): String {
    if (this.isBlank()) return ""
    // Always quote the identifier to match pgjdbc behavior and prevent case-folding issues.
    // We add ~10% extra capacity to account for potential escaped quotes.
    val capacity = 2 + (this.length + 10) / 10 * 11

    return buildString(capacity) {
        append('"')
        for (c in this@quoteAsPgIdentifier) {
            if (c == '\u0000') {
                throw BadStatementException(
                    BadStatementExceptionMessage.SYNTAX_ERROR,
                    cause = IllegalArgumentException("PostgreSQL identifiers cannot contain the NUL (\\0) character.")
                )
            }
            if (c == '"') append('"')
            append(c)
        }
        append('"')
    }
}
