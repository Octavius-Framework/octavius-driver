package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.ServerErrorMessage

/**
 * Exception thrown when the current database user lacks the required privileges to execute a statement or access an object.
 *
 * This exception can include optional metadata identifying the specific schema, table, column, datatype,
 * or routine where the permission denial occurred, aiding in debugging access control issues.
 *
 * @property dbMessage The primary error message the database raised.
 * @property schema The name of the schema for which permission was denied, if applicable.
 * @property table The name of the table for which permission was denied, if applicable.
 * @property column The name of the column for which permission was denied, if applicable.
 * @property datatype The name of the datatype for which permission was denied, if applicable.
 * @property routine The name of the routine (function/procedure) for which permission was denied, if applicable.
 * @param sqlState The SQL state code returned by the database.
 * @param serverErrorMessage The original error message from the database server.
 */
class PermissionDeniedException(
    sqlState: String,
    serverErrorMessage: ServerErrorMessage
) : OctaviusException(
    message = "PERMISSION_DENIED_EXCEPTION",
    sqlState = sqlState,
    serverErrorMessage = serverErrorMessage
) {
    val dbMessage: String get() = serverErrorMessage!!.message
    val schema: String? get() = serverErrorMessage!!.schema
    val table: String? get() = serverErrorMessage!!.table
    val column: String? get() = serverErrorMessage!!.column
    val datatype: String? get() = serverErrorMessage!!.datatype
    val routine: String? get() = serverErrorMessage!!.routine
    override fun getDetailedMessage(): String = buildString {
        appendLine("Database message: $dbMessage")
        if (schema != null) appendLine("Schema: $schema")
        if (table != null) appendLine("Table: $table")
        if (column != null) appendLine("Column: $column")
        if (datatype != null) appendLine("Datatype: $datatype")
        if (routine != null) appendLine("Routine: $routine")
    }
}
