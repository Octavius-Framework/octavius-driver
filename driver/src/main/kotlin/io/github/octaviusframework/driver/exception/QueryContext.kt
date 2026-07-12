package io.github.octaviusframework.driver.exception

/**
 * Context of a database operation execution.
 *
 * Contains all the information needed to reproduce or debug a failed query,
 * including both the high-level query and the low-level SQL sent to the database.
 */
data class QueryContext(
    val sql: String,
    val parameters: Map<String, Any?>,
    val dbSql: String? = null,
    val dbParameters: List<Any?>? = null,
    val transactionStepIndex: Int? = null
) {
    override fun toString(): String {
        val width = 80
        val line = "=".repeat(width)
        val thinLine = "-".repeat(width)

        val paramsStr = if (parameters.isEmpty()) "none" else parameters.entries.joinToString("\n") { "${it.key} - ${it.value}" }
        val dbParamsStr = dbParameters?.mapIndexed { index, value -> "${index + 1} - $value" }?.joinToString("\n") ?: "none"

        return buildString {
            appendLine(line)
            appendLine("DATABASE EXECUTION CONTEXT")
            appendLine(line)

            if (transactionStepIndex != null) {
                appendLine("Transaction Step Index: $transactionStepIndex")
                appendLine(thinLine)
            }

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

    fun withTransactionStep(stepIndex: Int) = this.copy(transactionStepIndex = stepIndex)
}
