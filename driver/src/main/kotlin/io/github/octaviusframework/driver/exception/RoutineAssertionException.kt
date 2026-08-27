package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.ServerErrorMessage

/**
 * Represents which assertion a PL/pgSQL routine made and the data then falsified.
 */
enum class RoutineAssertionExceptionReason {
    /** A SELECT INTO STRICT statement did not return any rows (P0002). */
    NO_DATA_FOUND,

    /** A SELECT INTO STRICT statement returned more than one row (P0003). */
    TOO_MANY_ROWS,

    /** An ASSERT statement failed (P0004). */
    ASSERT_FAILURE
}

/**
 * Exception thrown when an assertion inside a PL/pgSQL routine turns out to be false.
 *
 * `INTO STRICT` states that the query matches exactly one row, and `ASSERT` states its condition holds. A run
 * that finds otherwise has falsified something the routine claimed, which makes this a defect in the database
 * code rather than an outcome of the call - the same reading that has `fetch*Strict` finding no row raise
 * [InvalidOperationException] with `INCORRECT_RESULT_SIZE` instead of returning null. This is that failure
 * one level down, asserted in PL/pgSQL rather than in Kotlin.
 *
 * A routine saying no on purpose is [RoutineRaiseException] and is deliberately a different type.
 *
 * @property reason Which assertion failed.
 * @property dbMessage The primary error message the routine raised (the PostgreSQL MESSAGE field).
 * @property dbDetail Explicit DETAIL field provided by PostgreSQL.
 * @property hint Explicit HINT field provided by PostgreSQL.
 * @property where Call stack or context (WHERE field) of the PL/pgSQL execution.
 * @param sqlState The SQL state code returned by the database.
 * @param serverErrorMessage The original error message from the database server.
 */
class RoutineAssertionException(
    val reason: RoutineAssertionExceptionReason,
    sqlState: String,
    serverErrorMessage: ServerErrorMessage
) : OctaviusException("ROUTINE_ASSERTION_EXCEPTION:${reason.name}", sqlState, serverErrorMessage) {

    val dbMessage: String get() = serverErrorMessage!!.message
    val dbDetail: String? get() = serverErrorMessage!!.detail
    val hint: String? get() = serverErrorMessage!!.hint
    val where: String? get() = serverErrorMessage!!.where

    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        appendLine("DB Message: $dbMessage")
        if (dbDetail != null) appendLine("DB Detail: $dbDetail")
        if (hint != null) appendLine("Hint: $hint")
        if (where != null) appendLine("Where: $where")
    }
}

private fun generateDeveloperMessage(reason: RoutineAssertionExceptionReason): String =
    when (reason) {
        RoutineAssertionExceptionReason.NO_DATA_FOUND -> "A query intended to return a single row returned no data."
        RoutineAssertionExceptionReason.TOO_MANY_ROWS -> "A query intended to return a single row returned multiple rows."
        RoutineAssertionExceptionReason.ASSERT_FAILURE -> "An assertion failed during routine execution."
    }
