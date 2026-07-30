package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.backend.ErrorResponseMessage

/**
 * A specialized translator that converts low-level database error messages into a structured hierarchy
 * of Octavius [OctaviusException]s.
 *
 * This component categorizes PostgreSQL error messages based on their `SQLSTATE`
 * error codes, providing developers with actionable, high-level information.
 * Most of the specific exceptions (ConnectionException, TransactionException, etc.) 
 * are commented out or mapped to generic OctaviusException as placeholders for future implementation.
 */
object ExceptionTranslator {

    fun translate(errorMsg: ErrorResponseMessage): OctaviusException {
        val state = errorMsg.code ?: ""
        val message = errorMsg.message ?: "Unknown database error"

        return when {
            // Class 08 — Connection Exception
            state.startsWith("08") -> NetworkException(NetworkExceptionMessage.CONNECTION_ERROR, details = message, sqlState = state)

            // Class 22 — Data Exception (Invalid data provided by the user)
            state.startsWith("22") -> DataOperationException("Data Operation Exception (22): $message", sqlState = state)
            
            // Class 28 - Invalid Authorization Specification
            state.startsWith("28") -> InitializationException(
                InitializationExceptionMessage.SERVER_REJECTED_CREDENTIALS,
                details = "Message: $message",
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
                    "23505" -> ConstraintViolationExceptionMessage.UNIQUE_CONSTRAINT_VIOLATION
                    "23503" -> ConstraintViolationExceptionMessage.FOREIGN_KEY_VIOLATION
                    "23502" -> ConstraintViolationExceptionMessage.NOT_NULL_VIOLATION
                    "23514" -> ConstraintViolationExceptionMessage.CHECK_CONSTRAINT_VIOLATION
                    "23P01" -> ConstraintViolationExceptionMessage.EXCLUSION_CONSTRAINT_VIOLATION
                    else -> ConstraintViolationExceptionMessage.UNKNOWN
                }
                ConstraintViolationException(
                    reason = reason,
                    details = "Message: $message",
                    cause = null,
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
                    TransactionException("Transaction Timeout Exception (25): $message", sqlState = state)
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
                if (state == "40002") {
                    ConstraintViolationException(
                        reason = ConstraintViolationExceptionMessage.UNKNOWN,
                        details = "Message: $message",
                        cause = null,
                        sqlState = state,
                        schema = errorMsg.schema,
                        table = errorMsg.table,
                        column = errorMsg.column,
                        constraint = errorMsg.constraint
                    )
                } else {
                    TransactionException("Transaction Rollback Exception (40): $message", sqlState = state)
                }
            }

            // Class 42 — Syntax Error or Access Rule Violation
            state.startsWith("42") -> {
                if (state == "42501") {
                    PermissionDeniedException("Permission Denied (42501): $message", sqlState = state)
                } else {
                    val messageEnum = when (state) {
                        "42601", "42602", "42622", "42939", "42000" -> StatementExceptionReason.SYNTAX_ERROR
                        "42703", "42883", "42P01", "42P02", "42704" -> StatementExceptionReason.UNDEFINED_OBJECT
                        "42701", "42723", "42P03", "42P04", "42P05", "42P06", "42P07", "42712", "42710" -> StatementExceptionReason.DUPLICATE_OBJECT
                        "42702", "42725", "42P08", "42P09" -> StatementExceptionReason.AMBIGUOUS_OBJECT
                        "42804", "42P18", "42846", "42P21", "42P22" -> StatementExceptionReason.DATA_TYPE_ERROR
                        else -> StatementExceptionReason.INVALID_DEFINITION
                    }
                    StatementException(
                        messageEnum,
                        details = "Message: $message",
                        position = errorMsg.position,
                        sqlState = state
                    )
                }
            }

            state.startsWith("54") ->
                StatementException(
                    StatementExceptionReason.SYNTAX_ERROR,
                    details = "Message: $message",
                    position = errorMsg.position,
                    sqlState = state
                )

            state.startsWith("55") -> {
                if (state == "55P03") { // lock_not_available
                    TransactionException("Transaction Timeout Exception (55P03): $message", sqlState = state)
                } else {
                    DatabaseSystemException("Database object state error ($state): $message", sqlState = state)
                }
            }

            state == "57014" -> TransactionException("Transaction Timeout Exception (57014): $message", sqlState = state)
            state.startsWith("57") || state.startsWith("53") || state.startsWith("58") || state.startsWith("XX") ->
                DatabaseSystemException("Database system error ($state): $message", sqlState = state)
                
            // Class P0 — PL/pgSQL Error
            state.startsWith("P0") -> OctaviusException("PL/pgSQL Error ($state): $message", sqlState = state)

            else -> OctaviusException("Unknown database error ($state): $message", sqlState = state)
        }
    }
}
