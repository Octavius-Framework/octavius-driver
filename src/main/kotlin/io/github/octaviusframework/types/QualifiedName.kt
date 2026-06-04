package io.github.octaviusframework.types

import io.github.octaviusframework.exceptions.BadStatementException
import io.github.octaviusframework.exceptions.BadStatementExceptionMessage

/**
 * Represents a qualified PostgreSQL name (schema + object name).
 * Handles quoting correctly even if names contain dots.
 */
data class QualifiedName(
    val schema: String,
    val name: String,
    val isArray: Boolean = false
) {
    override fun toString(): String {
        val base = if (schema.isBlank()) name else "$schema.$name"
        return if (isArray) "$base[]" else base
    }

    /**
     * Returns a SQL-safe representation. 
     * Respects existing quotes and adds new ones only if necessary (e.g. dots in name).
     */
    fun quote(): String {
        val quotedBase = if (schema.isBlank()) {
            name.quoteAsPgIdentifier()
        } else {
            "${schema.quoteAsPgIdentifier()}.${name.quoteAsPgIdentifier()}"
        }
        return if (isArray) "$quotedBase[]" else quotedBase
    }

    fun asArray(): QualifiedName = copy(isArray = true)
}

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
