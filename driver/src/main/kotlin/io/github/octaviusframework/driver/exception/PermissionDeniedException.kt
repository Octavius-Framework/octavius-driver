package io.github.octaviusframework.driver.exception

/**
 * Exception thrown when the current database user lacks the required privileges to execute a statement or access an object.
 *
 * This exception can include optional metadata identifying the specific schema, table, column, datatype,
 * or routine where the permission denial occurred, aiding in debugging access control issues.
 *
 * @property schema The name of the schema for which permission was denied, if applicable.
 * @property table The name of the table for which permission was denied, if applicable.
 * @property column The name of the column for which permission was denied, if applicable.
 * @property datatype The name of the datatype for which permission was denied, if applicable.
 * @property routine The name of the routine (function/procedure) for which permission was denied, if applicable.
 */
class PermissionDeniedException(
    val dbMessage: String,
    cause: Throwable? = null,
    sqlState: String? = null,
    val schema: String? = null,
    val table: String? = null,
    val column: String? = null,
    val datatype: String? = null,
    val routine: String? = null
) : OctaviusException(
    message = "PERMISSION_DENIED_EXCEPTION",
    cause = cause,
    sqlState = sqlState
) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("Database message: $dbMessage")
        if (schema != null) appendLine("Schema: $schema")
        if (table != null) appendLine("Table: $table")
        if (column != null) appendLine("Column: $column")
        if (datatype != null) appendLine("Datatype: $datatype")
        if (routine != null) appendLine("Routine: $routine")
    }
}
