package io.github.octaviusframework.driver.message

import io.github.octaviusframework.driver.message.backend.ErrorOrNoticeMessage

/**
 * A publicly exposed data class containing all detailed fields provided by the PostgreSQL backend
 * when an error occurs.
 *
 * Reachable as [OctaviusException.serverErrorMessage][io.github.octaviusframework.driver.exception.OctaviusException.serverErrorMessage]
 * on any exception the server raised, and the place to look when the driver's own categorization is
 * not specific enough
 *
 * @property severity `ERROR`, `FATAL` or `PANIC`, always in English: it is read from the non-localized
 *   field, which every server the driver will talk to sends. Safe to compare against as a string.
 * @property localizedSeverity The same severity as the server translated it, which is what a non-English
 *   `lc_messages` changes. Fit for showing to a person, not for comparing against.
 * @property code The five-character SQLSTATE.
 * @property message The primary human-readable message.
 * @property detail Secondary detail carrying more about the problem.
 * @property hint A suggestion of what to do about it.
 * @property position The 1-based character position of the error in the submitted statement.
 * @property internalPosition The equivalent position within [internalQuery], for an error raised inside
 *   a statement the server generated itself.
 * @property internalQuery The server-generated statement the error arose in, such as the body of a
 *   PL/pgSQL function.
 * @property where The call stack the error arose in.
 * @property schema The schema of the object at fault, for the errors that name one.
 * @property table The table of the object at fault.
 * @property column The column at fault.
 * @property datatype The data type at fault.
 * @property constraint The constraint that was violated.
 * @property file The PostgreSQL source file that reported it - server internals, not your SQL.
 * @property line The line in that source file.
 * @property routine The PostgreSQL C function that reported it.
 */
data class ServerErrorMessage(
    val severity: String,
    val localizedSeverity: String,
    val code: String,
    val message: String,
    val detail: String?,
    val hint: String?,
    val position: Int?,
    val internalPosition: Int?,
    val internalQuery: String?,
    val where: String?,
    val schema: String?,
    val table: String?,
    val column: String?,
    val datatype: String?,
    val constraint: String?,
    val file: String?,
    val line: Int?,
    val routine: String?
) {
    internal companion object {
        fun from(errorMsg: ErrorOrNoticeMessage): ServerErrorMessage {
            return ServerErrorMessage(
                severity = errorMsg.severity,
                localizedSeverity = errorMsg.localizedSeverity,
                code = errorMsg.code,
                message = errorMsg.message,
                detail = errorMsg.detail,
                hint = errorMsg.hint,
                position = errorMsg.position,
                internalPosition = errorMsg.internalPosition,
                internalQuery = errorMsg.internalQuery,
                where = errorMsg.where,
                schema = errorMsg.schema,
                table = errorMsg.table,
                column = errorMsg.column,
                datatype = errorMsg.datatype,
                constraint = errorMsg.constraint,
                file = errorMsg.file,
                line = errorMsg.line,
                routine = errorMsg.routine
            )
        }
    }
}