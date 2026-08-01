package io.github.octaviusframework.driver.exception

/**
 * Represents specific reasons for a PL/pgSQL routine execution failure.
 */
enum class RoutineExecutionExceptionReason {
    /** A user-defined exception was explicitly raised via RAISE EXCEPTION (P0001). */
    RAISE_EXCEPTION,
    
    /** A SELECT INTO STRICT statement did not return any rows (P0002). */
    NO_DATA_FOUND,
    
    /** A SELECT INTO STRICT statement returned more than one row (P0003). */
    TOO_MANY_ROWS,
    
    /** An ASSERT statement failed (P0004). */
    ASSERT_FAILURE,
    
    /** An unknown or unmapped PL/pgSQL error. */
    UNKNOWN
}

/**
 * Exception thrown when an error occurs during the execution of a PL/pgSQL routine (stored procedure or function).
 *
 * This exception is specific to PostgreSQL's procedural language execution. It often indicates business logic 
 * errors explicitly raised by database developers (e.g., via RAISE EXCEPTION) or assertion failures inside the database.
 *
 * @property reason The categorized reason for the routine failure.
 * @property details Additional context or hints provided by the database regarding the error.
 * @property dbDetail Explicit DETAIL field provided by PostgreSQL.
 * @property hint Explicit HINT field provided by PostgreSQL.
 * @property whereContext Call stack or context (WHERE field) of the PL/pgSQL execution.
 */
class RoutineExecutionException(
    val reason: RoutineExecutionExceptionReason,
    val details: String? = null,
    val dbDetail: String? = null,
    val hint: String? = null,
    val whereContext: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException(reason.name, cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(reason)}")
        if (details != null) appendLine("Details: $details")
        if (dbDetail != null) appendLine("DB Detail: $dbDetail")
        if (hint != null) appendLine("Hint: $hint")
        if (whereContext != null) appendLine("Where: $whereContext")
    }
}

private fun generateDeveloperMessage(reason: RoutineExecutionExceptionReason): String =
    when (reason) {
        RoutineExecutionExceptionReason.RAISE_EXCEPTION -> "A user-defined exception was raised by the PL/pgSQL function or procedure."
        RoutineExecutionExceptionReason.NO_DATA_FOUND -> "A query intended to return a single row returned no data."
        RoutineExecutionExceptionReason.TOO_MANY_ROWS -> "A query intended to return a single row returned multiple rows."
        RoutineExecutionExceptionReason.ASSERT_FAILURE -> "An assertion failed during routine execution."
        RoutineExecutionExceptionReason.UNKNOWN -> "An unknown PL/pgSQL execution error occurred."
    }
