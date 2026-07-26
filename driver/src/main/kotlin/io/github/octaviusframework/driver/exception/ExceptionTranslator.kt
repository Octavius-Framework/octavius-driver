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
            state.startsWith("08") -> OctaviusException("Connection Exception (08): $message", sqlState = state) // TODO: ConnectionException(message)

            // Class 22 — Data Exception (Invalid data provided by the user)
            state.startsWith("22") -> OctaviusException("Data Operation Exception (22): $message", sqlState = state) // TODO: DataOperationException(...)
            
            // Class 28 - Invalid Authorization Specification
            state.startsWith("28") -> AuthException(
                AuthExceptionMessage.SERVER_REJECTED_CREDENTIALS,
                details = "Message: $message",
                sqlState = state
            )

            state.startsWith("21") || state.startsWith("0A") || state.startsWith("3D") || state.startsWith("3F") ->
                BadStatementException(
                    BadStatementExceptionMessage.INVALID_DEFINITION,
                    details = "Message: $message",
                    sqlState = state
                )

            // Class 23 — Integrity Constraint Violation
            state.startsWith("23") -> OctaviusException("Constraint Violation Exception (23): $message", sqlState = state) // TODO: ConstraintViolationException(...)

            // Class 25 — Invalid Transaction State
            state.startsWith("25") -> {
                if (state == "25P03" || state == "25P04") { // idle_in_transaction_session_timeout or PG 17+ transaction_timeout
                    OctaviusException("Transaction Timeout Exception (25): $message", sqlState = state) // TODO: TransactionException(TIMEOUT, message)
                } else {
                    BadStatementException(
                        BadStatementExceptionMessage.INVALID_TRANSACTION_STATE,
                        details = "Message: $message",
                        sqlState = state
                    )
                }
            }

            // Class 40 — Transaction Rollback
            state.startsWith("40") -> OctaviusException("Transaction Rollback Exception (40): $message", sqlState = state) // TODO: TransactionException(...)

            // Class 42 — Syntax Error or Access Rule Violation
            state.startsWith("42") -> {
                if (state == "42501") {
                    OctaviusException("Permission Denied (42501): $message", sqlState = state) // TODO: DataOperationException(PERMISSION_DENIED)
                } else {
                    val messageEnum = when (state) {
                        "42601", "42602", "42622", "42939", "42000" -> BadStatementExceptionMessage.SYNTAX_ERROR
                        "42703", "42883", "42P01", "42P02", "42704" -> BadStatementExceptionMessage.UNDEFINED_OBJECT
                        "42701", "42723", "42P03", "42P04", "42P05", "42P06", "42P07", "42712", "42710" -> BadStatementExceptionMessage.DUPLICATE_OBJECT
                        "42702", "42725", "42P08", "42P09" -> BadStatementExceptionMessage.AMBIGUOUS_OBJECT
                        "42804", "42P18", "42846", "42P21", "42P22" -> BadStatementExceptionMessage.DATA_TYPE_ERROR
                        else -> BadStatementExceptionMessage.INVALID_DEFINITION
                    }
                    BadStatementException(
                        messageEnum,
                        details = "Message: $message",
                        sqlState = state
                    )
                }
            }

            state.startsWith("54") ->
                BadStatementException(
                    BadStatementExceptionMessage.SYNTAX_ERROR,
                    details = "Message: $message",
                    sqlState = state
                )

            state.startsWith("55") -> {
                if (state == "55P03") { // lock_not_available
                    OctaviusException("Transaction Timeout Exception (55P03): $message", sqlState = state) // TODO: TransactionException(TIMEOUT, ...)
                } else {
                    OctaviusException("Database object state error ($state): $message", sqlState = state) // TODO: ConnectionException(...)
                }
            }

            state == "57014" -> OctaviusException("Transaction Timeout Exception (57014): $message", sqlState = state) // TODO: TransactionException(TIMEOUT, ...)
            state.startsWith("57") || state.startsWith("53") || state.startsWith("58") || state.startsWith("XX") ->
                OctaviusException("Database system error ($state): $message", sqlState = state) // TODO: ConnectionException(...)
                
            // Class P0 — PL/pgSQL Error
            state.startsWith("P0") -> OctaviusException("PL/pgSQL Error ($state): $message", sqlState = state) // TODO: UnknownDatabaseException(...)

            else -> OctaviusException("Unknown database error ($state): $message", sqlState = state)
        }
    }
}
