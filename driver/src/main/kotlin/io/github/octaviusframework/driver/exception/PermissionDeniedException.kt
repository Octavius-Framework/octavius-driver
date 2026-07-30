package io.github.octaviusframework.driver.exception

class PermissionDeniedException(
    message: String,
    cause: Throwable? = null,
    sqlState: String? = null,
    val schema: String? = null,
    val table: String? = null,
    val column: String? = null,
    val datatype: String? = null,
    val routine: String? = null
) : OctaviusException(
    message = message,
    cause = cause,
    sqlState = sqlState
) {
    override fun getDetailedMessage(): String? {
        if (schema == null && table == null && column == null && datatype == null && routine == null) {
            return null
        }
        return buildString {
            if (schema != null) appendLine("Schema: $schema")
            if (table != null) appendLine("Table: $table")
            if (column != null) appendLine("Column: $column")
            if (datatype != null) appendLine("Datatype: $datatype")
            if (routine != null) appendLine("Routine: $routine")
        }
    }
}
