package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.ServerErrorMessage

/**
 * Represents specific reasons for a SQL statement execution failure.
 */
enum class StatementExceptionReason {
    /** The statement contains a syntax error. */
    SYNTAX_ERROR,
    /** The statement contains an unclosed string or identifier quote. */
    UNCLOSED_QUOTE,
    /** The statement contains an unclosed dollar-quoted string. */
    UNCLOSED_DOLLAR_QUOTE,
    /** The statement contains an unclosed multi-line comment. */
    UNCLOSED_COMMENT,
    /** An object's definition or current state does not permit what was asked of it. */
    INVALID_DEFINITION,
    /** The statement references a table, column, function or type that does not exist. */
    UNDEFINED_OBJECT,
    /** The statement creates an object under a name that is already taken. */
    DUPLICATE_OBJECT,
    /** A name in the statement matches more than one candidate; qualify it. */
    AMBIGUOUS_OBJECT,
    /** A value's type does not fit where it was used, and no implicit cast applies. */
    DATA_TYPE_ERROR,
    /** The statement is not permitted in the session's current transaction state. */
    INVALID_TRANSACTION_STATE,
    /** Raised by the driver: the statement names a parameter the supplied values do not include. */
    MISSING_NAMED_PARAMETER,
    /** Raised by the driver: the query returned a number of rows the chosen fetch method forbids. */
    INCORRECT_RESULT_SIZE
}

/**
 * Exception thrown when an error occurs during the parsing, planning, or execution of a SQL statement.
 *
 * This exception covers various query-related errors such as syntax errors, undefined objects,
 * ambiguous references, and data type mismatches. If the database provides error positioning, 
 * this exception will format the original SQL query to visually indicate where the error occurred.
 *
 * @property reason The categorized reason for the statement failure.
 * @property details Additional context or hints provided by the database regarding the error.
 * @property position The 1-based character position in the SQL string where the error occurred, if available.
 * @param sqlState The SQL state code returned by the database, if available.
 * @param serverErrorMessage The original error message from the database server, if available.
 */
class StatementException(
    val reason: StatementExceptionReason,
    val details: String? = null,
    val position: Int? = null,
    sqlState: String? = null,
    serverErrorMessage: ServerErrorMessage? = null
) : OctaviusException("STATEMENT_EXCEPTION:${reason.name}", sqlState, serverErrorMessage) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        if (details != null) appendLine("Details: $details")
        
        val sqlContext = queryContext?.dbSql ?: queryContext?.sql
        if (sqlContext != null && position != null && position > 0 && position <= sqlContext.length + 1) {
            appendLine("Error at position $position:")
            val beforeError = sqlContext.substring(0, position - 1)
            val lastNewlineIndex = beforeError.lastIndexOf('\n')
            val lineStart = if (lastNewlineIndex == -1) 0 else lastNewlineIndex + 1
            
            val afterError = sqlContext.substring(position - 1)
            val nextNewlineIndex = afterError.indexOf('\n')
            val lineEnd = if (nextNewlineIndex == -1) sqlContext.length else (position - 1) + nextNewlineIndex
            
            val errorLine = sqlContext.substring(lineStart, lineEnd).replace("\r", "")
            val column = (position - 1) - lineStart
            
            appendLine(errorLine)
            appendLine(" ".repeat(maxOf(0, column)) + "^")
        }
    }
}

private fun generateDeveloperMessage(reason: StatementExceptionReason): String =
    when (reason) {
        StatementExceptionReason.SYNTAX_ERROR -> "The SQL statement contains a syntax error."
        StatementExceptionReason.UNCLOSED_QUOTE -> "The SQL statement contains an unclosed string or identifier quote."
        StatementExceptionReason.UNCLOSED_DOLLAR_QUOTE -> "The SQL statement contains an unclosed dollar-quoted string."
        StatementExceptionReason.UNCLOSED_COMMENT -> "The SQL statement contains an unclosed multi-line comment."
        StatementExceptionReason.INVALID_DEFINITION -> "Invalid definition or object state."
        StatementExceptionReason.UNDEFINED_OBJECT -> "The referenced object is undefined."
        StatementExceptionReason.DUPLICATE_OBJECT -> "The referenced object already exists."
        StatementExceptionReason.AMBIGUOUS_OBJECT -> "The referenced object is ambiguous."
        StatementExceptionReason.DATA_TYPE_ERROR -> "Data type error in statement."
        StatementExceptionReason.INVALID_TRANSACTION_STATE -> "Invalid transaction state."
        StatementExceptionReason.MISSING_NAMED_PARAMETER -> "A required named parameter is missing."
        StatementExceptionReason.INCORRECT_RESULT_SIZE -> "The query returned an unexpected number of rows."
    }
