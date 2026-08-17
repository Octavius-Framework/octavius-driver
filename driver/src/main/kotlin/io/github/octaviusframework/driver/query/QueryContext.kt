package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.util.formatDiagnosticValue

/**
 * Context of a database operation execution.
 *
 * Contains all the information needed to reproduce or debug a failed query,
 * including both the high-level query and the low-level SQL sent to the database.
 *
 * Attached to an [OctaviusException][io.github.octaviusframework.driver.exception.OctaviusException] as
 * it propagates out of a query, and rendered into its `toString`. The two levels differ only for a named
 * query, where the statement is rewritten before it is sent; for a native query they are the same text.
 *
 * @property sql The statement as it was written, named parameters and all.
 * @property parameters The values as they were supplied — by name for a named query, by position as
 *   `"1"`, `"2"`, … for a native one.
 * @property dbSql The statement actually sent to the server, with names rewritten to `$n`.
 *   `null` where the failure happened before that rewriting.
 * @property dbParameters The values in the positional order they were bound in. `null` where the
 *   failure happened before binding.
 */
data class QueryContext(
    val sql: String,
    val parameters: Map<String, Any?>,
    val dbSql: String? = null,
    val dbParameters: List<Any?>? = null,
) {
    override fun toString(): String {
        val width = 80
        val line = "=".repeat(width)
        val thinLine = "-".repeat(width)

        // Rendered through the shared formatter rather than interpolated: a `bytea` parameter would
        // otherwise print as an identity hash and a large `text` or `json` one would print in full,
        // putting the whole value into whatever log this exception is written to.
        val paramsStr = if (parameters.isEmpty()) "none"
            else parameters.entries.joinToString("\n") { "${it.key} - ${formatDiagnosticValue(it.value)}" }
        val dbParamsStr = dbParameters
            ?.mapIndexed { index, value -> "${index + 1} - ${formatDiagnosticValue(value)}" }
            ?.joinToString("\n") ?: "none"

        return buildString {
            appendLine(line)
            appendLine("DATABASE EXECUTION CONTEXT")
            appendLine(line)

            appendLine("HIGH-LEVEL SQL:")
            appendLine(sql.trim())
            appendLine(thinLine)

            appendLine("PARAMETERS:")
            appendLine(paramsStr)

            if (dbSql != null) {
                appendLine(thinLine)
                appendLine("DATABASE-LEVEL SQL (SENT TO DB):")
                appendLine(dbSql.trim())
                appendLine(thinLine)

                appendLine("DATABASE-LEVEL PARAMETERS:")
                appendLine(dbParamsStr)
            }

            appendLine(line)
        }
    }
}