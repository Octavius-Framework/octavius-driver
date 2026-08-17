package io.github.octaviusframework.driver.util

/** Longest rendering [formatDiagnosticValue] will produce before cutting a value short. */
internal const val DIAGNOSTIC_VALUE_MAX_LENGTH = 100

/** How many elements of a container are rendered before the remainder is counted instead. */
private const val DIAGNOSTIC_MAX_ELEMENTS = 10

/** How far into nested containers the rendering descends before it stops looking. */
private const val DIAGNOSTIC_MAX_DEPTH = 3

/**
 * Renders one value for a diagnostic - an exception's query context, or a traced statement.
 *
 * Every place a parameter is shown to a human goes through here, so a value reads the same
 * whichever of them the reader is looking at.
 *
 * The rendering is bounded **as it is built**, not trimmed afterwards, and that distinction is the
 * whole point. Cutting `value.toString()` down to size materialises the entire thing first: this
 * driver's own bulk-write idiom passes an array of ten thousand elements as a single parameter, so
 * "render it all, then keep a hundred characters" would build a string of megabytes to discard
 * almost all of it - on the error path, where something has already gone wrong.
 *
 * So containers are walked element by element against a shared budget and abandoned once it is
 * spent, and a [ByteArray] is named rather than dumped, its `toString` being an identity hash
 * anyway. The one thing that cannot be bounded is a class the driver does not know: its `toString`
 * is a single opaque call that renders whatever it renders. Small DTOs are worth reading and most
 * are small, so it is still called - but a custom `toString` that assembles something enormous is
 * beyond anything this can do about it.
 */
internal fun formatDiagnosticValue(value: Any?): String {
    val out = StringBuilder(DIAGNOSTIC_VALUE_MAX_LENGTH + 8)
    appendDiagnosticValue(out, value, depth = 0)
    return if (out.length > DIAGNOSTIC_VALUE_MAX_LENGTH) {
        out.substring(0, DIAGNOSTIC_VALUE_MAX_LENGTH) + "..."
    } else {
        out.toString()
    }
}

/** Appends [value] to [out], stopping as soon as the budget is spent. */
private fun appendDiagnosticValue(out: StringBuilder, value: Any?, depth: Int) {
    if (out.length > DIAGNOSTIC_VALUE_MAX_LENGTH) return

    when (value) {
        null -> out.append("null")

        // Named, never dumped: a bytea parameter runs to megabytes and its toString is an
        // identity hash, so printing it costs everything and says nothing.
        is ByteArray -> out.append("ByteArray(").append(value.size).append(" bytes)")

        // Appended as a range, so a 40 MB text parameter never becomes a 40 MB copy.
        is CharSequence -> out.append(value, 0, minOf(value.length, DIAGNOSTIC_VALUE_MAX_LENGTH + 1))

        is Map<*, *> -> appendEntries(out, value.size, value.entries.asSequence(), depth) { entry ->
            appendDiagnosticValue(out, entry.key, depth + 1)
            out.append('=')
            appendDiagnosticValue(out, entry.value, depth + 1)
        }

        else -> {
            val elements = elementsOf(value)
            if (elements != null) {
                appendEntries(out, elements.first, elements.second, depth) { element ->
                    appendDiagnosticValue(out, element, depth + 1)
                }
            } else {
                // A type the driver does not know. See the note on [formatDiagnosticValue]: this
                // call is the one thing here that is not bounded.
                out.append(value.toString())
            }
        }
    }
}

/**
 * Renders a container as `[a, b, c, … +n more]`, stopping at [DIAGNOSTIC_MAX_ELEMENTS], at
 * [DIAGNOSTIC_MAX_DEPTH], or as soon as the budget runs out - whichever comes first.
 */
private inline fun <T> appendEntries(
    out: StringBuilder,
    size: Int,
    entries: Sequence<T>,
    depth: Int,
    appendOne: (T) -> Unit
) {
    if (depth >= DIAGNOSTIC_MAX_DEPTH) {
        out.append("(").append(size).append(" elements)")
        return
    }

    out.append('[')
    var rendered = 0
    for (entry in entries) {
        if (rendered == DIAGNOSTIC_MAX_ELEMENTS || out.length > DIAGNOSTIC_VALUE_MAX_LENGTH) break
        if (rendered > 0) out.append(", ")
        appendOne(entry)
        rendered++
    }
    if (rendered < size) out.append(", ... +").append(size - rendered).append(" more")
    out.append(']')
}

/**
 * The size and elements of [value] when it is a container the driver can walk, or null when it is
 * not. Kotlin's primitive arrays are listed one by one because none of them shares an interface
 * with the others, and every one of them has an identity hash for a `toString`.
 */
private fun elementsOf(value: Any): Pair<Int, Sequence<Any?>>? = when (value) {
    is Collection<*> -> value.size to value.asSequence()
    is Array<*> -> value.size to value.asSequence()
    is IntArray -> value.size to value.asSequence()
    is LongArray -> value.size to value.asSequence()
    is ShortArray -> value.size to value.asSequence()
    is DoubleArray -> value.size to value.asSequence()
    is FloatArray -> value.size to value.asSequence()
    is BooleanArray -> value.size to value.asSequence()
    is CharArray -> value.size to value.asSequence()
    else -> null
}
