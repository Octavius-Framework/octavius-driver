package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.ServerErrorMessage

/**
 * Exception thrown when a PL/pgSQL routine raises an error of its own, through `RAISE EXCEPTION`.
 *
 * This is the database saying no on purpose: a business rule expressed where the data is, rather than a
 * routine that turned out to be broken. Those are [RoutineAssertionException] and are a separate type
 * precisely so the two can be handled apart.
 *
 * There is no reason enum here because there is nothing to tell apart. A plain `RAISE EXCEPTION` reports
 * `P0001`; `P0000` is reachable only by asking for it - `RAISE ... USING ERRCODE = 'plpgsql_error'` - so it
 * is another deliberate raise rather than an unclassified failure. Which one it was is [sqlState][OctaviusException.sqlState], and
 * what it was about is [dbMessage].
 *
 * `RAISE EXCEPTION ... USING ERRCODE` reaching further than `P0`, on the other hand, leaves this class
 * entirely: `ERRCODE = '23505'` is routed by the code it names and arrives as a
 * [ConstraintViolationException].
 *
 * @property dbMessage The primary error message the routine raised (the PostgreSQL MESSAGE field).
 * @property dbDetail Explicit DETAIL field provided by PostgreSQL.
 * @property hint Explicit HINT field provided by PostgreSQL.
 * @property where Call stack or context (WHERE field) of the PL/pgSQL execution.
 * @param sqlState The SQL state code returned by the database.
 * @param serverErrorMessage The original error message from the database server.
 */
class RoutineRaiseException(
    sqlState: String,
    serverErrorMessage: ServerErrorMessage
) : OctaviusException("ROUTINE_RAISE_EXCEPTION", sqlState, serverErrorMessage) {

    val dbMessage: String get() = serverErrorMessage!!.message
    val dbDetail: String? get() = serverErrorMessage!!.detail
    val hint: String? get() = serverErrorMessage!!.hint
    val where: String? get() = serverErrorMessage!!.where

    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: A PL/pgSQL function or procedure raised an exception of its own.")
        appendLine("DB Message: $dbMessage")
        if (dbDetail != null) appendLine("DB Detail: $dbDetail")
        if (hint != null) appendLine("Hint: $hint")
        if (where != null) appendLine("Where: $where")
    }
}
