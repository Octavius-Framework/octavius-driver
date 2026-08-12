package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.backend.ErrorResponseMessage

/**
 * A specialized translator that converts low-level database error messages into a structured hierarchy
 * of Octavius [OctaviusException]s.
 *
 * This component categorizes PostgreSQL error messages based on their `SQLSTATE`
 * error codes, providing developers with actionable, high-level information.
 * Most of the specific exceptions (ConnectionException, ConcurrencyException, etc.) 
 * are commented out or mapped to generic OctaviusException as placeholders for future implementation.
 */
object ExceptionTranslator {

    fun translate(errorMsg: ErrorResponseMessage): OctaviusException {
        val state = errorMsg.code ?: ""
        val message = errorMsg.message ?: "Unknown database error"

        return when {
            // Class 08 — Connection Exception
            state.startsWith("08") -> NetworkException(NetworkExceptionReason.CONNECTION_ERROR, details = message, sqlState = state)

            // Class 22 — Data Exception (Invalid data provided by the user)
            state.startsWith("22") -> {
                val reason = when (state) {
                    "22001", "22008", "22015" -> DataExceptionReason.DATA_TRUNCATION
                    "22003", "22022" -> DataExceptionReason.NUMERIC_OUT_OF_RANGE
                    "22012" -> DataExceptionReason.DIVISION_BY_ZERO
                    "22007", "22P02", "22P03", "22018" -> DataExceptionReason.INVALID_FORMAT
                    "2202E" -> DataExceptionReason.ARRAY_SUBSCRIPT_ERROR
                    "22004", "22002" -> DataExceptionReason.NULL_VALUE_NOT_ALLOWED
                    "2201B" -> DataExceptionReason.REGEX_ERROR
                    "22019", "2200D", "22025", "22P06", "2200C", "2200B" -> DataExceptionReason.ESCAPE_CHARACTER_ERROR
                    "2200L", "2200M", "2200N", "2200S", "2200T" -> DataExceptionReason.XML_ERROR
                    else -> if (state.startsWith("2203")) DataExceptionReason.JSON_ERROR else DataExceptionReason.UNKNOWN
                }
                DataException(
                    reason = reason,
                    dbMessage = errorMsg.message,
                    details = errorMsg.detail,
                    where = errorMsg.whereContext,
                    sqlState = state
                )
            }
            
            // Class 28 - Invalid Authorization Specification
            state.startsWith("28") -> InitializationException(
                InitializationExceptionReason.SERVER_REJECTED_CREDENTIALS,
                details = message,
                sqlState = state
            )

            state.startsWith("21") || state.startsWith("0A") || state.startsWith("3D") || state.startsWith("3F") ->
                StatementException(
                    StatementExceptionReason.INVALID_DEFINITION,
                    details = "Message: $message",
                    position = errorMsg.position,
                    sqlState = state
                )

            // Class 23 — Integrity Constraint Violation
            state.startsWith("23") -> {
                val reason = when (state) {
                    "23505" -> ConstraintViolationExceptionReason.UNIQUE_CONSTRAINT_VIOLATION
                    "23503" -> ConstraintViolationExceptionReason.FOREIGN_KEY_VIOLATION
                    "23502" -> ConstraintViolationExceptionReason.NOT_NULL_VIOLATION
                    "23514" -> ConstraintViolationExceptionReason.CHECK_CONSTRAINT_VIOLATION
                    "23P01" -> ConstraintViolationExceptionReason.EXCLUSION_CONSTRAINT_VIOLATION
                    else -> ConstraintViolationExceptionReason.UNKNOWN
                }
                ConstraintViolationException(
                    reason = reason,
                    dbMessage = errorMsg.message,
                    details = errorMsg.detail,
                    where = errorMsg.whereContext,
                    sqlState = state,
                    schema = errorMsg.schema,
                    table = errorMsg.table,
                    column = errorMsg.column,
                    constraint = errorMsg.constraint
                )
            }

            // Class 25 — Invalid Transaction State
            state.startsWith("25") -> {
                if (state == "25P03" || state == "25P04") { // idle_in_transaction_session_timeout or transaction_timeout
                    ExecutionAbortedException(ExecutionAbortedExceptionReason.TRANSACTION_TIMEOUT, dbMessage = "Message: $message", sqlState = state)
                } else {
                    StatementException(
                        StatementExceptionReason.INVALID_TRANSACTION_STATE,
                        details = "Message: $message",
                        position = errorMsg.position,
                        sqlState = state
                    )
                }
            }

            // Class 40 — Transaction Rollback
            state.startsWith("40") -> {
                val reason = when (state) {
                    "40001" -> ConcurrencyExceptionReason.SERIALIZATION_FAILURE
                    "40P01" -> ConcurrencyExceptionReason.DEADLOCK_DETECTED
                    else -> ConcurrencyExceptionReason.UNKNOWN
                }

                if (state == "40002") {
                    ConstraintViolationException(
                        reason = ConstraintViolationExceptionReason.UNKNOWN,
                        dbMessage = errorMsg.message,
                        details = errorMsg.detail,
                        where = errorMsg.whereContext,
                        sqlState = state,
                        schema = errorMsg.schema,
                        table = errorMsg.table,
                        column = errorMsg.column,
                        constraint = errorMsg.constraint
                    )
                } else {
                    ConcurrencyException(reason, dbMessage = "Message: $message", sqlState = state)
                }
            }

            // Class 42 — Syntax Error or Access Rule Violation
            state.startsWith("42") -> {
                if (state == "42501") {
                    PermissionDeniedException(
                        dbMessage = message,
                        sqlState = state,
                        schema = errorMsg.schema,
                        table = errorMsg.table,
                        column = errorMsg.column,
                        datatype = errorMsg.datatype,
                        routine = errorMsg.routine
                    )
                } else {
                    val reason = when (state) {
                        "42601", "42602", "42622", "42939", "42000" -> StatementExceptionReason.SYNTAX_ERROR
                        "42703", "42883", "42P01", "42P02", "42704" -> StatementExceptionReason.UNDEFINED_OBJECT
                        "42701", "42723", "42P03", "42P04", "42P05", "42P06", "42P07", "42712", "42710" -> StatementExceptionReason.DUPLICATE_OBJECT
                        "42702", "42725", "42P08", "42P09" -> StatementExceptionReason.AMBIGUOUS_OBJECT
                        "42804", "42P18", "42846", "42P21", "42P22" -> StatementExceptionReason.DATA_TYPE_ERROR
                        else -> StatementExceptionReason.INVALID_DEFINITION
                    }
                    StatementException(
                        reason,
                        details = message,
                        position = errorMsg.position,
                        sqlState = state
                    )
                }
            }

            state.startsWith("54") ->
                StatementException(
                    StatementExceptionReason.SYNTAX_ERROR,
                    details = message,
                    position = errorMsg.position,
                    sqlState = state
                )

            state.startsWith("55") -> {
                if (state == "55P03") { // lock_not_available
                    ConcurrencyException(ConcurrencyExceptionReason.LOCK_NOT_AVAILABLE, dbMessage = message, sqlState = state)
                } else {
                    DatabaseSystemException("Database object state error ($state): $message", sqlState = state)
                }
            }

            state == "57014" -> ExecutionAbortedException(ExecutionAbortedExceptionReason.QUERY_CANCELED, dbMessage = message, sqlState = state)
            state.startsWith("57") || state.startsWith("53") || state.startsWith("58") || state.startsWith("XX") ->
                DatabaseSystemException("Database system error ($state): $message", sqlState = state)
                
            // Class P0 — PL/pgSQL Error
            state.startsWith("P0") -> {
                val reason = when (state) {
                    "P0001" -> RoutineExecutionExceptionReason.RAISE_EXCEPTION
                    "P0002" -> RoutineExecutionExceptionReason.NO_DATA_FOUND
                    "P0003" -> RoutineExecutionExceptionReason.TOO_MANY_ROWS
                    "P0004" -> RoutineExecutionExceptionReason.ASSERT_FAILURE
                    else -> RoutineExecutionExceptionReason.UNKNOWN
                }
                RoutineExecutionException(
                    reason = reason, 
                    dbMessage = message,
                    dbDetail = errorMsg.detail,
                    hint = errorMsg.hint,
                    whereContext = errorMsg.whereContext,
                    sqlState = state
                )
            }

            else -> OctaviusException("Unknown database error ($state): $message", sqlState = state)
        }
    }
}
