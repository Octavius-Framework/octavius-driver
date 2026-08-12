package io.github.octaviusframework.driver.message

import io.github.octaviusframework.driver.message.backend.ErrorResponseMessage

/**
 * A publicly exposed data class containing all detailed fields provided by the PostgreSQL backend
 * when an error occurs.
 */
data class ServerErrorMessage(
    val severity: String?,
    val code: String?,
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
        fun from(errorMsg: ErrorResponseMessage): ServerErrorMessage {
            return ServerErrorMessage(
                severity = errorMsg.severity,
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