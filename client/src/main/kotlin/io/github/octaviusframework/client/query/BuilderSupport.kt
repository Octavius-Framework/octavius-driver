package io.github.octaviusframework.client.query

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason

/**
 * Refuses a query the builder cannot render, naming what is missing.
 *
 * Raised as [InvalidOperationException] `INVALID_ARGUMENT` because that is what it is - an argument the caller
 * got wrong - and because the failure boundary already counts that as a caller bug, so it is thrown rather
 * than turned into a value even under `asResult()`. A statement that cannot be assembled is the same on every
 * run; there is nothing to branch on.
 */
internal inline fun requireBuildable(value: Boolean, message: () -> String) {
    if (!value) {
        throw InvalidOperationException(InvalidOperationExceptionReason.INVALID_ARGUMENT, details = message())
    }
}

/**
 * The `WITH` clause, which every builder here can carry and none of them renders differently.
 *
 * Held as a field rather than inherited, so that `with` and `recursive` stay declared on each builder and
 * return that builder's own type - two one-line methods apiece, against a self-typed base class whose type
 * parameter would show up in every signature the reader sees.
 */
internal class CteClause {

    private val entries = LinkedHashMap<String, String>()
    var recursive: Boolean = false

    fun add(name: String, query: String) {
        requireBuildable(name.isNotBlank()) { "A common table expression needs a name." }
        requireBuildable(query.isNotBlank()) { "The common table expression '$name' has no query." }
        entries[name] = query
    }

    fun copyFrom(other: CteClause) {
        entries.putAll(other.entries)
        recursive = other.recursive
    }

    /** Renders the clause with its trailing newline, or nothing at all where no CTE was added. */
    fun render(): String {
        if (entries.isEmpty()) return ""
        return buildString {
            append("WITH ")
            if (recursive) append("RECURSIVE ")
            entries.entries.joinTo(this, ",\n     ") { (name, query) -> "$name AS ($query)" }
            append("\n")
        }
    }
}

/**
 * Appends a clause where its body carries anything, and does nothing where it does not.
 *
 * The whole of how an optional clause disappears: `where(null)` and `where("")` both leave no `WHERE` behind,
 * which is what lets a filter assembled at runtime be passed straight in.
 */
internal fun StringBuilder.appendClause(keyword: String, body: String?) {
    if (!body.isNullOrBlank()) {
        append('\n').append(keyword).append(' ').append(body)
    }
}
